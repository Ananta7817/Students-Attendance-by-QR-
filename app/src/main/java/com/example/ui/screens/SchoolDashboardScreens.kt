package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.PendingAttendanceEntity
import com.example.data.model.Announcement
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceStatus
import com.example.data.model.GlobalAnnouncement
import com.example.data.model.School
import com.example.data.model.SchoolClass
import com.example.data.model.Student
import com.example.data.model.StudentStatus
import com.example.data.model.Teacher
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.ui.components.AttendanceStatusBadge
import com.example.ui.components.AvatarBadge
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.LanguagePillSwitcher
import com.example.ui.components.SaffronCard
import com.example.ui.components.SaffronHeroHeader
import com.example.ui.components.SaffronStatCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.InfoBlue
import com.example.ui.theme.SaffronDark
import com.example.ui.theme.SaffronLight
import com.example.ui.theme.SaffronPrimary
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningLight
import com.example.util.AppLanguage
import com.example.util.LocalAppStrings

@Composable
fun SchoolAdminAndTeacherDashboardScreen(
    school: School?,
    profile: UserProfile,
    students: List<Student>,
    teachers: List<Teacher>,
    classes: List<SchoolClass>,
    attendance: List<AttendanceRecord>,
    announcements: List<Announcement>,
    globalAnnouncements: List<GlobalAnnouncement>,
    offlinePending: List<PendingAttendanceEntity>,
    todayDate: String,
    language: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
    onLaunchScanner: (String?) -> Unit,
    onSyncOffline: () -> Unit,
    onOpenStudents: () -> Unit,
    onOpenTeachers: () -> Unit,
    onOpenClasses: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenAnnouncements: () -> Unit,
    onOpenQrBatch: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val isTeacher = profile.roleEnum == UserRole.TEACHER
    val assignedClasses = if (isTeacher) {
        classes.filter { it.id in profile.assignedClassIds }
    } else {
        classes
    }
    val activeStudents = students.filter {
        it.statusEnum == StudentStatus.ACTIVE &&
            (!isTeacher || it.classId in profile.assignedClassIds)
    }
    val todayRecords = attendance.filter {
        it.attendanceDate == todayDate &&
            (!isTeacher || it.classId in profile.assignedClassIds)
    }
    val presentToday = todayRecords.count { it.statusEnum == AttendanceStatus.PRESENT || it.statusEnum == AttendanceStatus.LATE }
    val absentToday = (activeStudents.size - presentToday).coerceAtLeast(0)
    val percentage = if (activeStudents.isEmpty()) 0 else (presentToday * 100 / activeStudents.size)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SaffronHeroHeader(
                title = school?.schoolName ?: "School Dashboard",
                subtitle = "${profile.displayName} • ${school?.district ?: ""} ${school?.schoolCode ?: ""}".trim(),
                badgeText = if (isTeacher) "TEACHER PORTAL" else "SCHOOL ADMIN",
                trailing = { LanguagePillSwitcher(language, onSelectLanguage) },
            )
        }

        if (offlinePending.isNotEmpty()) {
            item {
                Surface(
                    color = WarningLight,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${offlinePending.size} Offline Scan(s) Pending Sync",
                                style = MaterialTheme.typography.titleSmall,
                                color = SaffronDark,
                                fontWeight = FontWeight.Bold,
                            )
                            Text("Tap sync to upload queued attendance records.", style = MaterialTheme.typography.bodySmall)
                        }
                        Button(
                            onClick = onSyncOffline,
                            colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary),
                            modifier = Modifier.testTag("sync_offline_btn"),
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Sync")
                        }
                    }
                }
            }
        }

        // Primary QR Scanner CTA
        item {
            Button(
                onClick = { onLaunchScanner(assignedClasses.firstOrNull()?.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("launch_qr_scanner_cta"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary),
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(strings.scanQrAttendance, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }

        // KPI Grid
        item {
            SectionHeader(title = "Today's Overview ($todayDate)")
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    SaffronStatCard(
                        label = strings.presentToday,
                        value = presentToday.toString(),
                        icon = Icons.Default.CheckCircle,
                        accentColor = SuccessGreen,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenReports,
                    )
                    SaffronStatCard(
                        label = strings.absentToday,
                        value = absentToday.toString(),
                        icon = Icons.Default.PersonOff,
                        accentColor = ErrorRed,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenReports,
                    )
                    SaffronStatCard(
                        label = strings.attendanceRate,
                        value = "$percentage%",
                        icon = Icons.Default.Assessment,
                        accentColor = SaffronPrimary,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenReports,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    SaffronStatCard(
                        label = strings.totalStudents,
                        value = activeStudents.size.toString(),
                        icon = Icons.Default.Groups,
                        accentColor = SaffronDark,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenStudents,
                    )
                    if (!isTeacher) {
                        SaffronStatCard(
                            label = strings.totalTeachers,
                            value = teachers.size.toString(),
                            icon = Icons.Default.Person,
                            accentColor = InfoBlue,
                            modifier = Modifier.weight(1f),
                            onClick = onOpenTeachers,
                        )
                    }
                    SaffronStatCard(
                        label = if (isTeacher) "My Classes" else strings.totalClasses,
                        value = assignedClasses.size.toString(),
                        icon = Icons.Default.Class,
                        accentColor = WarningAmber,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenClasses,
                    )
                }
            }
        }

        // Quick Actions
        if (!isTeacher) {
            item {
                SectionHeader(title = "Quick School Actions")
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onOpenClasses,
                        border = BorderStroke(1.dp, SaffronPrimary),
                        modifier = Modifier.weight(1f).testTag("quick_classes_btn"),
                    ) {
                        Icon(Icons.Default.Class, contentDescription = null, tint = SaffronPrimary)
                        Spacer(Modifier.width(4.dp))
                        Text(strings.classes, color = SaffronPrimary)
                    }
                    OutlinedButton(
                        onClick = onOpenTeachers,
                        border = BorderStroke(1.dp, SaffronPrimary),
                        modifier = Modifier.weight(1f).testTag("quick_teachers_btn"),
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = SaffronPrimary)
                        Spacer(Modifier.width(4.dp))
                        Text(strings.teachers, color = SaffronPrimary)
                    }
                    OutlinedButton(
                        onClick = onOpenQrBatch,
                        border = BorderStroke(1.dp, SaffronPrimary),
                        modifier = Modifier.weight(1f).testTag("quick_qr_cards_btn"),
                    ) {
                        Icon(Icons.Default.QrCode2, contentDescription = null, tint = SaffronPrimary)
                        Spacer(Modifier.width(4.dp))
                        Text("QR", color = SaffronPrimary)
                    }
                }
            }
        } else {
            item {
                SectionHeader(title = "Your Assigned Classes")
            }
            if (assignedClasses.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Class,
                        title = "No Classes Assigned",
                        subtitle = "Ask your School Admin to assign classes to your Teacher profile.",
                    )
                }
            } else {
                items(assignedClasses, key = { it.id }) { cls ->
                    SaffronCard(onClick = { onLaunchScanner(cls.id) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(cls.className, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Sections: ${cls.sections.joinToString()}", style = MaterialTheme.typography.bodySmall)
                            }
                            Button(
                                onClick = { onLaunchScanner(cls.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary),
                            ) {
                                Text("Scan QR")
                            }
                        }
                    }
                }
            }
        }

        // Recent Attendance
        item {
            SectionHeader(
                title = "Recent Attendance",
                actionLabel = "All Reports",
                onActionClick = onOpenReports,
            )
        }
        if (todayRecords.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Default.QrCodeScanner,
                    title = "No Attendance Recorded Today",
                    subtitle = "Open the QR Scanner or Manual Attendance to record today's attendance.",
                )
            }
        } else {
            items(todayRecords.take(6), key = { it.id }) { rec ->
                SaffronCard(padding = PaddingValues(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AvatarBadge(name = rec.studentName, photoUrl = rec.studentPhotoUrl, size = 42.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rec.studentName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "${rec.className}-${rec.section} • Roll ${rec.rollNumber} • ${rec.attendanceTime}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        AttendanceStatusBadge(rec.statusEnum)
                    }
                }
            }
        }

        // Latest Announcements
        item {
            SectionHeader(
                title = strings.announcements,
                actionLabel = "View All",
                onActionClick = onOpenAnnouncements,
            )
        }
        if (announcements.isEmpty() && globalAnnouncements.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Default.Campaign,
                    title = "No Notices Yet",
                    subtitle = "School circulars, holiday alerts, and exam notices will appear here.",
                )
            }
        } else {
            items(announcements.take(3), key = { it.id }) { notice ->
                SaffronCard(padding = PaddingValues(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(notice.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        StatusPill(notice.category, SaffronLight, SaffronDark)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(notice.body, style = MaterialTheme.typography.bodySmall)
                }
            }
            items(globalAnnouncements.take(2), key = { "global_${it.id}" }) { notice ->
                SaffronCard(padding = PaddingValues(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("[Platform] ${notice.title}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        StatusPill(notice.category, SaffronLight, SaffronDark)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(notice.body, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
