package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.local.AppPreferencesState
import com.example.data.local.OfflineAttendanceDatabase
import com.example.data.local.PendingAttendanceEntity
import com.example.data.local.UserPreferencesRepository
import com.example.data.model.Announcement
import com.example.data.model.AnnouncementCategory
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceStatus
import com.example.data.model.AuditLog
import com.example.data.model.GlobalAnnouncement
import com.example.data.model.QrScanOutcome
import com.example.data.model.School
import com.example.data.model.SchoolClass
import com.example.data.model.SchoolStatus
import com.example.data.model.Student
import com.example.data.model.StudentStatus
import com.example.data.model.SystemLog
import com.example.data.model.Teacher
import com.example.data.model.TeacherInvite
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.repository.PlatformRepository
import com.example.data.repository.SchoolDataRepository
import com.example.util.AppLanguage
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val VM_TAG = "AttendanceVM"
private const val STOP_TIMEOUT_MS = 5000L

private fun <T> Flow<T>.toSafeUiState(
    scope: kotlinx.coroutines.CoroutineScope,
    tag: String,
    fallbackMessage: String,
): StateFlow<UiState<T>> =
    map<T, UiState<T>> { UiState.Success(it) }
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            val root = fsErr ?: error
            Log.w(tag, fallbackMessage, root)
            emit(UiState.Error(root.message ?: fallbackMessage))
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

/**
 * Manages authenticated user session, Super Admin auto-bootstrap, school registration,
 * teacher invite acceptance, and bilingual app preferences.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModel(
    private val platformRepo: PlatformRepository,
    private val prefsRepo: UserPreferencesRepository,
    val currentUserId: String,
) : ViewModel() {

    val preferences: StateFlow<AppPreferencesState> = prefsRepo.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppPreferencesState())

    val userProfileState: StateFlow<UiState<UserProfile?>> = platformRepo.observeUserProfile(currentUserId)
        .toSafeUiState(viewModelScope, VM_TAG, "Failed to load user profile")

    val pendingInvites: StateFlow<List<TeacherInvite>> = platformRepo.observePendingInvitesForCurrentUser()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val globalAnnouncements: StateFlow<List<GlobalAnnouncement>> = platformRepo.observeGlobalAnnouncements()
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val assignedSchoolState: StateFlow<UiState<School?>> = userProfileState
        .flatMapLatest { state ->
            val schoolId = (state as? UiState.Success)?.data?.schoolId.orEmpty()
            if (schoolId.isBlank()) flowOf(null) else platformRepo.observeSchool(schoolId)
        }
        .toSafeUiState(viewModelScope, VM_TAG, "Failed to load school details")

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        viewModelScope.launch {
            platformRepo.ensureSuperAdminProfileIfEligible()
        }
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { prefsRepo.setLanguage(language) }
    }

    fun setVibrateOnScan(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setVibrateOnScan(enabled) }
    }

    fun setAutoSyncOffline(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setAutoSyncOffline(enabled) }
    }

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setDarkTheme(enabled) }
    }

    fun registerSchool(
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
        onSuccess: (String) -> Unit,
    ) {
        viewModelScope.launch {
            _busy.value = true
            val res = platformRepo.registerNewSchool(
                schoolName, schoolCode, headmasterName, email, phone,
                address, villageOrTown, district, state, pinCode
            )
            _busy.value = false
            res.onSuccess { id ->
                _feedbackMessage.value = "School registered ($id). Pending Super Admin approval."
                onSuccess(id)
            }.onFailure { err ->
                _feedbackMessage.value = err.message ?: "School registration failed."
            }
        }
    }

    fun acceptTeacherInvite(invite: TeacherInvite) {
        viewModelScope.launch {
            _busy.value = true
            val res = platformRepo.acceptTeacherInvite(invite)
            _busy.value = false
            res.onSuccess {
                _feedbackMessage.value = "Joined ${invite.schoolName} as Teacher!"
            }.onFailure { err ->
                _feedbackMessage.value = err.message ?: "Failed to accept invitation."
            }
        }
    }

    fun updateProfile(displayName: String, phone: String, photoUrl: String? = null) {
        viewModelScope.launch {
            _busy.value = true
            val res = platformRepo.updateOwnProfile(displayName, phone, photoUrl)
            _busy.value = false
            _feedbackMessage.value = if (res.isSuccess) "Profile updated." else (res.exceptionOrNull()?.message ?: "Update failed.")
        }
    }

    companion object {
        fun factory(currentUserId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                SessionViewModel(
                    platformRepo = PlatformRepository(db),
                    prefsRepo = UserPreferencesRepository(app),
                    currentUserId = currentUserId,
                )
            }
        }
    }
}

/**
 * Super Admin ViewModel for global platform stats, school lifecycle, user RBAC, and system logs.
 */
class SuperAdminViewModel(
    private val platformRepo: PlatformRepository,
) : ViewModel() {

    val schoolsState: StateFlow<UiState<List<School>>> = platformRepo.observeAllSchoolsForSuperAdmin()
        .toSafeUiState(viewModelScope, VM_TAG, "Failed to load registered schools")

    val usersState: StateFlow<UiState<List<UserProfile>>> = platformRepo.observeAllUsersForSuperAdmin()
        .toSafeUiState(viewModelScope, VM_TAG, "Failed to load platform users")

    val systemLogsState: StateFlow<UiState<List<SystemLog>>> = platformRepo.observeSystemLogs()
        .toSafeUiState(viewModelScope, VM_TAG, "Failed to load system logs")

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() { _message.value = null }

    fun updateSchoolStatus(school: School, status: SchoolStatus, reason: String = "") {
        viewModelScope.launch {
            val res = platformRepo.updateSchoolStatusBySuperAdmin(school, status, reason)
            _message.value = if (res.isSuccess) {
                "${school.schoolName} marked ${status.name}."
            } else {
                res.exceptionOrNull()?.message ?: "Failed to update school status."
            }
        }
    }

    fun editSchoolInfo(
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
    ) {
        viewModelScope.launch {
            val res = platformRepo.updateSchoolDetails(
                schoolId, schoolName, schoolCode, headmasterName, email, phone,
                address, villageOrTown, district, state, pinCode, parentNotificationsEnabled
            )
            _message.value = if (res.isSuccess) "School details saved." else (res.exceptionOrNull()?.message ?: "Save failed.")
        }
    }

    fun updateUserAccess(user: UserProfile, role: UserRole, active: Boolean) {
        viewModelScope.launch {
            val res = platformRepo.updateUserBySuperAdmin(user, role, active)
            _message.value = if (res.isSuccess) "Updated ${user.displayName}." else (res.exceptionOrNull()?.message ?: "Update failed.")
        }
    }

    fun publishGlobalNotice(title: String, body: String, category: AnnouncementCategory) {
        viewModelScope.launch {
            val res = platformRepo.publishGlobalAnnouncement(title, body, category)
            _message.value = if (res.isSuccess) "Global announcement published." else (res.exceptionOrNull()?.message ?: "Publish failed.")
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                SuperAdminViewModel(PlatformRepository(db))
            }
        }
    }
}

/**
 * Tenant-scoped ViewModel for School Admin, Teacher, and Super Admin school-inspection views.
 */
class SchoolViewModel(
    private val schoolRepo: SchoolDataRepository,
    private val platformRepo: PlatformRepository,
    val schoolId: String,
    val actorRole: UserRole,
) : ViewModel() {

    val schoolState: StateFlow<UiState<School?>> = platformRepo.observeSchool(schoolId)
        .toSafeUiState(viewModelScope, VM_TAG, "Failed to load school")

    val studentsState: StateFlow<UiState<List<Student>>> = schoolRepo.observeStudents(schoolId, actorRole)
        .toSafeUiState(viewModelScope, VM_TAG, "Failed to load students")

    val teachersState: StateFlow<UiState<List<Teacher>>> = schoolRepo.observeTeachers(schoolId, actorRole)
        .toSafeUiState(viewModelScope, VM_TAG, "Failed to load teachers")

    val classesState: StateFlow<UiState<List<SchoolClass>>> = schoolRepo.observeClasses(schoolId, actorRole)
        .toSafeUiState(viewModelScope, VM_TAG, "Failed to load classes")

    val attendanceState: StateFlow<UiState<List<AttendanceRecord>>> = schoolRepo.observeAttendance(schoolId, actorRole)
        .toSafeUiState(viewModelScope, VM_TAG, "Failed to load attendance")

    val announcementsState: StateFlow<UiState<List<Announcement>>> = schoolRepo.observeAnnouncements(schoolId, actorRole)
        .toSafeUiState(viewModelScope, VM_TAG, "Failed to load announcements")

    val auditLogsState: StateFlow<UiState<List<AuditLog>>> =
        if (actorRole == UserRole.TEACHER) {
            flowOf(emptyList<AuditLog>())
        } else {
            schoolRepo.observeAuditLogs(schoolId, actorRole)
        }.toSafeUiState(viewModelScope, VM_TAG, "Failed to load audit logs")

    val offlineQueue: StateFlow<List<PendingAttendanceEntity>> = schoolRepo.observeOfflinePendingQueue(schoolId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _scanOutcome = MutableStateFlow<QrScanOutcome>(QrScanOutcome.Idle)
    val scanOutcome: StateFlow<QrScanOutcome> = _scanOutcome.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun todayDate(): String = schoolRepo.todayDateString()
    fun clearMessage() { _message.value = null }
    fun resetScanOutcome() { _scanOutcome.value = QrScanOutcome.Idle }

    fun saveClass(
        school: School,
        existingId: String? = null,
        className: String,
        sections: List<String>,
        classTeacherId: String = "",
        classTeacherName: String = "",
        active: Boolean = true,
        onDone: () -> Unit = {},
    ) {
        viewModelScope.launch {
            val res = schoolRepo.saveClass(school, existingId, className, sections, classTeacherId, classTeacherName, active)
            _message.value = if (res.isSuccess) "Class saved." else (res.exceptionOrNull()?.message ?: "Failed to save class.")
            if (res.isSuccess) onDone()
        }
    }

    fun deleteClass(school: School, classId: String) {
        viewModelScope.launch {
            val res = schoolRepo.deleteClass(school, classId)
            _message.value = if (res.isSuccess) "Class removed." else (res.exceptionOrNull()?.message ?: "Delete failed.")
        }
    }

    fun saveStudent(
        school: School,
        existing: Student? = null,
        studentName: String,
        fatherName: String,
        motherName: String,
        dateOfBirth: String,
        gender: String,
        classId: String,
        className: String,
        section: String,
        rollNumber: String,
        address: String,
        parentContact: String,
        photoUrl: String,
        admissionDate: String,
        status: StudentStatus = StudentStatus.ACTIVE,
        onDone: () -> Unit = {},
    ) {
        viewModelScope.launch {
            val res = schoolRepo.saveStudent(
                school, existing, studentName, fatherName, motherName, dateOfBirth, gender,
                classId, className, section, rollNumber, address, parentContact, photoUrl, admissionDate, status
            )
            _message.value = if (res.isSuccess) {
                if (existing == null) "Student added with secure QR code." else "Student updated."
            } else {
                res.exceptionOrNull()?.message ?: "Failed to save student."
            }
            if (res.isSuccess) onDone()
        }
    }

    fun toggleOrDeleteStudent(school: School, student: Student, hardDelete: Boolean) {
        viewModelScope.launch {
            val res = schoolRepo.deleteOrDeactivateStudent(school, student, hardDelete)
            _message.value = if (res.isSuccess) {
                if (hardDelete) "Student removed." else "Student status updated."
            } else {
                res.exceptionOrNull()?.message ?: "Action failed."
            }
        }
    }

    fun importStudentsCsv(school: School, targetClass: SchoolClass, csvText: String) {
        viewModelScope.launch {
            val res = schoolRepo.importStudentsFromCsv(school, targetClass, csvText)
            _message.value = res.fold(
                onSuccess = { "Imported $it student(s) into ${targetClass.className}." },
                onFailure = { it.message ?: "CSV import failed." }
            )
        }
    }

    fun regenerateQr(school: School, student: Student) {
        viewModelScope.launch {
            val res = schoolRepo.regenerateStudentQrToken(school, student)
            _message.value = if (res.isSuccess) "New QR token generated (previous revoked)." else (res.exceptionOrNull()?.message ?: "QR regeneration failed.")
        }
    }

    fun revokeQr(school: School, student: Student) {
        viewModelScope.launch {
            val res = schoolRepo.revokeStudentQrToken(school, student)
            _message.value = if (res.isSuccess) "QR code revoked." else (res.exceptionOrNull()?.message ?: "Revoke failed.")
        }
    }

    fun saveTeacher(
        school: School,
        existing: Teacher? = null,
        teacherName: String,
        email: String,
        phone: String,
        photoUrl: String,
        assignedClassIds: List<String>,
        assignedSections: List<String>,
        assignedSubjects: List<String>,
        active: Boolean = true,
        onDone: () -> Unit = {},
    ) {
        viewModelScope.launch {
            val res = schoolRepo.saveTeacher(
                school, existing, teacherName, email, phone, photoUrl,
                assignedClassIds, assignedSections, assignedSubjects, active
            )
            _message.value = if (res.isSuccess) {
                if (existing == null) "Teacher added & Google invite created." else "Teacher updated."
            } else {
                res.exceptionOrNull()?.message ?: "Failed to save teacher."
            }
            if (res.isSuccess) onDone()
        }
    }

    fun handleQrCodeScanned(
        rawQrContent: String,
        school: School,
        actor: UserProfile,
        selectedClassId: String?,
        isOnline: Boolean,
    ) {
        if (_scanOutcome.value is QrScanOutcome.Processing) return
        viewModelScope.launch {
            _scanOutcome.value = QrScanOutcome.Processing
            val students = (studentsState.value as? UiState.Success)?.data.orEmpty()
            val today = schoolRepo.todayDateString()
            val todayRecs = (attendanceState.value as? UiState.Success)?.data
                ?.filter { it.attendanceDate == today }.orEmpty()
            val outcome = schoolRepo.processScannedQrAttendance(
                rawQrContent = rawQrContent,
                school = school,
                actor = actor,
                selectedClassId = selectedClassId,
                cachedStudents = students,
                todayAttendance = todayRecs,
                isOnline = isOnline,
            )
            _scanOutcome.value = outcome
        }
    }

    fun submitManualAttendance(
        school: School,
        actor: UserProfile,
        student: Student,
        status: AttendanceStatus,
    ) {
        viewModelScope.launch {
            val res = schoolRepo.recordManualAttendance(school, actor, student, status)
            _message.value = if (res.isSuccess) {
                "${student.studentName} marked ${status.name}."
            } else {
                res.exceptionOrNull()?.message ?: "Manual attendance failed."
            }
        }
    }

    fun correctAttendance(
        school: School,
        actor: UserProfile,
        record: AttendanceRecord,
        newStatus: AttendanceStatus,
        note: String,
    ) {
        viewModelScope.launch {
            val res = schoolRepo.correctAttendanceWithAudit(school, actor, record, newStatus, note)
            _message.value = if (res.isSuccess) "Attendance corrected & logged." else (res.exceptionOrNull()?.message ?: "Correction failed.")
        }
    }

    fun syncOfflineQueue() {
        viewModelScope.launch {
            val res = schoolRepo.syncPendingOfflineAttendance(schoolId)
            _message.value = res.fold(
                onSuccess = { "Synced $it offline attendance record(s)." },
                onFailure = { it.message ?: "Offline sync failed." }
            )
        }
    }

    fun publishAnnouncement(
        school: School,
        actor: UserProfile,
        title: String,
        body: String,
        category: AnnouncementCategory,
        onDone: () -> Unit = {},
    ) {
        viewModelScope.launch {
            val res = schoolRepo.publishSchoolAnnouncement(school, actor, title, body, category)
            _message.value = if (res.isSuccess) "Announcement published." else (res.exceptionOrNull()?.message ?: "Publish failed.")
            if (res.isSuccess) onDone()
        }
    }

    fun toggleParentNotifications(enabled: Boolean) {
        viewModelScope.launch {
            val res = platformRepo.toggleSchoolParentNotifications(schoolId, enabled)
            _message.value = if (res.isSuccess) {
                if (enabled) "Parent attendance alerts enabled." else "Parent attendance alerts paused."
            } else {
                res.exceptionOrNull()?.message ?: "Update failed."
            }
        }
    }

    companion object {
        fun factory(schoolId: String, actorRole: UserRole): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                val offlineDb = OfflineAttendanceDatabase.getInstance(app)
                SchoolViewModel(
                    schoolRepo = SchoolDataRepository(db, offlineDb),
                    platformRepo = PlatformRepository(db),
                    schoolId = schoolId,
                    actorRole = actorRole,
                )
            }
        }
    }
}
