package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

object SuperAdminConfig {
    const val PERMANENT_UID = "Q6vhlqwqAlMvDy4WFhOq9xnjnit1"
    const val PERMANENT_EMAIL = "anantabarman.mtb736172@gmail.com"
    const val PERMANENT_NAME = "Ananta Barman"

    fun isPermanentSuperAdmin(uid: String?, email: String?, emailVerified: Boolean): Boolean {
        if (uid == PERMANENT_UID) return true
        return emailVerified && email?.equals(PERMANENT_EMAIL, ignoreCase = true) == true
    }
}

enum class UserRole {
    SUPER_ADMIN,
    SCHOOL_ADMIN,
    TEACHER,
    PENDING_ADMIN;

    companion object {
        fun fromString(raw: String?): UserRole =
            entries.firstOrNull { it.name == raw } ?: PENDING_ADMIN
    }
}

enum class SchoolStatus {
    PENDING,
    APPROVED,
    REJECTED,
    SUSPENDED;

    companion object {
        fun fromString(raw: String?): SchoolStatus =
            entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

enum class StudentStatus {
    ACTIVE,
    INACTIVE;

    companion object {
        fun fromString(raw: String?): StudentStatus =
            entries.firstOrNull { it.name == raw } ?: ACTIVE
    }
}

enum class AttendanceStatus {
    PRESENT,
    ABSENT,
    LATE,
    EXCUSED;

    companion object {
        fun fromString(raw: String?): AttendanceStatus =
            entries.firstOrNull { it.name == raw } ?: PRESENT
    }
}

enum class AttendanceMethod {
    QR,
    MANUAL,
    OFFLINE_SYNC;

    companion object {
        fun fromString(raw: String?): AttendanceMethod =
            entries.firstOrNull { it.name == raw } ?: QR
    }
}

enum class AnnouncementCategory {
    GENERAL,
    HOLIDAY,
    EXAM,
    URGENT;

    companion object {
        fun fromString(raw: String?): AnnouncementCategory =
            entries.firstOrNull { it.name == raw } ?: GENERAL
    }
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val phone: String = "",
    val role: String = UserRole.PENDING_ADMIN.name,
    val schoolId: String = "",
    val schoolName: String = "",
    val adminUid: String = "",
    val inviteId: String = "",
    val assignedClassIds: List<String> = emptyList(),
    val assignedSections: List<String> = emptyList(),
    val assignedSubjects: List<String> = emptyList(),
    val photoUrl: String = "",
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val roleEnum: UserRole get() = UserRole.fromString(role)
}

@Keep
@IgnoreExtraProperties
data class School(
    @DocumentId val id: String = "",
    val schoolName: String = "",
    val schoolCode: String = "",
    val headmasterName: String = "",
    val email: String = "",
    val phone: String = "",
    val address: String = "",
    val villageOrTown: String = "",
    val district: String = "",
    val state: String = "",
    val pinCode: String = "",
    val adminUid: String = "",
    val memberUids: List<String> = emptyList(),
    val status: String = SchoolStatus.PENDING.name,
    val logoUrl: String = "",
    val rejectionReason: String = "",
    val lastInviteId: String = "",
    val parentNotificationsEnabled: Boolean = true,
    val studentCount: Long = 0L,
    val teacherCount: Long = 0L,
    val classCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val statusEnum: SchoolStatus get() = SchoolStatus.fromString(status)
}

@Keep
@IgnoreExtraProperties
data class Student(
    @DocumentId val id: String = "",
    val studentIdCode: String = "",
    val studentName: String = "",
    val fatherName: String = "",
    val motherName: String = "",
    val dateOfBirth: String = "",
    val gender: String = "",
    val classId: String = "",
    val className: String = "",
    val section: String = "",
    val rollNumber: String = "",
    val address: String = "",
    val parentContact: String = "",
    val photoUrl: String = "",
    val admissionDate: String = "",
    val status: String = StudentStatus.ACTIVE.name,
    val qrTokenId: String = "",
    val qrVersion: Long = 1L,
    val schoolId: String = "",
    val adminUid: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val statusEnum: StudentStatus get() = StudentStatus.fromString(status)
}

@Keep
@IgnoreExtraProperties
data class Teacher(
    @DocumentId val id: String = "",
    val teacherIdCode: String = "",
    val teacherName: String = "",
    val email: String = "",
    val phone: String = "",
    val photoUrl: String = "",
    val userUid: String = "",
    val assignedClassIds: List<String> = emptyList(),
    val assignedSubjects: List<String> = emptyList(),
    val assignedSections: List<String> = emptyList(),
    val joiningDate: String = "",
    val active: Boolean = true,
    val schoolId: String = "",
    val adminUid: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class SchoolClass(
    @DocumentId val id: String = "",
    val className: String = "",
    val sections: List<String> = emptyList(),
    val classTeacherId: String = "",
    val classTeacherName: String = "",
    val active: Boolean = true,
    val schoolId: String = "",
    val adminUid: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class AttendanceRecord(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val studentId: String = "",
    val studentIdCode: String = "",
    val studentName: String = "",
    val studentPhotoUrl: String = "",
    val classId: String = "",
    val className: String = "",
    val section: String = "",
    val rollNumber: String = "",
    val attendanceDate: String = "",
    val attendanceTime: String = "",
    val status: String = AttendanceStatus.PRESENT.name,
    val method: String = AttendanceMethod.QR.name,
    val recordedByUid: String = "",
    val recordedByName: String = "",
    val correctionNote: String = "",
    val adminUid: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val statusEnum: AttendanceStatus get() = AttendanceStatus.fromString(status)
    val methodEnum: AttendanceMethod get() = AttendanceMethod.fromString(method)
}

@Keep
@IgnoreExtraProperties
data class Announcement(
    @DocumentId val id: String = "",
    val title: String = "",
    val body: String = "",
    val category: String = AnnouncementCategory.GENERAL.name,
    val authorUid: String = "",
    val authorName: String = "",
    val schoolId: String = "",
    val adminUid: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val categoryEnum: AnnouncementCategory get() = AnnouncementCategory.fromString(category)
}

@Keep
@IgnoreExtraProperties
data class GlobalAnnouncement(
    @DocumentId val id: String = "",
    val title: String = "",
    val body: String = "",
    val category: String = AnnouncementCategory.GENERAL.name,
    val authorUid: String = "",
    val authorName: String = "",
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val categoryEnum: AnnouncementCategory get() = AnnouncementCategory.fromString(category)
}

@Keep
@IgnoreExtraProperties
data class QrToken(
    @DocumentId val id: String = "",
    val studentId: String = "",
    val schoolId: String = "",
    val tokenHash: String = "",
    val version: Long = 1L,
    val revoked: Boolean = false,
    val adminUid: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class AuditLog(
    @DocumentId val id: String = "",
    val action: String = "",
    val actorUid: String = "",
    val actorName: String = "",
    val targetType: String = "",
    val targetId: String = "",
    val details: String = "",
    val schoolId: String = "",
    val adminUid: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class SystemLog(
    @DocumentId val id: String = "",
    val action: String = "",
    val actorUid: String = "",
    val actorName: String = "",
    val targetType: String = "",
    val targetId: String = "",
    val details: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class TeacherInvite(
    @DocumentId val id: String = "",
    val teacherEmail: String = "",
    val teacherName: String = "",
    val schoolId: String = "",
    val schoolName: String = "",
    val teacherDocId: String = "",
    val adminUid: String = "",
    val assignedClassIds: List<String> = emptyList(),
    val assignedSections: List<String> = emptyList(),
    val assignedSubjects: List<String> = emptyList(),
    val status: String = "PENDING",
    val acceptedUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

sealed interface QrScanOutcome {
    data object Idle : QrScanOutcome
    data object Processing : QrScanOutcome
    data class Recorded(
        val student: Student,
        val status: AttendanceStatus,
        val time: String,
        val offlineQueued: Boolean = false,
        val parentNotificationQueued: Boolean = false,
    ) : QrScanOutcome
    data class AlreadyPresent(
        val student: Student,
        val existingTime: String,
        val status: AttendanceStatus,
    ) : QrScanOutcome
    data class Rejected(val reason: String) : QrScanOutcome
}
