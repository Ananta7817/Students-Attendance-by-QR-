package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.example.data.model.AnnouncementCategory
import com.example.data.model.GlobalAnnouncement
import com.example.data.model.School
import com.example.data.model.SchoolStatus
import com.example.data.model.SuperAdminConfig
import com.example.data.model.SystemLog
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.LanguagePillSwitcher
import com.example.ui.components.LoadingStateView
import com.example.ui.components.SaffronCard
import com.example.ui.components.SaffronHeroHeader
import com.example.ui.components.SaffronSearchBar
import com.example.ui.components.SaffronStatCard
import com.example.ui.components.SaffronSubScreenBar
import com.example.ui.components.SchoolStatusBadge
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.ErrorLight
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.InfoBlue
import com.example.ui.theme.SaffronDark
import com.example.ui.theme.SaffronLight
import com.example.ui.theme.SaffronPrimary
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SuccessLight
import com.example.ui.theme.WarningAmber
import com.example.util.AppLanguage
import com.example.util.LocalAppStrings

@Composable
fun SuperAdminDashboardScreen(
    schoolsState: UiState<List<School>>,
    usersState: UiState<List<UserProfile>>,
    systemLogsState: UiState<List<SystemLog>>,
    globalAnnouncements: List<GlobalAnnouncement>,
    language: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
    onOpenSchoolDetail: (School) -> Unit,
    onInspectSchoolPanel: (School) -> Unit,
    onApproveSchool: (School) -> Unit,
    onOpenPublishGlobalNotice: () -> Unit,
    onGoToSchoolsTab: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val schools = (schoolsState as? UiState.Success)?.data.orEmpty()
    val users = (usersState as? UiState.Success)?.data.orEmpty()
    val logs = (systemLogsState as? UiState.Success)?.data.orEmpty()

    val approvedCount = schools.count { it.statusEnum == SchoolStatus.APPROVED }
    val pendingSchools = schools.filter { it.statusEnum == SchoolStatus.PENDING }
    val suspendedCount = schools.count { it.statusEnum == SchoolStatus.SUSPENDED }
    val totalStudents = schools.sumOf { it.studentCount }
    val totalTeachers = schools.sumOf { it.teacherCount }
    val totalClasses = schools.sumOf { it.classCount }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SaffronHeroHeader(
                title = "Super Admin Command",
                subtitle = "${SuperAdminConfig.PERMANENT_NAME} • Multi-School Platform",
                badgeText = "PERMANENT SUPER ADMIN",
                trailing = { LanguagePillSwitcher(language, onSelectLanguage) },
            )
        }

        if (schoolsState is UiState.Loading) {
            item { LoadingStateView("Syncing platform schools...") }
        } else if (schoolsState is UiState.Error) {
            item {
                SaffronCard {
                    Text("Error: ${schoolsState.message}", color = ErrorRed)
                }
            }
        } else {
            item {
                SectionHeader(title = "Platform Statistics")
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        SaffronStatCard(
                            label = strings.totalSchools,
                            value = schools.size.toString(),
                            icon = Icons.Default.School,
                            modifier = Modifier.weight(1f),
                            onClick = onGoToSchoolsTab,
                        )
                        SaffronStatCard(
                            label = strings.approvedSchools,
                            value = approvedCount.toString(),
                            icon = Icons.Default.CheckCircle,
                            accentColor = SuccessGreen,
                            modifier = Modifier.weight(1f),
                            onClick = onGoToSchoolsTab,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        SaffronStatCard(
                            label = strings.pendingSchools,
                            value = pendingSchools.size.toString(),
                            icon = Icons.Default.HourglassTop,
                            accentColor = WarningAmber,
                            modifier = Modifier.weight(1f),
                            onClick = onGoToSchoolsTab,
                        )
                        SaffronStatCard(
                            label = strings.suspendedSchools,
                            value = suspendedCount.toString(),
                            icon = Icons.Default.Block,
                            accentColor = ErrorRed,
                            modifier = Modifier.weight(1f),
                            onClick = onGoToSchoolsTab,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        SaffronStatCard(
                            label = strings.totalStudents,
                            value = totalStudents.toString(),
                            icon = Icons.Default.Groups,
                            accentColor = SaffronPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        SaffronStatCard(
                            label = strings.totalTeachers,
                            value = totalTeachers.toString(),
                            icon = Icons.Default.Person,
                            accentColor = InfoBlue,
                            modifier = Modifier.weight(1f),
                        )
                        SaffronStatCard(
                            label = strings.totalClasses,
                            value = totalClasses.toString(),
                            icon = Icons.Default.Class,
                            accentColor = SaffronDark,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            if (pendingSchools.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Pending School Approvals (${pendingSchools.size})",
                        actionLabel = "View All",
                        onActionClick = onGoToSchoolsTab,
                    )
                }
                items(pendingSchools.take(5), key = { it.id }) { school ->
                    SaffronCard(onClick = { onOpenSchoolDetail(school) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(school.schoolName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "UDISE: ${school.schoolCode} • ${school.district}, ${school.state}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text("Headmaster: ${school.headmasterName}", style = MaterialTheme.typography.bodySmall)
                            }
                            Button(
                                onClick = { onApproveSchool(school) },
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                modifier = Modifier.testTag("quick_approve_${school.id}"),
                            ) {
                                Text(strings.approve)
                            }
                        }
                    }
                }
            }

            item {
                SectionHeader(
                    title = "Global Announcements (${globalAnnouncements.size})",
                    actionLabel = "Publish Notice",
                    onActionClick = onOpenPublishGlobalNotice,
                )
            }
            if (globalAnnouncements.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Campaign,
                        title = "No Global Announcements Yet",
                        subtitle = "Broadcast platform-wide circulars or holiday notices to all approved schools.",
                    )
                }
            } else {
                items(globalAnnouncements.take(3), key = { it.id }) { notice ->
                    SaffronCard {
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
            }

            item {
                SectionHeader(title = "Monitor Approved Schools")
            }
            val approvedList = schools.filter { it.statusEnum == SchoolStatus.APPROVED }
            if (approvedList.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Assessment,
                        title = "No Approved Schools Yet",
                        subtitle = "Approve pending school applications to monitor live attendance and reports.",
                    )
                }
            } else {
                items(approvedList.take(5), key = { it.id }) { school ->
                    SaffronCard(onClick = { onInspectSchoolPanel(school) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(school.schoolName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "${school.studentCount} Students • ${school.teacherCount} Teachers • ${school.classCount} Classes",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { onInspectSchoolPanel(school) }) {
                                Text("Monitor", color = SaffronPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (logs.isNotEmpty()) {
                item {
                    SectionHeader(title = "Recent System Audit Logs (${users.size} Users)")
                }
                items(logs.take(5), key = { it.id }) { log ->
                    SaffronCard(padding = PaddingValues(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.History, contentDescription = null, tint = SaffronPrimary)
                            Column {
                                Text(log.action, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Text(log.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SchoolManagementListScreen(
    schoolsState: UiState<List<School>>,
    onSelectSchool: (School) -> Unit,
    onInspectSchool: (School) -> Unit,
) {
    val strings = LocalAppStrings.current
    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<SchoolStatus?>(null) }

    val allSchools = (schoolsState as? UiState.Success)?.data.orEmpty()
    val filtered = remember(allSchools, search, statusFilter) {
        allSchools.filter { school ->
            val matchesQuery = search.isBlank() ||
                school.schoolName.contains(search, ignoreCase = true) ||
                school.id.contains(search, ignoreCase = true) ||
                school.schoolCode.contains(search, ignoreCase = true) ||
                school.district.contains(search, ignoreCase = true)
            val matchesStatus = statusFilter == null || school.statusEnum == statusFilter
            matchesQuery && matchesStatus
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("School Management", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            SaffronSearchBar(
                query = search,
                onQueryChange = { search = it },
                placeholder = "Search by school name, ID, UDISE, or district...",
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                FilterChip(
                    selected = statusFilter == null,
                    onClick = { statusFilter = null },
                    label = { Text("All (${allSchools.size})") },
                )
                listOf(SchoolStatus.PENDING, SchoolStatus.APPROVED, SchoolStatus.SUSPENDED).forEach { st ->
                    FilterChip(
                        selected = statusFilter == st,
                        onClick = { statusFilter = if (statusFilter == st) null else st },
                        label = { Text(st.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SaffronLight,
                            selectedLabelColor = SaffronDark,
                        ),
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Default.School,
                    title = "No Schools Found",
                    subtitle = "Registered schools will appear here for approval, editing, and attendance monitoring.",
                )
            }
        } else {
            items(filtered, key = { it.id }) { school ->
                SaffronCard(onClick = { onSelectSchool(school) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(school.schoolName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("ID: ${school.id} • UDISE: ${school.schoolCode}", style = MaterialTheme.typography.labelSmall)
                            Text("${school.villageOrTown}, ${school.district}, ${school.state}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "Students: ${school.studentCount} • Teachers: ${school.teacherCount} • Classes: ${school.classCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = SaffronDark,
                            )
                        }
                        SchoolStatusBadge(school.statusEnum)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onSelectSchool(school) },
                            border = BorderStroke(1.dp, SaffronPrimary),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Manage / Approve", color = SaffronPrimary)
                        }
                        if (school.statusEnum == SchoolStatus.APPROVED) {
                            Button(
                                onClick = { onInspectSchool(school) },
                                colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Live Reports")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SchoolDetailAndApprovalScreen(
    school: School,
    onBack: () -> Unit,
    onUpdateStatus: (School, SchoolStatus, String) -> Unit,
    onSaveDetails: (
        schoolId: String,
        schoolName: String,
        schoolCode: String,
        headmasterName: String,
        email: String,
        phone: String,
        address: String,
        villageOrTown: String,
        district: String,
        state: String,
        pinCode: String,
        parentNotificationsEnabled: Boolean,
    ) -> Unit,
    onInspectSchoolPanel: (School) -> Unit,
) {
    BackHandler(onBack = onBack)
    val strings = LocalAppStrings.current
    var editing by remember { mutableStateOf(false) }
    var showRejectDialog by remember { mutableStateOf(false) }
    var rejectReason by remember { mutableStateOf("") }

    var schoolName by remember(school) { mutableStateOf(school.schoolName) }
    var schoolCode by remember(school) { mutableStateOf(school.schoolCode) }
    var headmaster by remember(school) { mutableStateOf(school.headmasterName) }
    var email by remember(school) { mutableStateOf(school.email) }
    var phone by remember(school) { mutableStateOf(school.phone) }
    var address by remember(school) { mutableStateOf(school.address) }
    var village by remember(school) { mutableStateOf(school.villageOrTown) }
    var district by remember(school) { mutableStateOf(school.district) }
    var state by remember(school) { mutableStateOf(school.state) }
    var pinCode by remember(school) { mutableStateOf(school.pinCode) }
    var parentAlerts by remember(school) { mutableStateOf(school.parentNotificationsEnabled) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SaffronSubScreenBar(
            title = school.schoolName,
            subtitle = "School ID: ${school.id}",
            onBack = onBack,
            trailing = {
                TextButton(onClick = { editing = !editing }) {
                    Icon(if (editing) Icons.Default.Close else Icons.Default.Edit, contentDescription = null, tint = SaffronPrimary)
                    Spacer(Modifier.width(4.dp))
                    Text(if (editing) "Cancel" else "Edit", color = SaffronPrimary)
                }
            },
        )

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SaffronCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Approval & Access Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    SchoolStatusBadge(school.statusEnum)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (school.statusEnum != SchoolStatus.APPROVED) {
                        Button(
                            onClick = { onUpdateStatus(school, SchoolStatus.APPROVED, "") },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                            modifier = Modifier.weight(1f).testTag("approve_school_btn"),
                        ) {
                            Text(if (school.statusEnum == SchoolStatus.SUSPENDED) strings.reactivate else strings.approve)
                        }
                    }
                    if (school.statusEnum == SchoolStatus.PENDING) {
                        OutlinedButton(
                            onClick = { showRejectDialog = true },
                            border = BorderStroke(1.dp, ErrorRed),
                            modifier = Modifier.weight(1f).testTag("reject_school_btn"),
                        ) {
                            Text(strings.reject, color = ErrorRed)
                        }
                    }
                    if (school.statusEnum == SchoolStatus.APPROVED) {
                        OutlinedButton(
                            onClick = { onUpdateStatus(school, SchoolStatus.SUSPENDED, "Suspended by Super Admin") },
                            border = BorderStroke(1.dp, ErrorRed),
                            modifier = Modifier.weight(1f).testTag("suspend_school_btn"),
                        ) {
                            Text(strings.suspend, color = ErrorRed)
                        }
                    }
                }
                if (school.statusEnum == SchoolStatus.APPROVED) {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { onInspectSchoolPanel(school) },
                        colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary),
                        modifier = Modifier.fillMaxWidth().testTag("inspect_school_reports_btn"),
                    ) {
                        Text("Open School Dashboard & Attendance Reports")
                    }
                }
            }

            SaffronCard {
                Text("School Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                if (!editing) {
                    InfoLine("UDISE / School Code", school.schoolCode)
                    InfoLine("Headmaster", school.headmasterName)
                    InfoLine("Email", school.email)
                    InfoLine("Phone", school.phone)
                    InfoLine("Address", "${school.address}, ${school.villageOrTown}")
                    InfoLine("Location", "${school.district}, ${school.state} - ${school.pinCode}")
                    InfoLine("Parent Alerts", if (school.parentNotificationsEnabled) "Enabled" else "Paused")
                    InfoLine("Admin UID", school.adminUid)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(schoolName, { schoolName = it }, label = { Text("School Name") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(schoolCode, { schoolCode = it }, label = { Text("UDISE Code") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(headmaster, { headmaster = it }, label = { Text("Headmaster") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(address, { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(village, { village = it }, label = { Text("Village/Town") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(district, { district = it }, label = { Text("District") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(state, { state = it }, label = { Text("State") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(pinCode, { pinCode = it }, label = { Text("PIN Code") }, modifier = Modifier.fillMaxWidth())
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Parent Attendance Notifications")
                            Switch(checked = parentAlerts, onCheckedChange = { parentAlerts = it })
                        }
                        Button(
                            onClick = {
                                onSaveDetails(
                                    school.id, schoolName, schoolCode, headmaster, email, phone,
                                    address, village, district, state, pinCode, parentAlerts
                                )
                                editing = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Save School Changes")
                        }
                    }
                }
            }
        }
    }

    if (showRejectDialog) {
        AlertDialog(
            onDismissRequest = { showRejectDialog = false },
            title = { Text("Reject School Application") },
            text = {
                OutlinedTextField(
                    value = rejectReason,
                    onValueChange = { rejectReason = it },
                    label = { Text("Reason for rejection") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateStatus(school, SchoolStatus.REJECTED, rejectReason.ifBlank { "Incomplete UDISE verification" })
                        showRejectDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                ) { Text("Confirm Reject") }
            },
            dismissButton = {
                TextButton(onClick = { showRejectDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SuperAdminUserManagementScreen(
    usersState: UiState<List<UserProfile>>,
    onUpdateUser: (UserProfile, UserRole, Boolean) -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val users = (usersState as? UiState.Success)?.data.orEmpty().filter {
        search.isBlank() ||
            it.displayName.contains(search, ignoreCase = true) ||
            it.email.contains(search, ignoreCase = true) ||
            it.role.contains(search, ignoreCase = true)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Platform User Management", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            SaffronSearchBar(query = search, onQueryChange = { search = it }, placeholder = "Search admins or teachers...")
        }
        if (users.isEmpty()) {
            item {
                EmptyStateCard(Icons.Default.Groups, "No Users Found", "School Admins and Teachers will appear here.")
            }
        } else {
            items(users, key = { it.uid.ifBlank { it.id } }) { user ->
                val isPermanentSuper = user.uid == SuperAdminConfig.PERMANENT_UID || user.roleEnum == UserRole.SUPER_ADMIN
                SaffronCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(user.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                if (isPermanentSuper) {
                                    Icon(Icons.Default.Verified, contentDescription = "Protected Super Admin", tint = SaffronPrimary)
                                }
                            }
                            Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (user.schoolName.isNotBlank()) {
                                Text("School: ${user.schoolName}", style = MaterialTheme.typography.bodySmall, color = SaffronDark)
                            }
                        }
                        StatusPill(
                            text = user.role.replace("_", " "),
                            backgroundColor = if (user.active) SaffronLight else ErrorLight,
                            contentColor = if (user.active) SaffronDark else ErrorRed,
                        )
                    }
                    if (!isPermanentSuper) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onUpdateUser(user, user.roleEnum, !user.active) },
                                border = BorderStroke(1.dp, if (user.active) ErrorRed else SuccessGreen),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    if (user.active) "Deactivate" else "Activate",
                                    color = if (user.active) ErrorRed else SuccessGreen,
                                )
                            }
                            val toggledRole = if (user.roleEnum == UserRole.SCHOOL_ADMIN) UserRole.TEACHER else UserRole.SCHOOL_ADMIN
                            OutlinedButton(
                                onClick = { onUpdateUser(user, toggledRole, user.active) },
                                border = BorderStroke(1.dp, SaffronPrimary),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Set ${toggledRole.name.replace("_", " ")}", color = SaffronPrimary)
                            }
                        }
                    } else {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Protected Permanent Super Admin — immutable via client operations.",
                            style = MaterialTheme.typography.labelSmall,
                            color = SuccessGreen,
                        )
                    }
                }
            }
        }
    }
}
