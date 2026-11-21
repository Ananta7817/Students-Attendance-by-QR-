package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceStatus
import com.example.data.model.School
import com.example.data.model.SchoolClass
import com.example.ui.components.AttendanceStatusBadge
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SaffronCard
import com.example.ui.components.SaffronSearchBar
import com.example.ui.components.SaffronStatCard
import com.example.ui.components.SectionHeader
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.InfoBlue
import com.example.ui.theme.SaffronDark
import com.example.ui.theme.SaffronLight
import com.example.ui.theme.SaffronPrimary
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.util.LocalAppStrings
import com.example.util.QrSecurityManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class ReportPeriod { DAILY, WEEKLY, MONTHLY, ALL }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AttendanceReportsAndHistoryScreen(
    school: School?,
    attendance: List<AttendanceRecord>,
    classes: List<SchoolClass>,
    todayDate: String,
    canCorrectAttendance: Boolean,
    onCorrectAttendance: (AttendanceRecord, AttendanceStatus, String) -> Unit,
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current

    var period by remember { mutableStateOf(ReportPeriod.DAILY) }
    var selectedClassId by remember { mutableStateOf<String?>(null) }
    var statusFilter by remember { mutableStateOf<AttendanceStatus?>(null) }
    var search by remember { mutableStateOf("") }
    var exportNotice by remember { mutableStateOf<String?>(null) }
    var editingRecord by remember { mutableStateOf<AttendanceRecord?>(null) }

    val weekAgoDate = remember {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }
    val currentMonthPrefix = remember(todayDate) { todayDate.take(7) }

    val filtered = remember(attendance, period, selectedClassId, statusFilter, search, todayDate) {
        attendance.filter { rec ->
            val dateOk = when (period) {
                ReportPeriod.DAILY -> rec.attendanceDate == todayDate
                ReportPeriod.WEEKLY -> rec.attendanceDate >= weekAgoDate
                ReportPeriod.MONTHLY -> rec.attendanceDate.startsWith(currentMonthPrefix)
                ReportPeriod.ALL -> true
            }
            val classOk = selectedClassId == null || rec.classId == selectedClassId
            val statusOk = statusFilter == null || rec.statusEnum == statusFilter
            val queryOk = search.isBlank() ||
                rec.studentName.contains(search, ignoreCase = true) ||
                rec.studentIdCode.contains(search, ignoreCase = true) ||
                rec.recordedByName.contains(search, ignoreCase = true) ||
                rec.section.equals(search.trim(), ignoreCase = true)
            dateOk && classOk && statusOk && queryOk
        }
    }

    val presentCount = filtered.count { it.statusEnum == AttendanceStatus.PRESENT }
    val absentCount = filtered.count { it.statusEnum == AttendanceStatus.ABSENT }
    val lateCount = filtered.count { it.statusEnum == AttendanceStatus.LATE }
    val excusedCount = filtered.count { it.statusEnum == AttendanceStatus.EXCUSED }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Attendance Reports & History", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            // Report period chips
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    ReportPeriod.DAILY to strings.dailyReport,
                    ReportPeriod.WEEKLY to strings.weeklyReport,
                    ReportPeriod.MONTHLY to strings.monthlyReport,
                    ReportPeriod.ALL to "All Time",
                ).forEach { (p, label) ->
                    FilterChip(
                        selected = period == p,
                        onClick = { period = p },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SaffronLight,
                            selectedLabelColor = SaffronDark,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            SaffronSearchBar(
                query = search,
                onQueryChange = { search = it },
                placeholder = "Filter by student, ID, section, or teacher...",
            )

            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = selectedClassId == null,
                    onClick = { selectedClassId = null },
                    label = { Text("All Classes") },
                )
                classes.forEach { cls ->
                    FilterChip(
                        selected = selectedClassId == cls.id,
                        onClick = { selectedClassId = if (selectedClassId == cls.id) null else cls.id },
                        label = { Text(cls.className) },
                    )
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AttendanceStatus.entries.forEach { st ->
                    FilterChip(
                        selected = statusFilter == st,
                        onClick = { statusFilter = if (statusFilter == st) null else st },
                        label = { Text(st.name) },
                    )
                }
            }
        }

        // KPI Summary Row
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SaffronStatCard(strings.present, presentCount.toString(), Icons.Default.CheckCircle, SuccessGreen, modifier = Modifier.weight(1f))
                SaffronStatCard(strings.absent, absentCount.toString(), Icons.Default.PersonOff, ErrorRed, modifier = Modifier.weight(1f))
                SaffronStatCard(strings.late, lateCount.toString(), Icons.Default.Schedule, WarningAmber, modifier = Modifier.weight(1f))
                SaffronStatCard(strings.excused, excusedCount.toString(), Icons.Default.Assessment, InfoBlue, modifier = Modifier.weight(1f))
            }
        }

        // Export Buttons: PDF, Excel, CSV
        item {
            SectionHeader(title = "Export Printable Report (${filtered.size} rows)")
            Spacer(Modifier.height(6.dp))
            val reportTitle = "${period.name} Attendance Report"
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        val res = QrSecurityManager.exportAttendancePdf(
                            context = context,
                            schoolName = school?.schoolName ?: "Students Attendance by QR",
                            records = filtered,
                            reportTitle = reportTitle,
                        )
                        exportNotice = res.fold(
                            onSuccess = { "PDF exported (${filtered.size} rows)." },
                            onFailure = { it.message ?: "PDF export failed." }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    modifier = Modifier.weight(1f).testTag("export_pdf_btn"),
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("PDF")
                }
                OutlinedButton(
                    onClick = {
                        val res = QrSecurityManager.exportAttendanceCsvOrExcel(context, filtered, reportTitle, asExcelExtension = true)
                        exportNotice = res.fold(
                            onSuccess = { "Excel spreadsheet exported." },
                            onFailure = { it.message ?: "Excel export failed." }
                        )
                    },
                    border = BorderStroke(1.dp, SaffronPrimary),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    modifier = Modifier.weight(1f).testTag("export_excel_btn"),
                ) {
                    Icon(Icons.Default.TableChart, contentDescription = null, tint = SaffronPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Excel", color = SaffronPrimary)
                }
                OutlinedButton(
                    onClick = {
                        val res = QrSecurityManager.exportAttendanceCsvOrExcel(context, filtered, reportTitle, asExcelExtension = false)
                        exportNotice = res.fold(
                            onSuccess = { "CSV report exported." },
                            onFailure = { it.message ?: "CSV export failed." }
                        )
                    },
                    border = BorderStroke(1.dp, SaffronDark),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    modifier = Modifier.weight(1f).testTag("export_csv_btn"),
                ) {
                    Text("CSV", color = SaffronDark)
                }
            }
            if (!exportNotice.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(exportNotice!!, style = MaterialTheme.typography.bodySmall, color = SuccessGreen)
            }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Default.Assessment,
                    title = "No Attendance Records Match",
                    subtitle = "Try switching the report period or scanning students in the QR Scanner.",
                )
            }
        } else {
            items(filtered, key = { it.id }) { rec ->
                SaffronCard(padding = PaddingValues(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rec.studentName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "${rec.className}-${rec.section} • Roll ${rec.rollNumber} • ${rec.attendanceDate} ${rec.attendanceTime}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "By: ${rec.recordedByName} (${rec.method})" +
                                    if (rec.correctionNote.isNotBlank()) " • Note: ${rec.correctionNote}" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = SaffronDark,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AttendanceStatusBadge(rec.statusEnum)
                            if (canCorrectAttendance) {
                                IconButton(
                                    onClick = { editingRecord = rec },
                                    modifier = Modifier.testTag("correct_attendance_${rec.id}"),
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Correct Attendance", tint = SaffronPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingRecord?.let { rec ->
        var newStatus by remember(rec) { mutableStateOf(rec.statusEnum) }
        var note by remember(rec) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { editingRecord = null },
            title = { Text("Correct Attendance (Audit Logged)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${rec.studentName} • ${rec.attendanceDate}", fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AttendanceStatus.entries.forEach { st ->
                            FilterChip(
                                selected = newStatus == st,
                                onClick = { newStatus = st },
                                label = { Text(st.name) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Audit Reason (min 3 chars) *") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (note.trim().length >= 3) {
                            onCorrectAttendance(rec, newStatus, note.trim())
                            editingRecord = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary),
                ) { Text("Save Correction") }
            },
            dismissButton = {
                TextButton(onClick = { editingRecord = null }) { Text("Cancel") }
            },
        )
    }
}
