package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AnnouncementCategory
import com.example.data.model.GlobalAnnouncement
import com.example.data.model.School
import com.example.data.model.SchoolStatus
import com.example.data.model.SuperAdminConfig
import com.example.data.model.SystemLog
import com.example.data.model.TeacherInvite
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class PlatformRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun generateSafeId(prefix: String): String {
        val raw = UUID.randomUUID().toString().replace("-", "").take(10).uppercase()
        return "${prefix}_${raw}"
    }

    /**
     * Ensures permanent Super Admin identity (Ananta Barman) is provisioned in systemAdmins & users.
     */
    suspend fun ensureSuperAdminProfileIfEligible(): Result<UserProfile?> {
        val user = auth.currentUser ?: return Result.success(null)
        if (!SuperAdminConfig.isPermanentSuperAdmin(user.uid, user.email, user.isEmailVerified)) {
            return Result.success(null)
        }
        val uid = user.uid
        return try {
            val adminRef = db.collection("systemAdmins").document(uid)
            val existingAdmin = adminRef.get().await()
            if (!existingAdmin.exists()) {
                adminRef.set(
                    mapOf(
                        "uid" to uid,
                        "displayName" to (user.displayName?.takeIf { it.isNotBlank() } ?: SuperAdminConfig.PERMANENT_NAME),
                        "email" to (user.email ?: SuperAdminConfig.PERMANENT_EMAIL),
                        "role" to UserRole.SUPER_ADMIN.name,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }

            val userRef = db.collection("users").document(uid)
            val existingUser = userRef.get().await()
            if (!existingUser.exists()) {
                val profilePayload = buildMap<String, Any> {
                    put("uid", uid)
                    put("displayName", user.displayName?.takeIf { it.isNotBlank() } ?: SuperAdminConfig.PERMANENT_NAME)
                    put("email", user.email ?: SuperAdminConfig.PERMANENT_EMAIL)
                    put("role", UserRole.SUPER_ADMIN.name)
                    put("active", true)
                    user.photoUrl?.toString()?.takeIf { it.isNotBlank() }?.let { put("photoUrl", it.take(500)) }
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                userRef.set(profilePayload).await()
            }
            val snap = userRef.get().await()
            Result.success(snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "systemAdmins/$uid")
            Result.failure(e)
        }
    }

    fun observeUserProfile(uid: String): Flow<UserProfile?> = flow {
        require(uid.isNotBlank()) { "UID cannot be blank" }
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { it.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observePendingInvitesForCurrentUser(): Flow<List<TeacherInvite>> = flow {
        val user = auth.currentUser ?: return@flow
        val email = user.email?.lowercase()?.trim() ?: return@flow
        if (!user.isEmailVerified) {
            emit(emptyList())
            return@flow
        }
        val path = "teacherInvites"
        emitAll(
            db.collection(path)
                .whereEqualTo("teacherEmail", email)
                .whereEqualTo("status", "PENDING")
                .snapshots()
                .map { snap -> snap.toObjects(TeacherInvite::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser != null) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    emit(emptyList())
                }
        )
    }

    suspend fun acceptTeacherInvite(invite: TeacherInvite): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not authenticated"))
        return try {
            // 1. Update teacherInvite to ACCEPTED
            db.collection("teacherInvites").document(invite.id).update(
                mapOf(
                    "status" to "ACCEPTED",
                    "acceptedUid" to uid,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()

            // 2. Create or update users/{uid} with TEACHER role referencing verified inviteId
            val userPayload = buildMap<String, Any> {
                put("uid", uid)
                put("displayName", user.displayName?.takeIf { it.isNotBlank() } ?: invite.teacherName)
                put("email", (user.email ?: invite.teacherEmail).lowercase().trim())
                put("role", UserRole.TEACHER.name)
                put("schoolId", invite.schoolId)
                put("schoolName", invite.schoolName)
                put("adminUid", invite.adminUid)
                put("inviteId", invite.id)
                put("assignedClassIds", invite.assignedClassIds)
                put("assignedSections", invite.assignedSections)
                put("assignedSubjects", invite.assignedSubjects)
                put("active", true)
                user.photoUrl?.toString()?.takeIf { it.isNotBlank() }?.let { put("photoUrl", it.take(500)) }
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            db.collection("users").document(uid).set(userPayload).await()

            // 3. Add teacher UID to school memberUids referencing invite
            db.collection("schools").document(invite.schoolId).update(
                mapOf(
                    "memberUids" to FieldValue.arrayUnion(uid),
                    "lastInviteId" to invite.id,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()

            // 4. Link teacher document in school
            db.collection("schools").document(invite.schoolId)
                .collection("teachers").document(invite.teacherDocId)
                .update(
                    mapOf(
                        "userUid" to uid,
                        "memberUids" to FieldValue.arrayUnion(uid),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "teacherInvites/${invite.id}")
            Result.failure(e)
        }
    }

    /**
     * Registers a new school in PENDING status and assigns creator as PENDING_ADMIN.
     */
    suspend fun registerNewSchool(
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
    ): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not authenticated"))
        val schoolId = generateSafeId("SCH")
        return try {
            require(schoolName.trim().length in 2..150) { "School name must be 2-150 characters." }
            require(schoolCode.trim().length in 2..50) { "School / UDISE code is required." }
            require(headmasterName.trim().length in 2..120) { "Headmaster name is required." }
            require(email.trim().contains("@")) { "Valid school email is required." }
            require(phone.trim().length in 7..30) { "Valid contact number is required." }
            require(address.trim().isNotBlank() && district.trim().isNotBlank() && state.trim().isNotBlank() && pinCode.trim().isNotBlank()) {
                "Complete school address, district, state, and PIN code are required."
            }

            val schoolPayload = mapOf(
                "schoolName" to schoolName.trim().take(150),
                "schoolCode" to schoolCode.trim().take(50),
                "headmasterName" to headmasterName.trim().take(120),
                "email" to email.trim().lowercase().take(150),
                "phone" to phone.trim().take(30),
                "address" to address.trim().take(300),
                "villageOrTown" to villageOrTown.trim().ifBlank { district.trim() }.take(100),
                "district" to district.trim().take(100),
                "state" to state.trim().take(100),
                "pinCode" to pinCode.trim().take(20),
                "adminUid" to uid,
                "memberUids" to listOf(uid),
                "status" to SchoolStatus.PENDING.name,
                "parentNotificationsEnabled" to true,
                "studentCount" to 0L,
                "teacherCount" to 0L,
                "classCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            db.collection("schools").document(schoolId).set(schoolPayload).await()

            val userRef = db.collection("users").document(uid)
            val existingUser = userRef.get().await()
            if (!existingUser.exists()) {
                val userPayload = buildMap<String, Any> {
                    put("uid", uid)
                    put("displayName", headmasterName.trim().take(120))
                    put("email", (user.email ?: email).trim().lowercase().take(150))
                    put("phone", phone.trim().take(30))
                    put("role", UserRole.PENDING_ADMIN.name)
                    put("schoolId", schoolId)
                    put("schoolName", schoolName.trim().take(150))
                    put("active", true)
                    user.photoUrl?.toString()?.takeIf { it.isNotBlank() }?.let { put("photoUrl", it.take(500)) }
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                userRef.set(userPayload).await()
            }
            Result.success(schoolId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "schools/$schoolId")
            Result.failure(e)
        }
    }

    fun observeSchool(schoolId: String): Flow<School?> = flow {
        require(schoolId.isNotBlank()) { "School ID cannot be blank" }
        val path = "schools/$schoolId"
        emitAll(
            db.collection("schools").document(schoolId)
                .snapshots()
                .map { it.toObject(School::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser != null) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeAllSchoolsForSuperAdmin(): Flow<List<School>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("schools")
                .snapshots()
                .map { snap ->
                    snap.toObjects(School::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, "schools")
                    }
                    throw error
                }
        )
    }

    fun observeAllUsersForSuperAdmin(): Flow<List<UserProfile>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("users")
                .snapshots()
                .map { snap ->
                    snap.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, "users")
                    }
                    throw error
                }
        )
    }

    fun observeSystemLogs(): Flow<List<SystemLog>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("systemLogs")
                .snapshots()
                .map { snap ->
                    snap.toObjects(SystemLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, "systemLogs")
                    }
                    throw error
                }
        )
    }

    fun observeGlobalAnnouncements(): Flow<List<GlobalAnnouncement>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("globalAnnouncements")
                .whereEqualTo("active", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(GlobalAnnouncement::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, "globalAnnouncements")
                    }
                    throw error
                }
        )
    }

    suspend fun updateSchoolStatusBySuperAdmin(
        school: School,
        newStatus: SchoolStatus,
        reason: String = "",
    ): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        return try {
            val updates = buildMap<String, Any> {
                put("status", newStatus.name)
                if (reason.isNotBlank()) put("rejectionReason", reason.trim().take(300))
                put("updatedAt", FieldValue.serverTimestamp())
            }
            db.collection("schools").document(school.id).update(updates).await()

            if (school.adminUid.isNotBlank() && school.adminUid != SuperAdminConfig.PERMANENT_UID) {
                val targetRole = if (newStatus == SchoolStatus.APPROVED) UserRole.SCHOOL_ADMIN.name else UserRole.PENDING_ADMIN.name
                val userDoc = db.collection("users").document(school.adminUid).get().await()
                if (userDoc.exists()) {
                    db.collection("users").document(school.adminUid).update(
                        mapOf(
                            "role" to targetRole,
                            "active" to (newStatus != SchoolStatus.SUSPENDED),
                            "schoolId" to school.id,
                            "schoolName" to school.schoolName,
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    ).await()
                }
            }
            recordSystemLog(
                action = "SCHOOL_${newStatus.name}",
                actorUid = uid,
                targetType = "SCHOOL",
                targetId = school.id,
                details = "School '${school.schoolName}' changed to ${newStatus.name}. ${reason.trim()}"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "schools/${school.id}")
            Result.failure(e)
        }
    }

    suspend fun updateSchoolDetails(
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
    ): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        return try {
            db.collection("schools").document(schoolId).update(
                mapOf(
                    "schoolName" to schoolName.trim().take(150),
                    "schoolCode" to schoolCode.trim().take(50),
                    "headmasterName" to headmasterName.trim().take(120),
                    "email" to email.trim().lowercase().take(150),
                    "phone" to phone.trim().take(30),
                    "address" to address.trim().take(300),
                    "villageOrTown" to villageOrTown.trim().take(100),
                    "district" to district.trim().take(100),
                    "state" to state.trim().take(100),
                    "pinCode" to pinCode.trim().take(20),
                    "parentNotificationsEnabled" to parentNotificationsEnabled,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            if (SuperAdminConfig.isPermanentSuperAdmin(uid, auth.currentUser?.email, auth.currentUser?.isEmailVerified == true)) {
                recordSystemLog("SCHOOL_EDITED", uid, "SCHOOL", schoolId, "Updated details for $schoolName")
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "schools/$schoolId")
            Result.failure(e)
        }
    }

    suspend fun toggleSchoolParentNotifications(schoolId: String, enabled: Boolean): Result<Unit> {
        try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        return try {
            db.collection("schools").document(schoolId).update(
                mapOf(
                    "parentNotificationsEnabled" to enabled,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "schools/$schoolId")
            Result.failure(e)
        }
    }

    suspend fun updateUserBySuperAdmin(
        targetUser: UserProfile,
        newRole: UserRole,
        active: Boolean,
    ): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        if (targetUser.uid == SuperAdminConfig.PERMANENT_UID) {
            return Result.failure(IllegalArgumentException("Permanent Super Admin account cannot be modified."))
        }
        return try {
            db.collection("users").document(targetUser.uid).update(
                mapOf(
                    "role" to newRole.name,
                    "active" to active,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            recordSystemLog(
                action = "USER_UPDATED",
                actorUid = uid,
                targetType = "USER",
                targetId = targetUser.uid,
                details = "Updated ${targetUser.displayName}: role=${newRole.name}, active=$active"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/${targetUser.uid}")
            Result.failure(e)
        }
    }

    suspend fun updateOwnProfile(displayName: String, phone: String, photoUrl: String?): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        return try {
            val payload = buildMap<String, Any> {
                put("displayName", displayName.trim().take(120))
                put("phone", phone.trim().take(30))
                photoUrl?.takeIf { it.isNotBlank() }?.let { put("photoUrl", it.take(500)) }
                put("updatedAt", FieldValue.serverTimestamp())
            }
            db.collection("users").document(uid).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun publishGlobalAnnouncement(
        title: String,
        body: String,
        category: AnnouncementCategory,
    ): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val docId = generateSafeId("GANN")
        return try {
            require(title.trim().isNotBlank() && body.trim().isNotBlank()) { "Title and body are required." }
            db.collection("globalAnnouncements").document(docId).set(
                mapOf(
                    "title" to title.trim().take(150),
                    "body" to body.trim().take(2000),
                    "category" to category.name,
                    "authorUid" to uid,
                    "authorName" to (auth.currentUser?.displayName ?: SuperAdminConfig.PERMANENT_NAME),
                    "active" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            recordSystemLog("GLOBAL_ANNOUNCEMENT", uid, "ANNOUNCEMENT", docId, "Published: ${title.trim()}")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "globalAnnouncements/$docId")
            Result.failure(e)
        }
    }

    private suspend fun recordSystemLog(
        action: String,
        actorUid: String,
        targetType: String,
        targetId: String,
        details: String,
    ) {
        val logId = generateSafeId("SLOG")
        try {
            db.collection("systemLogs").document(logId).set(
                mapOf(
                    "action" to action.take(80),
                    "actorUid" to actorUid,
                    "actorName" to (auth.currentUser?.displayName ?: SuperAdminConfig.PERMANENT_NAME),
                    "targetType" to targetType.take(60),
                    "targetId" to targetId.take(128),
                    "details" to details.take(500),
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "systemLogs/$logId")
        }
    }
}
