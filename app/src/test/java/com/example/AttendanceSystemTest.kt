package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.OfflineAttendanceDatabase
import com.example.data.local.PendingAttendanceEntity
import com.example.data.model.AttendanceStatus
import com.example.data.model.School
import com.example.data.model.SchoolStatus
import com.example.data.model.Student
import com.example.data.model.SuperAdminConfig
import com.example.data.model.UserRole
import com.example.util.BengaliStrings
import com.example.util.EnglishStrings
import com.example.util.QrSecurityManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AttendanceSystemTest {

    @Test
    fun `super admin identity verification enforces exact UID and verified email`() {
        assertTrue(
            SuperAdminConfig.isPermanentSuperAdmin(
                uid = "Q6vhlqwqAlMvDy4WFhOq9xnjnit1",
                email = null,
                emailVerified = false,
            )
        )
        assertTrue(
            SuperAdminConfig.isPermanentSuperAdmin(
                uid = "other_uid",
                email = "anantabarman.mtb736172@gmail.com",
                emailVerified = true,
            )
        )
        // Unverified spoofed email MUST be rejected
        assertFalse(
            SuperAdminConfig.isPermanentSuperAdmin(
                uid = "attacker_uid",
                email = "anantabarman.mtb736172@gmail.com",
                emailVerified = false,
            )
        )
        assertFalse(
            SuperAdminConfig.isPermanentSuperAdmin(
                uid = "teacher_uid",
                email = "teacher@school.edu",
                emailVerified = true,
            )
        )
    }

    @Test
    fun `opaque signed QR payload verifies cleanly, hides PII, and rejects tampering`() {
        val payload = QrSecurityManager.buildOpaqueQrPayload(
            schoolId = "SCH_A_1001",
            tokenId = "QRT_998877",
            version = 2L,
        )
        assertFalse(payload.contains("Aarav"))
        assertFalse(payload.contains("+91"))

        val parsed = QrSecurityManager.parseAndVerifyPayload(payload)
        assertNotNull(parsed)
        assertEquals("SCH_A_1001", parsed?.schoolId)
        assertEquals("QRT_998877", parsed?.tokenId)
        assertEquals(2, parsed?.version)

        // Tampered schoolId MUST fail HMAC check
        val tampered = payload.replace("SCH_A_1001", "SCH_B_2002")
        assertNull(QrSecurityManager.parseAndVerifyPayload(tampered))

        // Invalid raw text MUST fail
        assertNull(QrSecurityManager.parseAndVerifyPayload("https://malicious.example.com"))
    }

    @Test
    fun `qr bitmap and printable student QR ID card render without error`() {
        val school = School(
            id = "SCH_1",
            schoolName = "Saffron Vidya Mandir",
            schoolCode = "UDISE-1901",
            district = "Cooch Behar",
            state = "West Bengal",
            status = SchoolStatus.APPROVED.name,
        )
        val student = Student(
            id = "STU_1",
            studentIdCode = "STU-1901-1024",
            studentName = "Aarav Sen",
            classId = "CLS_10",
            className = "Class 10",
            section = "A",
            rollNumber = "1",
            qrTokenId = "QRT_1024",
            qrVersion = 1L,
            schoolId = "SCH_1",
        )
        val qrBitmap = QrSecurityManager.generateQrBitmap(
            QrSecurityManager.buildOpaqueQrPayload(student.schoolId, student.qrTokenId, student.qrVersion),
            256,
        )
        assertEquals(256, qrBitmap.width)
        assertEquals(256, qrBitmap.height)

        val cardBitmap = QrSecurityManager.renderStudentQrCardBitmap(school, student)
        assertEquals(760, cardBitmap.width)
        assertEquals(1040, cardBitmap.height)
    }

    @Test
    fun `offline room queue prevents duplicate attendance and isolates schools`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, OfflineAttendanceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.pendingAttendanceDao()

        val entry = PendingAttendanceEntity(
            deterministicId = "STU_1_2026-11-20",
            schoolId = "SCH_A",
            studentId = "STU_1",
            studentIdCode = "STU-001",
            studentName = "Aarav Sen",
            studentPhotoUrl = "",
            classId = "CLS_10",
            className = "Class 10",
            section = "A",
            rollNumber = "1",
            attendanceDate = "2026-11-20",
            attendanceTime = "09:10:00",
            status = AttendanceStatus.PRESENT.name,
            recordedByUid = "TCH_1",
            recordedByName = "Priya Das",
            adminUid = "ADM_A",
            memberUidsCsv = "ADM_A,TCH_1",
        )

        val firstInsert = dao.insertIfAbsent(entry)
        val duplicateInsert = dao.insertIfAbsent(entry.copy(attendanceTime = "09:15:00"))

        assertTrue(firstInsert > 0)
        assertEquals(-1L, duplicateInsert) // Ignored duplicate!
        assertEquals(1, dao.getPendingForSchoolOnce("SCH_A").size)
        assertEquals(0, dao.getPendingForSchoolOnce("SCH_B").size)

        dao.deleteById("STU_1_2026-11-20")
        assertEquals(0, dao.getPendingForSchoolOnce("SCH_A").size)
        db.close()
    }

    @Test
    fun `app metadata, safe enum parsing, and bilingual strings are properly configured`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Students Attendance by QR", context.getString(R.string.app_name))
        assertEquals(UserRole.PENDING_ADMIN, UserRole.fromString("UNKNOWN_FUTURE_ROLE"))
        assertEquals(AttendanceStatus.PRESENT, AttendanceStatus.fromString("INVALID"))
        assertEquals("Students Attendance by QR", EnglishStrings.appTitle)
        assertTrue(BengaliStrings.appTitle.isNotBlank())
    }
}
