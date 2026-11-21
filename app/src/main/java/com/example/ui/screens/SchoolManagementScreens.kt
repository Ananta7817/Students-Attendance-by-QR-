package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.AppPreferencesState
import com.example.data.model.Announcement
import com.example.data.model.AnnouncementCategory
import com.example.data.model.AttendanceRecord
import com.example.data.model.AuditLog
import com.example.data.model.GlobalAnnouncement
import com.example.data.model.School
import com.example.data.model.SchoolClass
import com.example.data.model.Student
import com.example.data.model.Teacher
import com.example.data.model.UserProfile
import com.example.ui.components.AvatarBadge
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SaffronCard
import com.example.ui.components.SaffronSubScreenBar
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.ErrorLight
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SaffronDark
import com.example.ui.theme.SaffronLight
import com.example.ui.theme.SaffronPrimary
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SuccessLight
import com.example.util.AppLanguage
import com.example.util.LocalAppStrings

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeacherManagementScreen(
    teachers: List<Teacher>,
    classes: List<SchoolClass>,
    attendance: List<AttendanceRecord>,
    canEdit: Boolean,
    onBack: () -> Unit,
    onSaveTeacher: (
        existing: Teacher?,
        name: String,
        email: String,
        phone: String,
        assignedClassIds: List<String>,
        assignedSections: List<String>,
        assignedSubjects: List<String>,
        active: Boolean,
    ) -> Unit,
) {
    BackHandler(onBack = onBack)
    var showDialog by remember { mutableStateOf(false) }
    var editingTeacher by remember { mutableStateOf<Teacher?>(null) }

    Scaffold(
        topBar = {
            SaffronSubScreenBar(
                title = "Teacher Management (${teachers.size})",
                subtitle = "Assign classes, sections, subjects & Google access",
                onBack = onBack,
            )
        },
        floatingActionButton = {
            if (canEdit) {
                FloatingActionButton(
                    onClick = { editingTeacher = null; showDialog = true },
                    containerColor = SaffronPrimary,
                    contentColor = PureWhite,
                    modifier = Modifier.testTag("add_teacher_fab"),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Teacher")
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (teachers.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Person,
                        title = "No Teachers Added",
                        subtitle = "Tap + to add a teacher with their Google email so they can sign in and scan QR attendance.",
                    )
                }
            } else {
                items(teachers, key = { it.id }) { teacher ->
                    val submittedCount = attendance.count {
                        it.recordedByUid.isNotBlank() && it.recordedByUid == teacher.userUid
                    }
                    val classNames = classes.filter { it.id in teacher.assignedClassIds }.map { it.className }
                    SaffronCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AvatarBadge(name = teacher.teacherName, photoUrl = teacher.photoUrl)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(teacher.teacherName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("${teacher.email} • ${teacher.phone}", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "Classes: ${classNames.joinToString().ifBlank { "None" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SaffronDark,
                                )
                                Text(
                                    "Subjects: ${teacher.assignedSubjects.joinToString().ifBlank { "General" }} • Scans: $submittedCount",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                StatusPill(
                                    text = if (teacher.active) "Active" else "Inactive",
                                    backgroundColor = if (teacher.active) SuccessLight else ErrorLight,
                                    contentColor = if (teacher.active) SuccessGreen else ErrorRed,
                                )
                                if (canEdit) {
                                    IconButton(onClick = { editingTeacher = teacher; showDialog = true }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Teacher", tint = SaffronPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showDialog) {
        var name by remember(editingTeacher) { mutableStateOf(editingTeacher?.teacherName.orEmpty()) }
        var email by remember(editingTeacher) { mutableStateOf(editingTeacher?.email.orEmpty()) }
        var phone by remember(editingTeacher) { mutableStateOf(editingTeacher?.phone.orEmpty()) }
        var subjectsCsv by remember(editingTeacher) { mutableStateOf(editingTeacher?.assignedSubjects?.joinToString(", ").orEmpty()) }
        var sectionsCsv by remember(editingTeacher) { mutableStateOf(editingTeacher?.assignedSections?.joinToString(", ").orEmpty().ifBlank { "A" }) }
        var selectedClasses by remember(editingTeacher) { mutableStateOf(editingTeacher?.assignedClassIds.orEmpty().toSet()) }
        var active by remember(editingTeacher) { mutableStateOf(editingTeacher?.active ?: true) }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (editingTeacher == null) "Add Teacher" else "Edit Teacher") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(name, { name = it }, label = { Text("Teacher Name *") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        enabled = editingTeacher == null,
                        label = { Text("Google Email *") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(phone, { phone = it }, label = { Text("Phone Number") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(subjectsCsv, { subjectsCsv = it }, label = { Text("Subjects (comma-separated)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(sectionsCsv, { sectionsCsv = it }, label = { Text("Sections (e.g. A, B)") }, modifier = Modifier.fillMaxWidth())
                    Text("Assigned Classes (QR Scan Scope):", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        classes.forEach { cls ->
                            val isSelected = cls.id in selectedClasses
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedClasses = if (isSelected) selectedClasses - cls.id else selectedClasses + cls.id
                                },
                                label = { Text(cls.className) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SaffronLight,
                                    selectedLabelColor = SaffronDark,
                                ),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Account Active")
                        Switch(checked = active, onCheckedChange = { active = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank() && email.contains("@")) {
                            onSaveTeacher(
                                editingTeacher,
                                name,
                                email,
                                phone,
                                selectedClasses.toList(),
                                sectionsCsv.split(",").map { it.trim().uppercase() }.filter { it.isNotBlank() },
                                subjectsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() },
                                active,
                            )
                            showDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary),
                ) { Text("Save Teacher") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
fun ClassAndSectionManagementScreen(
    classes: List<SchoolClass>,
    teachers: List<Teacher>,
    students: List<Student>,
    todayAttendance: List<AttendanceRecord>,
    canEdit: Boolean,
    onBack: () -> Unit,
    onSaveClass: (existingId: String?, className: String, sections: List<String>, teacherId: String, teacherName: String) -> Unit,
    onDeleteClass: (String) -> Unit,
) {
    BackHandler(onBack = onBack)
    var showDialog by remember { mutableStateOf(false) }
    var editingClass by remember { mutableStateOf<SchoolClass?>(null) }

    Scaffold(
        topBar = {
            SaffronSubScreenBar(
                title = "Classes & Sections (${classes.size})",
                subtitle = "Manage classes, sections & class teachers",
                onBack = onBack,
            )
        },
        floatingActionButton = {
            if (canEdit) {
                FloatingActionButton(
                    onClick = { editingClass = null; showDialog = true },
                    containerColor = SaffronPrimary,
                    contentColor = PureWhite,
                    modifier = Modifier.testTag("add_class_fab"),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Class")
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (classes.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Class,
                        title = "No Classes Created",
                        subtitle = "Create classes like Class 1, Class 10, etc. with sections A, B, C.",
                    )
                }
            } else {
                items(classes, key = { it.id }) { cls ->
                    val classStudentCount = students.count { it.classId == cls.id }
                    val presentCount = todayAttendance.count { it.classId == cls.id }
                    SaffronCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(cls.className, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Sections: ${cls.sections.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = SaffronDark)
                                if (cls.classTeacherName.isNotBlank()) {
                                    Text("Class Teacher: ${cls.classTeacherName}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    "Students: $classStudentCount • Present Today: $presentCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SuccessGreen,
                                )
                            }
                            if (canEdit) {
                                Row {
                                    IconButton(onClick = { editingClass = cls; showDialog = true }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Class", tint = SaffronPrimary)
                                    }
                                    IconButton(onClick = { onDeleteClass(cls.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Class", tint = ErrorRed)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showDialog) {
        var className by remember(editingClass) { mutableStateOf(editingClass?.className.orEmpty()) }
        var sectionsText by remember(editingClass) { mutableStateOf(editingClass?.sections?.joinToString(", ").orEmpty().ifBlank { "A, B" }) }
        var selectedTeacher by remember(editingClass) {
            mutableStateOf(teachers.firstOrNull { it.id == editingClass?.classTeacherId })
        }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (editingClass == null) "Create Class" else "Edit Class") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(className, { className = it }, label = { Text("Class Name (e.g. Class 10) *") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(sectionsText, { sectionsText = it }, label = { Text("Sections (comma-separated: A, B, C)") }, modifier = Modifier.fillMaxWidth())
                    if (teachers.isNotEmpty()) {
                        Text("Assign Class Teacher (Optional):", style = MaterialTheme.typography.labelMedium)
                        teachers.take(5).forEach { t ->
                            FilterChip(
                                selected = selectedTeacher?.id == t.id,
                                onClick = { selectedTeacher = if (selectedTeacher?.id == t.id) null else t },
                                label = { Text(t.teacherName) },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (className.isNotBlank()) {
                            onSaveClass(
                                editingClass?.id,
                                className,
                                sectionsText.split(",").map { it.trim() }.filter { it.isNotBlank() },
                                selectedTeacher?.id.orEmpty(),
                                selectedTeacher?.teacherName.orEmpty(),
                            )
                            showDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary),
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun AnnouncementsAndNotificationsScreen(
    school: School?,
    announcements: List<Announcement>,
    globalAnnouncements: List<GlobalAnnouncement>,
    auditLogs: List<AuditLog>,
    canPublishSchoolNotice: Boolean,
    canToggleParentAlerts: Boolean,
    onBack: (() -> Unit)?,
    onPublishNotice: (title: String, body: String, category: AnnouncementCategory) -> Unit,
    onToggleParentNotifications: (Boolean) -> Unit,
) {
    if (onBack != null) BackHandler(onBack = onBack)
    val strings = LocalAppStrings.current
    var showCompose by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (onBack != null) {
                SaffronSubScreenBar(title = strings.announcements, subtitle = "Notices, Parent Alerts & Audit Logs", onBack = onBack)
            }
        },
        floatingActionButton = {
            if (canPublishSchoolNotice) {
                FloatingActionButton(
                    onClick = { showCompose = true },
                    containerColor = SaffronPrimary,
                    contentColor = PureWhite,
                    modifier = Modifier.testTag("publish_notice_fab"),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Publish Announcement")
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (onBack == null) {
                item {
                    Text("Announcements & Notifications", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }

            if (school != null) {
                item {
                    SaffronCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(Icons.Default.Notifications, contentDescription = null, tint = SaffronPrimary)
                                Column {
                                    Text(strings.parentNotifications, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        "Trigger FCM attendance alerts to parents when students are marked Present/Absent.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Switch(
                                checked = school.parentNotificationsEnabled,
                                enabled = canToggleParentAlerts,
                                onCheckedChange = onToggleParentNotifications,
                            )
                        }
                    }
                }
            }

            item { SectionHeader(title = "School Announcements (${announcements.size})") }
            if (announcements.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Campaign,
                        title = "No School Announcements",
                        subtitle = "Holiday notices, exam dates, and updates posted by School Admin appear here.",
                    )
                }
            } else {
                items(announcements, key = { it.id }) { notice ->
                    SaffronCard {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(notice.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            StatusPill(notice.category, SaffronLight, SaffronDark)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(notice.body, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        Text("Posted by ${notice.authorName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (globalAnnouncements.isNotEmpty()) {
                item { SectionHeader(title = strings.globalAnnouncements) }
                items(globalAnnouncements, key = { "g_${it.id}" }) { notice ->
                    SaffronCard {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(notice.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            StatusPill("SYSTEM • ${notice.category}", SaffronLight, SaffronDark)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(notice.body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (auditLogs.isNotEmpty()) {
                item { SectionHeader(title = "${strings.auditTrail} (${auditLogs.size})") }
                items(auditLogs.take(15), key = { it.id }) { log ->
                    SaffronCard(padding = PaddingValues(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.History, contentDescription = null, tint = SaffronPrimary)
                            Column {
                                Text("${log.action} • ${log.actorName}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Text(log.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showCompose) {
        ComposeAnnouncementDialog(
            dialogTitle = "Publish School Announcement",
            onDismiss = { showCompose = false },
            onPublish = { t, b, cat ->
                onPublishNotice(t, b, cat)
                showCompose = false
            },
        )
    }
}

@Composable
fun ComposeAnnouncementDialog(
    dialogTitle: String,
    onDismiss: () -> Unit,
    onPublish: (String, String, AnnouncementCategory) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(AnnouncementCategory.GENERAL) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Headline *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(body, { body = it }, label = { Text("Message *") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AnnouncementCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.name.take(4)) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && body.isNotBlank()) onPublish(title, body, category)
                },
                colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary),
            ) { Text("Publish") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ProfileAndSettingsScreen(
    profile: UserProfile,
    school: School?,
    preferences: AppPreferencesState,
    offlinePendingCount: Int,
    onSelectLanguage: (AppLanguage) -> Unit,
    onToggleVibrate: (Boolean) -> Unit,
    onToggleAutoSync: (Boolean) -> Unit,
    onToggleDarkTheme: (Boolean) -> Unit,
    onUpdateProfile: (String, String) -> Unit,
    onSyncOffline: () -> Unit,
    onSignOut: () -> Unit,
) {
    val strings = LocalAppStrings.current
    var displayName by remember(profile) { mutableStateOf(profile.displayName) }
    var phone by remember(profile) { mutableStateOf(profile.phone) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("${strings.profile} & ${strings.settings}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        SaffronCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AvatarBadge(name = profile.displayName, photoUrl = profile.photoUrl, size = 60.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(profile.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(profile.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    StatusPill(profile.role.replace("_", " "), SaffronLight, SaffronDark)
                }
            }
            if (school != null) {
                Spacer(Modifier.height(10.dp))
                Text("School: ${school.schoolName} (${school.id})", style = MaterialTheme.typography.bodySmall, color = SaffronDark)
            }
        }

        SaffronCard {
            Text(strings.languageLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                AppLanguage.entries.forEach { lang ->
                    val selected = preferences.language == lang
                    if (selected) {
                        Button(
                            onClick = { onSelectLanguage(lang) },
                            colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary),
                            modifier = Modifier.weight(1f).testTag("settings_lang_${lang.code}"),
                        ) { Text(lang.displayLabel) }
                    } else {
                        OutlinedButton(
                            onClick = { onSelectLanguage(lang) },
                            border = BorderStroke(1.dp, SaffronPrimary),
                            modifier = Modifier.weight(1f).testTag("settings_lang_${lang.code}"),
                        ) { Text(lang.displayLabel, color = SaffronPrimary) }
                    }
                }
            }
        }

        SaffronCard {
            Text("Edit Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(displayName, { displayName = it }, label = { Text("Display Name") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(phone, { phone = it }, label = { Text("Phone Number") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { onUpdateProfile(displayName, phone) },
                colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary),
                modifier = Modifier.fillMaxWidth().testTag("save_profile_btn"),
            ) { Text(strings.save) }
        }

        SaffronCard {
            Text("App Preferences", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            SettingToggleRow("Vibrate on QR Scan", preferences.vibrateOnScan, onToggleVibrate)
            SettingToggleRow("Auto-Sync Offline Attendance", preferences.autoSyncOffline, onToggleAutoSync)
            SettingToggleRow("Warm Dark Theme", preferences.darkTheme, onToggleDarkTheme)
            if (offlinePendingCount > 0) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onSyncOffline,
                    border = BorderStroke(1.dp, SaffronPrimary),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = SaffronPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("${strings.syncOfflineQueue} ($offlinePendingCount)", color = SaffronPrimary)
                }
            }
        }

        OutlinedButton(
            onClick = onSignOut,
            border = BorderStroke(1.5.dp, ErrorRed),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("sign_out_button"),
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = ErrorRed)
            Spacer(Modifier.width(8.dp))
            Text(strings.signOut, color = ErrorRed, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
