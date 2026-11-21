package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.auth.authStateFlow
import com.example.data.auth.performSignOut
import com.example.data.local.AppPreferencesState
import com.example.data.local.UserPreferencesRepository
import com.example.data.model.School
import com.example.data.model.SchoolStatus
import com.example.data.model.Student
import com.example.data.model.SuperAdminConfig
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.ui.components.LoadingStateView
import com.example.ui.components.SaffronSubScreenBar
import com.example.ui.screens.AddEditStudentScreen
import com.example.ui.screens.AnnouncementsAndNotificationsScreen
import com.example.ui.screens.AttendanceReportsAndHistoryScreen
import com.example.ui.screens.ClassAndSectionManagementScreen
import com.example.ui.screens.ComposeAnnouncementDialog
import com.example.ui.screens.OnboardingAndSchoolRegistrationScreen
import com.example.ui.screens.ProfileAndSettingsScreen
import com.example.ui.screens.QrScannerAndManualAttendanceScreen
import com.example.ui.screens.SchoolAdminAndTeacherDashboardScreen
import com.example.ui.screens.SchoolApprovalGateScreen
import com.example.ui.screens.SchoolDetailAndApprovalScreen
import com.example.ui.screens.SchoolManagementListScreen
import com.example.ui.screens.StudentManagementScreen
import com.example.ui.screens.StudentProfileScreen
import com.example.ui.screens.StudentQrCardScreen
import com.example.ui.screens.SuperAdminDashboardScreen
import com.example.ui.screens.SuperAdminUserManagementScreen
import com.example.ui.screens.TeacherManagementScreen
import com.example.ui.screens.UnauthenticatedFlow
import com.example.ui.theme.SaffronDark
import com.example.ui.theme.SaffronLight
import com.example.ui.theme.SaffronPrimary
import com.example.ui.theme.StudentsAttendanceTheme
import com.example.ui.viewmodel.SchoolViewModel
import com.example.ui.viewmodel.SessionViewModel
import com.example.ui.viewmodel.SuperAdminViewModel
import com.example.util.AppLanguage
import com.example.util.BengaliStrings
import com.example.util.EnglishStrings
import com.example.util.LocalAppStrings
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppRoot()
        }
    }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefsRepo = remember(context) { UserPreferencesRepository(context.applicationContext) }
    val prefs by prefsRepo.preferencesFlow.collectAsStateWithLifecycle(initialValue = AppPreferencesState())
    val currentUser by Firebase.auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    val strings = if (prefs.language == AppLanguage.BENGALI) BengaliStrings else EnglishStrings

    StudentsAttendanceTheme(
        darkTheme = prefs.darkTheme,
        useBengaliFont = prefs.language == AppLanguage.BENGALI,
    ) {
        CompositionLocalProvider(LocalAppStrings provides strings) {
            val user = currentUser
            if (user == null) {
                UnauthenticatedFlow(
                    language = prefs.language,
                    onSelectLanguage = { lang -> scope.launch { prefsRepo.setLanguage(lang) } },
                    onSignedIn = { /* Reactive authStateFlow automatically transitions */ },
                )
            } else {
                // Key composition by UID so switching accounts tears down and recreates listeners cleanly
                key(user.uid) {
                    AuthenticatedRoot(
                        uid = user.uid,
                        email = user.email.orEmpty(),
                        displayName = user.displayName.orEmpty(),
                        emailVerified = user.isEmailVerified,
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthenticatedRoot(
    uid: String,
    email: String,
    displayName: String,
    emailVerified: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val sessionVm: SessionViewModel = viewModel(
        key = "session_$uid",
        factory = SessionViewModel.factory(uid),
    )

    val prefs by sessionVm.preferences.collectAsStateWithLifecycle()
    val profileState by sessionVm.userProfileState.collectAsStateWithLifecycle()
    val schoolState by sessionVm.assignedSchoolState.collectAsStateWithLifecycle()
    val pendingInvites by sessionVm.pendingInvites.collectAsStateWithLifecycle()
    val globalAnnouncements by sessionVm.globalAnnouncements.collectAsStateWithLifecycle()
    val feedback by sessionVm.feedbackMessage.collectAsStateWithLifecycle()
    val busy by sessionVm.busy.collectAsStateWithLifecycle()

    val onSignOut: () -> Unit = {
        performSignOut(credentialManager = credentialManager, scope = scope)
    }

    val isBootstrapSuper = SuperAdminConfig.isPermanentSuperAdmin(uid, email, emailVerified)

    when (val pState = profileState) {
        UiState.Loading -> {
            Box(Modifier.fillMaxSize()) {
                LoadingStateView("Loading your secure workspace...")
            }
        }
        is UiState.Error -> {
            if (isBootstrapSuper) {
                SuperAdminShell(
                    profile = UserProfile(
                        uid = uid,
                        displayName = displayName.ifBlank { SuperAdminConfig.PERMANENT_NAME },
                        email = email.ifBlank { SuperAdminConfig.PERMANENT_EMAIL },
                        role = UserRole.SUPER_ADMIN.name,
                        active = true,
                    ),
                    sessionVm = sessionVm,
                    prefs = prefs,
                    globalAnnouncements = globalAnnouncements,
                    onSignOut = onSignOut,
                )
            } else {
                OnboardingAndSchoolRegistrationScreen(
                    userEmail = email,
                    userName = displayName,
                    pendingInvites = pendingInvites,
                    busy = busy,
                    feedbackMessage = feedback ?: pState.message,
                    onAcceptInvite = sessionVm::acceptTeacherInvite,
                    onRegisterSchool = { sName, sCode, hm, em, ph, addr, vil, dist, st, pin ->
                        sessionVm.registerSchool(sName, sCode, hm, em, ph, addr, vil, dist, st, pin) {}
                    },
                    onSignOut = onSignOut,
                )
            }
        }
        is UiState.Success -> {
            val profile = pState.data
            if (profile == null) {
                if (isBootstrapSuper) {
                    Box(Modifier.fillMaxSize()) {
                        LoadingStateView("Provisioning permanent Super Admin account...")
                    }
                } else {
                    OnboardingAndSchoolRegistrationScreen(
                        userEmail = email,
                        userName = displayName,
                        pendingInvites = pendingInvites,
                        busy = busy,
                        feedbackMessage = feedback,
                        onAcceptInvite = sessionVm::acceptTeacherInvite,
                        onRegisterSchool = { sName, sCode, hm, em, ph, addr, vil, dist, st, pin ->
                            sessionVm.registerSchool(sName, sCode, hm, em, ph, addr, vil, dist, st, pin) {}
                        },
                        onSignOut = onSignOut,
                    )
                }
            } else {
                when (profile.roleEnum) {
                    UserRole.SUPER_ADMIN -> {
                        SuperAdminShell(
                            profile = profile,
                            sessionVm = sessionVm,
                            prefs = prefs,
                            globalAnnouncements = globalAnnouncements,
                            onSignOut = onSignOut,
                        )
                    }
                    UserRole.PENDING_ADMIN -> {
                        val school = (schoolState as? UiState.Success)?.data
                        SchoolApprovalGateScreen(school = school, onSignOut = onSignOut)
                    }
                    UserRole.SCHOOL_ADMIN, UserRole.TEACHER -> {
                        when (val sState = schoolState) {
                            UiState.Loading -> LoadingStateView("Loading school tenant...")
                            is UiState.Error -> SchoolApprovalGateScreen(school = null, onSignOut = onSignOut)
                            is UiState.Success -> {
                                val school = sState.data
                                if (school == null || school.statusEnum != SchoolStatus.APPROVED || !profile.active) {
                                    SchoolApprovalGateScreen(school = school, onSignOut = onSignOut)
                                } else {
                                    key(school.id) {
                                        SchoolPanelShell(
                                            schoolId = school.id,
                                            profile = profile,
                                            sessionVm = sessionVm,
                                            prefs = prefs,
                                            globalAnnouncements = globalAnnouncements,
                                            onSignOut = onSignOut,
                                            onExitInspection = null,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class NavTab(val label: String, val icon: ImageVector, val tag: String)

@Composable
private fun AdaptiveNavScaffold(
    tabs: List<NavTab>,
    selectedIndex: Int,
    onSelectTab: (Int) -> Unit,
    snackbarHostState: SnackbarHostState,
    showChrome: Boolean = true,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp
        if (useRail && showChrome) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                contentWindowInsets = WindowInsets.safeDrawing,
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        tabs.forEachIndexed { idx, tab ->
                            NavigationRailItem(
                                selected = selectedIndex == idx,
                                onClick = { onSelectTab(idx) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label, maxLines = 1) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = SaffronDark,
                                    selectedTextColor = SaffronDark,
                                    indicatorColor = SaffronLight,
                                ),
                                modifier = Modifier.testTag(tab.tag),
                            )
                        }
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        content()
                    }
                }
            }
        } else {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    if (showChrome) {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            tabs.forEachIndexed { idx, tab ->
                                NavigationBarItem(
                                    selected = selectedIndex == idx,
                                    onClick = { onSelectTab(idx) },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label, maxLines = 1) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = SaffronDark,
                                        selectedTextColor = SaffronDark,
                                        indicatorColor = SaffronLight,
                                    ),
                                    modifier = Modifier.testTag(tab.tag),
                                )
                            }
                        }
                    }
                },
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun SuperAdminShell(
    profile: UserProfile,
    sessionVm: SessionViewModel,
    prefs: AppPreferencesState,
    globalAnnouncements: List<com.example.data.model.GlobalAnnouncement>,
    onSignOut: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val superVm: SuperAdminViewModel = viewModel(
        key = "super_${profile.uid}",
        factory = SuperAdminViewModel.factory(),
    )
    val schoolsState by superVm.schoolsState.collectAsStateWithLifecycle()
    val usersState by superVm.usersState.collectAsStateWithLifecycle()
    val logsState by superVm.systemLogsState.collectAsStateWithLifecycle()
    val message by superVm.message.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message!!)
            superVm.clearMessage()
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedSchoolId by remember { mutableStateOf<String?>(null) }
    var inspectingSchoolId by remember { mutableStateOf<String?>(null) }
    var showGlobalNoticeDialog by remember { mutableStateOf(false) }

    val schools = (schoolsState as? UiState.Success)?.data.orEmpty()

    if (inspectingSchoolId != null) {
        key("inspect_$inspectingSchoolId") {
            SchoolPanelShell(
                schoolId = inspectingSchoolId!!,
                profile = profile,
                sessionVm = sessionVm,
                prefs = prefs,
                globalAnnouncements = globalAnnouncements,
                onSignOut = onSignOut,
                onExitInspection = { inspectingSchoolId = null },
            )
        }
        return
    }

    val tabs = listOf(
        NavTab(strings.dashboard, Icons.Default.Dashboard, "nav_super_dashboard"),
        NavTab(strings.schools, Icons.Default.School, "nav_super_schools"),
        NavTab(strings.users, Icons.Default.ManageAccounts, "nav_super_users"),
        NavTab(strings.settings, Icons.Default.Settings, "nav_super_settings"),
    )

    val activeDetailSchool = selectedSchoolId?.let { id -> schools.firstOrNull { it.id == id } }

    AdaptiveNavScaffold(
        tabs = tabs,
        selectedIndex = selectedTab,
        onSelectTab = { selectedTab = it; selectedSchoolId = null },
        snackbarHostState = snackbarHostState,
        showChrome = activeDetailSchool == null,
    ) {
        if (activeDetailSchool != null) {
            SchoolDetailAndApprovalScreen(
                school = activeDetailSchool,
                onBack = { selectedSchoolId = null },
                onUpdateStatus = superVm::updateSchoolStatus,
                onSaveDetails = superVm::editSchoolInfo,
                onInspectSchoolPanel = { school ->
                    selectedSchoolId = null
                    inspectingSchoolId = school.id
                },
            )
        } else {
            when (selectedTab) {
                0 -> SuperAdminDashboardScreen(
                    schoolsState = schoolsState,
                    usersState = usersState,
                    systemLogsState = logsState,
                    globalAnnouncements = globalAnnouncements,
                    language = prefs.language,
                    onSelectLanguage = sessionVm::setLanguage,
                    onOpenSchoolDetail = { selectedSchoolId = it.id },
                    onInspectSchoolPanel = { inspectingSchoolId = it.id },
                    onApproveSchool = { superVm.updateSchoolStatus(it, SchoolStatus.APPROVED) },
                    onOpenPublishGlobalNotice = { showGlobalNoticeDialog = true },
                    onGoToSchoolsTab = { selectedTab = 1 },
                )
                1 -> SchoolManagementListScreen(
                    schoolsState = schoolsState,
                    onSelectSchool = { selectedSchoolId = it.id },
                    onInspectSchool = { inspectingSchoolId = it.id },
                )
                2 -> SuperAdminUserManagementScreen(
                    usersState = usersState,
                    onUpdateUser = superVm::updateUserAccess,
                )
                3 -> ProfileAndSettingsScreen(
                    profile = profile,
                    school = null,
                    preferences = prefs,
                    offlinePendingCount = 0,
                    onSelectLanguage = sessionVm::setLanguage,
                    onToggleVibrate = sessionVm::setVibrateOnScan,
                    onToggleAutoSync = sessionVm::setAutoSyncOffline,
                    onToggleDarkTheme = sessionVm::setDarkTheme,
                    onUpdateProfile = { n, p -> sessionVm.updateProfile(n, p) },
                    onSyncOffline = {},
                    onSignOut = onSignOut,
                )
            }
        }
    }

    if (showGlobalNoticeDialog) {
        ComposeAnnouncementDialog(
            dialogTitle = "Publish Global Platform Announcement",
            onDismiss = { showGlobalNoticeDialog = false },
            onPublish = { title, body, category ->
                superVm.publishGlobalNotice(title, body, category)
                showGlobalNoticeDialog = false
            },
        )
    }
}

private sealed interface SchoolSubRoute {
    data object None : SchoolSubRoute
    data class AddEditStudent(val student: Student?) : SchoolSubRoute
    data class StudentProfile(val studentId: String) : SchoolSubRoute
    data class StudentQrCard(val studentId: String) : SchoolSubRoute
    data object Teachers : SchoolSubRoute
    data object Classes : SchoolSubRoute
    data object Announcements : SchoolSubRoute
    data class ScannerModal(val classId: String?) : SchoolSubRoute
}

@Composable
private fun SchoolPanelShell(
    schoolId: String,
    profile: UserProfile,
    sessionVm: SessionViewModel,
    prefs: AppPreferencesState,
    globalAnnouncements: List<com.example.data.model.GlobalAnnouncement>,
    onSignOut: () -> Unit,
    onExitInspection: (() -> Unit)?,
) {
    if (onExitInspection != null) BackHandler(onBack = onExitInspection)

    val strings = LocalAppStrings.current
    val schoolVm: SchoolViewModel = viewModel(
        key = "school_${schoolId}_${profile.uid}",
        factory = SchoolViewModel.factory(schoolId, profile.roleEnum),
    )

    val schoolState by schoolVm.schoolState.collectAsStateWithLifecycle()
    val studentsState by schoolVm.studentsState.collectAsStateWithLifecycle()
    val teachersState by schoolVm.teachersState.collectAsStateWithLifecycle()
    val classesState by schoolVm.classesState.collectAsStateWithLifecycle()
    val attendanceState by schoolVm.attendanceState.collectAsStateWithLifecycle()
    val announcementsState by schoolVm.announcementsState.collectAsStateWithLifecycle()
    val auditLogsState by schoolVm.auditLogsState.collectAsStateWithLifecycle()
    val offlineQueue by schoolVm.offlineQueue.collectAsStateWithLifecycle()
    val scanOutcome by schoolVm.scanOutcome.collectAsStateWithLifecycle()
    val vmMessage by schoolVm.message.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(vmMessage) {
        if (!vmMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(vmMessage!!)
            schoolVm.clearMessage()
        }
    }

    val school = (schoolState as? UiState.Success)?.data
    val students = (studentsState as? UiState.Success)?.data.orEmpty()
    val teachers = (teachersState as? UiState.Success)?.data.orEmpty()
    val classes = (classesState as? UiState.Success)?.data.orEmpty()
    val attendance = (attendanceState as? UiState.Success)?.data.orEmpty()
    val announcements = (announcementsState as? UiState.Success)?.data.orEmpty()
    val auditLogs = (auditLogsState as? UiState.Success)?.data.orEmpty()
    val todayDate = schoolVm.todayDate()
    val todayAttendance = remember(attendance, todayDate) {
        attendance.filter { it.attendanceDate == todayDate }
    }

    val isAdmin = profile.roleEnum == UserRole.SCHOOL_ADMIN || profile.roleEnum == UserRole.SUPER_ADMIN
    var selectedTab by remember { mutableIntStateOf(0) }
    var subRoute by remember { mutableStateOf<SchoolSubRoute>(SchoolSubRoute.None) }

    val tabs = if (isAdmin) {
        listOf(
            NavTab(strings.dashboard, Icons.Default.Dashboard, "nav_school_dashboard"),
            NavTab(strings.students, Icons.Default.Groups, "nav_school_students"),
            NavTab(strings.scanner, Icons.Default.QrCodeScanner, "nav_school_scanner"),
            NavTab(strings.reports, Icons.Default.Assessment, "nav_school_reports"),
            NavTab(strings.settings, Icons.Default.Settings, "nav_school_settings"),
        )
    } else {
        listOf(
            NavTab(strings.dashboard, Icons.Default.Dashboard, "nav_teacher_dashboard"),
            NavTab(strings.scanner, Icons.Default.QrCodeScanner, "nav_teacher_scanner"),
            NavTab(strings.reports, Icons.Default.Assessment, "nav_teacher_reports"),
            NavTab(strings.notices, Icons.Default.Campaign, "nav_teacher_notices"),
            NavTab(strings.settings, Icons.Default.Settings, "nav_teacher_settings"),
        )
    }

    AdaptiveNavScaffold(
        tabs = tabs,
        selectedIndex = selectedTab,
        onSelectTab = { selectedTab = it; subRoute = SchoolSubRoute.None },
        snackbarHostState = snackbarHostState,
        showChrome = subRoute is SchoolSubRoute.None,
    ) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
            if (onExitInspection != null && subRoute is SchoolSubRoute.None) {
                SaffronSubScreenBar(
                    title = "Super Admin Monitor",
                    subtitle = school?.schoolName ?: schoolId,
                    onBack = onExitInspection,
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when (val route = subRoute) {
                    is SchoolSubRoute.AddEditStudent -> {
                        AddEditStudentScreen(
                            existingStudent = route.student,
                            classes = classes,
                            onBack = { subRoute = SchoolSubRoute.None },
                            onSave = { name, father, mother, dob, gender, cid, cname, sec, roll, addr, phone, photo, adm, st ->
                                school?.let { sch ->
                                    schoolVm.saveStudent(
                                        school = sch, existing = route.student,
                                        studentName = name, fatherName = father, motherName = mother,
                                        dateOfBirth = dob, gender = gender, classId = cid, className = cname,
                                        section = sec, rollNumber = roll, address = addr, parentContact = phone,
                                        photoUrl = photo, admissionDate = adm, status = st,
                                    ) { subRoute = SchoolSubRoute.None }
                                }
                            },
                        )
                    }
                    is SchoolSubRoute.StudentProfile -> {
                        val stu = students.firstOrNull { it.id == route.studentId }
                        if (stu == null) {
                            subRoute = SchoolSubRoute.None
                        } else {
                            StudentProfileScreen(
                                student = stu,
                                attendanceRecords = attendance,
                                canEdit = isAdmin,
                                onBack = { subRoute = SchoolSubRoute.None },
                                onEdit = { subRoute = SchoolSubRoute.AddEditStudent(stu) },
                                onViewQrCard = { subRoute = SchoolSubRoute.StudentQrCard(stu.id) },
                                onToggleActive = { school?.let { schoolVm.toggleOrDeleteStudent(it, stu, false) } },
                                onDelete = {
                                    school?.let {
                                        schoolVm.toggleOrDeleteStudent(it, stu, true)
                                        subRoute = SchoolSubRoute.None
                                    }
                                },
                            )
                        }
                    }
                    is SchoolSubRoute.StudentQrCard -> {
                        val stu = students.firstOrNull { it.id == route.studentId }
                        if (stu == null) {
                            subRoute = SchoolSubRoute.None
                        } else {
                            StudentQrCardScreen(
                                school = school,
                                student = stu,
                                canManageQr = isAdmin,
                                onBack = { subRoute = SchoolSubRoute.StudentProfile(stu.id) },
                                onRegenerateQr = { school?.let { schoolVm.regenerateQr(it, stu) } },
                                onRevokeQr = { school?.let { schoolVm.revokeQr(it, stu) } },
                            )
                        }
                    }
                    SchoolSubRoute.Teachers -> {
                        TeacherManagementScreen(
                            teachers = teachers,
                            classes = classes,
                            attendance = attendance,
                            canEdit = isAdmin,
                            onBack = { subRoute = SchoolSubRoute.None },
                            onSaveTeacher = { existing, name, email, phone, classIds, secs, subs, active ->
                                school?.let { sch ->
                                    schoolVm.saveTeacher(
                                        school = sch, existing = existing,
                                        teacherName = name, email = email, phone = phone, photoUrl = "",
                                        assignedClassIds = classIds, assignedSections = secs,
                                        assignedSubjects = subs, active = active,
                                    )
                                }
                            },
                        )
                    }
                    SchoolSubRoute.Classes -> {
                        ClassAndSectionManagementScreen(
                            classes = classes,
                            teachers = teachers,
                            students = students,
                            todayAttendance = todayAttendance,
                            canEdit = isAdmin,
                            onBack = { subRoute = SchoolSubRoute.None },
                            onSaveClass = { existingId, name, secs, tId, tName ->
                                school?.let { sch ->
                                    schoolVm.saveClass(sch, existingId, name, secs, tId, tName)
                                }
                            },
                            onDeleteClass = { cid -> school?.let { schoolVm.deleteClass(it, cid) } },
                        )
                    }
                    SchoolSubRoute.Announcements -> {
                        AnnouncementsAndNotificationsScreen(
                            school = school,
                            announcements = announcements,
                            globalAnnouncements = globalAnnouncements,
                            auditLogs = auditLogs,
                            canPublishSchoolNotice = isAdmin,
                            canToggleParentAlerts = isAdmin,
                            onBack = { subRoute = SchoolSubRoute.None },
                            onPublishNotice = { title, body, cat ->
                                school?.let { sch -> schoolVm.publishAnnouncement(sch, profile, title, body, cat) }
                            },
                            onToggleParentNotifications = schoolVm::toggleParentNotifications,
                        )
                    }
                    is SchoolSubRoute.ScannerModal -> {
                        QrScannerAndManualAttendanceScreen(
                            school = school,
                            actor = profile,
                            classes = classes,
                            students = students,
                            todayAttendance = todayAttendance,
                            initialClassId = route.classId,
                            scanOutcome = scanOutcome,
                            vibrateOnScan = prefs.vibrateOnScan,
                            onBack = {
                                schoolVm.resetScanOutcome()
                                subRoute = SchoolSubRoute.None
                            },
                            onQrDetected = { raw, cid, online ->
                                school?.let { sch ->
                                    schoolVm.handleQrCodeScanned(raw, sch, profile, cid, online)
                                }
                            },
                            onResetOutcome = schoolVm::resetScanOutcome,
                            onManualMark = { stu, st ->
                                school?.let { sch -> schoolVm.submitManualAttendance(sch, profile, stu, st) }
                            },
                        )
                    }
                    SchoolSubRoute.None -> {
                        if (isAdmin) {
                            when (selectedTab) {
                                0 -> SchoolAdminAndTeacherDashboardScreen(
                                    school = school,
                                    profile = profile,
                                    students = students,
                                    teachers = teachers,
                                    classes = classes,
                                    attendance = attendance,
                                    announcements = announcements,
                                    globalAnnouncements = globalAnnouncements,
                                    offlinePending = offlineQueue,
                                    todayDate = todayDate,
                                    language = prefs.language,
                                    onSelectLanguage = sessionVm::setLanguage,
                                    onLaunchScanner = { cid -> subRoute = SchoolSubRoute.ScannerModal(cid) },
                                    onSyncOffline = schoolVm::syncOfflineQueue,
                                    onOpenStudents = { selectedTab = 1 },
                                    onOpenTeachers = { subRoute = SchoolSubRoute.Teachers },
                                    onOpenClasses = { subRoute = SchoolSubRoute.Classes },
                                    onOpenReports = { selectedTab = 3 },
                                    onOpenAnnouncements = { subRoute = SchoolSubRoute.Announcements },
                                    onOpenQrBatch = { selectedTab = 1 },
                                )
                                1 -> StudentManagementScreen(
                                    school = school,
                                    students = students,
                                    classes = classes,
                                    canEdit = isAdmin,
                                    onAddStudent = { subRoute = SchoolSubRoute.AddEditStudent(null) },
                                    onOpenProfile = { subRoute = SchoolSubRoute.StudentProfile(it.id) },
                                    onOpenQrCard = { subRoute = SchoolSubRoute.StudentQrCard(it.id) },
                                    onImportCsv = { cls, csv -> school?.let { schoolVm.importStudentsCsv(it, cls, csv) } },
                                )
                                2 -> QrScannerAndManualAttendanceScreen(
                                    school = school,
                                    actor = profile,
                                    classes = classes,
                                    students = students,
                                    todayAttendance = todayAttendance,
                                    initialClassId = null,
                                    scanOutcome = scanOutcome,
                                    vibrateOnScan = prefs.vibrateOnScan,
                                    onBack = null,
                                    onQrDetected = { raw, cid, online ->
                                        school?.let { sch -> schoolVm.handleQrCodeScanned(raw, sch, profile, cid, online) }
                                    },
                                    onResetOutcome = schoolVm::resetScanOutcome,
                                    onManualMark = { stu, st ->
                                        school?.let { sch -> schoolVm.submitManualAttendance(sch, profile, stu, st) }
                                    },
                                )
                                3 -> AttendanceReportsAndHistoryScreen(
                                    school = school,
                                    attendance = attendance,
                                    classes = classes,
                                    todayDate = todayDate,
                                    canCorrectAttendance = true,
                                    onCorrectAttendance = { rec, st, note ->
                                        school?.let { sch -> schoolVm.correctAttendance(sch, profile, rec, st, note) }
                                    },
                                )
                                4 -> ProfileAndSettingsScreen(
                                    profile = profile,
                                    school = school,
                                    preferences = prefs,
                                    offlinePendingCount = offlineQueue.size,
                                    onSelectLanguage = sessionVm::setLanguage,
                                    onToggleVibrate = sessionVm::setVibrateOnScan,
                                    onToggleAutoSync = sessionVm::setAutoSyncOffline,
                                    onToggleDarkTheme = sessionVm::setDarkTheme,
                                    onUpdateProfile = { n, p -> sessionVm.updateProfile(n, p) },
                                    onSyncOffline = schoolVm::syncOfflineQueue,
                                    onSignOut = onSignOut,
                                )
                            }
                        } else {
                            // Teacher Navigation Tabs
                            when (selectedTab) {
                                0 -> SchoolAdminAndTeacherDashboardScreen(
                                    school = school,
                                    profile = profile,
                                    students = students,
                                    teachers = teachers,
                                    classes = classes,
                                    attendance = attendance,
                                    announcements = announcements,
                                    globalAnnouncements = globalAnnouncements,
                                    offlinePending = offlineQueue,
                                    todayDate = todayDate,
                                    language = prefs.language,
                                    onSelectLanguage = sessionVm::setLanguage,
                                    onLaunchScanner = { cid -> subRoute = SchoolSubRoute.ScannerModal(cid) },
                                    onSyncOffline = schoolVm::syncOfflineQueue,
                                    onOpenStudents = {},
                                    onOpenTeachers = {},
                                    onOpenClasses = {},
                                    onOpenReports = { selectedTab = 2 },
                                    onOpenAnnouncements = { selectedTab = 3 },
                                    onOpenQrBatch = {},
                                )
                                1 -> QrScannerAndManualAttendanceScreen(
                                    school = school,
                                    actor = profile,
                                    classes = classes,
                                    students = students,
                                    todayAttendance = todayAttendance,
                                    initialClassId = profile.assignedClassIds.firstOrNull(),
                                    scanOutcome = scanOutcome,
                                    vibrateOnScan = prefs.vibrateOnScan,
                                    onBack = null,
                                    onQrDetected = { raw, cid, online ->
                                        school?.let { sch -> schoolVm.handleQrCodeScanned(raw, sch, profile, cid, online) }
                                    },
                                    onResetOutcome = schoolVm::resetScanOutcome,
                                    onManualMark = { stu, st ->
                                        school?.let { sch -> schoolVm.submitManualAttendance(sch, profile, stu, st) }
                                    },
                                )
                                2 -> AttendanceReportsAndHistoryScreen(
                                    school = school,
                                    attendance = attendance.filter { it.classId in profile.assignedClassIds },
                                    classes = classes.filter { it.id in profile.assignedClassIds },
                                    todayDate = todayDate,
                                    canCorrectAttendance = true,
                                    onCorrectAttendance = { rec, st, note ->
                                        school?.let { sch -> schoolVm.correctAttendance(sch, profile, rec, st, note) }
                                    },
                                )
                                3 -> AnnouncementsAndNotificationsScreen(
                                    school = school,
                                    announcements = announcements,
                                    globalAnnouncements = globalAnnouncements,
                                    auditLogs = emptyList(),
                                    canPublishSchoolNotice = false,
                                    canToggleParentAlerts = false,
                                    onBack = null,
                                    onPublishNotice = { _, _, _ -> },
                                    onToggleParentNotifications = {},
                                )
                                4 -> ProfileAndSettingsScreen(
                                    profile = profile,
                                    school = school,
                                    preferences = prefs,
                                    offlinePendingCount = offlineQueue.size,
                                    onSelectLanguage = sessionVm::setLanguage,
                                    onToggleVibrate = sessionVm::setVibrateOnScan,
                                    onToggleAutoSync = sessionVm::setAutoSyncOffline,
                                    onToggleDarkTheme = sessionVm::setDarkTheme,
                                    onUpdateProfile = { n, p -> sessionVm.updateProfile(n, p) },
                                    onSyncOffline = schoolVm::syncOfflineQueue,
                                    onSignOut = onSignOut,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
