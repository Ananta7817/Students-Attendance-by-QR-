package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.model.AttendanceRecord
import com.example.data.model.School
import com.example.data.model.Student
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class ParsedQrPayload(
    val version: Int,
    val schoolId: String,
    val tokenId: String,
    val signature: String,
)

object QrSecurityManager {
    private const val PREFIX = "SABQR"
    private const val FORMAT_VERSION = 1
    // Domain-separated verification key for opaque token integrity checks
    private const val SIGNING_PEPPER = "SABQR_GERUA_ATTENDANCE_V1_HMAC_KEY"

    fun sha256Hex(input: String, maxChars: Int = 32): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(maxChars)
    }

    fun computeSignature(schoolId: String, tokenId: String, version: Long): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec("$SIGNING_PEPPER:$schoolId".toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val raw = mac.doFinal("$schoolId|$tokenId|$version".toByteArray(Charsets.UTF_8))
        return raw.joinToString("") { "%02x".format(it) }.take(16)
    }

    /**
     * Produces an opaque signed QR string that NEVER exposes private student PII.
     * Format: SABQR:1:<schoolId>:<tokenId>:<version>:<hmac16>
     */
    fun buildOpaqueQrPayload(schoolId: String, tokenId: String, version: Long): String {
        val sig = computeSignature(schoolId, tokenId, version)
        return "$PREFIX:$FORMAT_VERSION:$schoolId:$tokenId:$version:$sig"
    }

    fun parseAndVerifyPayload(raw: String): ParsedQrPayload? {
        val parts = raw.trim().split(":")
        if (parts.size != 6 || parts[0] != PREFIX) return null
        val fmt = parts[1].toIntOrNull() ?: return null
        if (fmt != FORMAT_VERSION) return null
        val schoolId = parts[2]
        val tokenId = parts[3]
        val version = parts[4].toLongOrNull() ?: return null
        val providedSig = parts[5]
        if (schoolId.isBlank() || tokenId.isBlank() || providedSig.length != 16) return null
        val expectedSig = computeSignature(schoolId, tokenId, version)
        if (!MessageDigest.isEqual(providedSig.toByteArray(), expectedSig.toByteArray())) return null
        return ParsedQrPayload(
            version = version.toInt(),
            schoolId = schoolId,
            tokenId = tokenId,
            signature = providedSig,
        )
    }

    fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.parseColor("#252525") else Color.WHITE)
            }
        }
        return bitmap
    }

    fun renderStudentQrCardBitmap(school: School?, student: Student): Bitmap {
        val width = 760
        val height = 1040
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Warm white card background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFF9F2") }
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), 32f, 32f, bgPaint)

        // Saffron header band
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E87516") }
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), 190f), 32f, 32f, headerPaint)
        canvas.drawRect(0f, 150f, width.toFloat(), 190f, headerPaint)

        // School title
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFF0DE")
            textSize = 20f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText((school?.schoolName ?: "Students Attendance by QR").take(34), width / 2f, 82f, titlePaint)
        canvas.drawText("STUDENT QR IDENTITY CARD • ${school?.schoolCode ?: student.schoolId}", width / 2f, 124f, subtitlePaint)
        canvas.drawText("${school?.district ?: ""} ${school?.state ?: ""}".trim(), width / 2f, 160f, subtitlePaint)

        // QR Code frame
        val qrPayload = buildOpaqueQrPayload(student.schoolId, student.qrTokenId, student.qrVersion)
        val qrBmp = generateQrBitmap(qrPayload, 420)
        val qrFramePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E87516")
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        val qrLeft = (width - 456f) / 2f
        val qrTop = 225f
        canvas.drawRoundRect(RectF(qrLeft, qrTop, qrLeft + 456f, qrTop + 456f), 24f, 24f, qrFramePaint)
        canvas.drawRoundRect(RectF(qrLeft, qrTop, qrLeft + 456f, qrTop + 456f), 24f, 24f, borderPaint)
        canvas.drawBitmap(qrBmp, qrLeft + 18f, qrTop + 18f, null)

        // Student details
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#252525")
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#555555")
            textSize = 24f
            textAlign = Paint.Align.CENTER
        }
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFF0DE") }
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#C65D0A")
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        canvas.drawText(student.studentName, width / 2f, 750f, namePaint)
        canvas.drawRoundRect(RectF(180f, 775f, width - 180f, 825f), 25f, 25f, badgePaint)
        canvas.drawText("ID: ${student.studentIdCode}", width / 2f, 809f, badgeTextPaint)
        canvas.drawText("Class: ${student.className}  •  Section: ${student.section}  •  Roll: ${student.rollNumber}", width / 2f, 880f, infoPaint)
        canvas.drawText("QR Token Ver. ${student.qrVersion}  •  Scan via Students Attendance by QR", width / 2f, 940f, Paint(infoPaint).apply { textSize = 18f })

        // Footer bar
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#C65D0A") }
        canvas.drawRoundRect(RectF(0f, height - 48f, width.toFloat(), height.toFloat()), 0f, 0f, footerPaint)
        return bitmap
    }

    fun exportQrCardPngAndShare(context: Context, school: School?, student: Student): Result<File> = runCatching {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "QR_${student.studentIdCode.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.png")
        val bmp = renderStudentQrCardBitmap(school, student)
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        shareFile(context, file, "image/png", "Student QR Card - ${student.studentName}")
        file
    }

    fun exportBatchQrCardsPdfAndShare(context: Context, school: School?, students: List<Student>, title: String): Result<File> = runCatching {
        val pdf = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val cardsPerPage = 4
        val pages = students.chunked(cardsPerPage)

        pages.forEachIndexed { pageIdx, batch ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIdx + 1).create()
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas
            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#C65D0A")
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("${school?.schoolName ?: "School"} — $title (Page ${pageIdx + 1})", 28f, 28f, headerPaint)

            batch.forEachIndexed { idx, student ->
                val col = idx % 2
                val row = idx / 2
                val cardBmp = renderStudentQrCardBitmap(school, student)
                val dest = RectF(
                    24f + col * 278f,
                    44f + row * 388f,
                    24f + col * 278f + 264f,
                    44f + row * 388f + 372f,
                )
                canvas.drawBitmap(cardBmp, null, dest, null)
            }
            pdf.finishPage(page)
        }

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "Batch_QR_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        shareFile(context, file, "application/pdf", "Batch Student QR Cards")
        file
    }

    fun exportAttendanceCsvOrExcel(
        context: Context,
        records: List<AttendanceRecord>,
        reportTitle: String,
        asExcelExtension: Boolean
    ): Result<File> = runCatching {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val ext = if (asExcelExtension) "xls" else "csv"
        val file = File(dir, "Attendance_${System.currentTimeMillis()}.$ext")
        val sb = StringBuilder()
        sb.appendLine("Date,Time,Student ID,Student Name,Class,Section,Roll No,Status,Method,Recorded By,Note")
        records.forEach { r ->
            fun esc(v: String) = "\"${v.replace("\"", "\"\"")}\""
            sb.appendLine(
                listOf(
                    r.attendanceDate, r.attendanceTime, r.studentIdCode, r.studentName,
                    r.className, r.section, r.rollNumber, r.status, r.method,
                    r.recordedByName, r.correctionNote
                ).joinToString(",") { esc(it) }
            )
        }
        file.writeText(sb.toString(), Charsets.UTF_8)
        val mime = if (asExcelExtension) "application/vnd.ms-excel" else "text/csv"
        shareFile(context, file, mime, reportTitle)
        file
    }

    fun exportAttendancePdf(
        context: Context,
        schoolName: String,
        records: List<AttendanceRecord>,
        reportTitle: String
    ): Result<File> = runCatching {
        val pdf = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val rowsPerPage = 28
        val chunks = if (records.isEmpty()) listOf(emptyList()) else records.chunked(rowsPerPage)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E87516")
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#252525")
            textSize = 11f
        }
        val headerBg = Paint().apply { color = Color.parseColor("#FFF0DE") }
        val headerTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#C65D0A")
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        chunks.forEachIndexed { idx, chunk ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, idx + 1).create()
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas

            canvas.drawText(schoolName, 28f, 38f, titlePaint)
            canvas.drawText("$reportTitle • Total Records: ${records.size} • Page ${idx + 1}/${chunks.size}", 28f, 56f, subPaint)

            canvas.drawRect(24f, 70f, pageWidth - 24f, 92f, headerBg)
            canvas.drawText("Date", 30f, 84f, headerTxt)
            canvas.drawText("Student ID", 95f, 84f, headerTxt)
            canvas.drawText("Name", 175f, 84f, headerTxt)
            canvas.drawText("Class", 325f, 84f, headerTxt)
            canvas.drawText("Status", 405f, 84f, headerTxt)
            canvas.drawText("Time", 475f, 84f, headerTxt)
            canvas.drawText("Method", 525f, 84f, headerTxt)

            var y = 112f
            chunk.forEach { rec ->
                canvas.drawText(rec.attendanceDate, 30f, y, subPaint)
                canvas.drawText(rec.studentIdCode.take(12), 95f, y, subPaint)
                canvas.drawText(rec.studentName.take(22), 175f, y, subPaint)
                canvas.drawText("${rec.className}-${rec.section}".take(12), 325f, y, subPaint)
                canvas.drawText(rec.status, 405f, y, subPaint)
                canvas.drawText(rec.attendanceTime.take(8), 475f, y, subPaint)
                canvas.drawText(rec.method.take(7), 525f, y, subPaint)
                y += 24f
            }
            pdf.finishPage(page)
        }

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "Attendance_Report_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        shareFile(context, file, "application/pdf", reportTitle)
        file
    }

    private fun shareFile(context: Context, file: File, mimeType: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
