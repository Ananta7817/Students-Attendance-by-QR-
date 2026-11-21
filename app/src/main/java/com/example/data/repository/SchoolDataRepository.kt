package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.local.OfflineAttendanceDatabase
import com.example.data.local.PendingAttendanceEntity
import com.example.data.model.Announcement
import com.example.data.model.AnnouncementCategory
import com.example.data.model.AttendanceMethod
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceStatus
import com.example.data.model.AuditLog
import com.example.data.model.QrScanOutcome
import com.example.data.model.QrToken
import com.example.data.model.School
import com.example.data.model.SchoolClass
import com.example.data.model.Student
import com.example.data.model.StudentStatus
import com.example.data.model.Teacher
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.example.util.QrSecurityManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class SchoolDataRepository(
    private val db: FirebaseFirestore,
    private val offlineDb: OfflineAttendanceDatabase,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        OfflineAttendanceDatabase.getInstance(context.applicationContext),
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun generateSafeId(prefix: String): String {
        val raw = UUID.randomUUID().toString().replace("-", "").take(10).uppercase()
        return "${prefix}_${raw}"
    }

    fun todayDateString(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    fun currentTimeString(): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

    private fun scopedCollectionQuery(
        schoolId: String,
        subcollection: String,
        role: UserRole,
        uid: String,
    ): Query {
        val col = db.collection("schools").document(schoolId).collection(subcollection)
        return when (role) {
            UserRole.SUPER_ADMIN -> col
            UserRole.SCHOOL_ADMIN -> col.whereEqualTo("adminUid", uid)
            else -> col.whereArrayContains("memberUids", uid)
        }
    }

    private fun <T> observeScoped(
        schoolId: String,
        subcollection: String,
        role: UserRole,
        clazz: Class<T>,
    ): Flow<List<T>> = flow {
        val uid = requireUserId()
        val path = "schools/$schoolId/$subcollection"
        emitAll(
            scopedCollectionQuery(schoolId, subcollection, role, uid)
                .snapshots()
                .map { it.toObjects(clazz, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeStudents(schoolId: String, role: UserRole): Flow<List<Student>> =
        observeScoped(schoolId, "students", role, Student::class.java)
            .map { list -> list.sortedWith(compareBy({ it.className }, { it.section }, { it.rollNumber.padStart(5, '0') })) }

    fun observeTeachers(schoolId: String, role: UserRole): Flow<List<Teacher>> =
        observeScoped(schoolId, "teachers", role, Teacher::class.java)
            .map { list -> list.sortedBy { it.teacherName } }

    fun observeClasses(schoolId: String, role: UserRole): Flow<List<SchoolClass>> =
        observeScoped(schoolId, "classes", role, SchoolClass::class.java)
            .map { list -> list.sortedBy { it.className } }

    fun observeAttendance(schoolId: String, role: UserRole): Flow<List<AttendanceRecord>> =
        observeScoped(schoolId, "attendance", role, AttendanceRecord::class.java)
            .map { list -> list.sortedByDescending { "${it.attendanceDate} ${it.attendanceTime}" } }

    fun observeAnnouncements(schoolId: String, role: UserRole): Flow<List<Announcement>> =
        observeScoped(schoolId, "announcements", role, Announcement::class.java)
            .map { list -> list.sortedByDescending { it.createdAt?.seconds ?: 0L } }

    fun observeAuditLogs(schoolId: String, role: UserRole): Flow<List<AuditLog>> =
        observeScoped(schoolId, "auditLogs", role, AuditLog::class.java)
            .map { list -> list.sortedByDescending { it.createdAt?.seconds ?: 0L } }

    fun observeOfflinePendingQueue(schoolId: String): Flow<List<PendingAttendanceEntity>> =
        offlineDb.pendingAttendanceDao().observePendingForSchool(schoolId)

    // -------------------------------------------------------------------------
    // Classes & Sections CRUD
    // -------------------------------------------------------------------------
    suspend fun saveClass(
        school: School,
        existingId: String? = null,
        className: String,
        sections: List<String>,
        classTeacherId: String = "",
        classTeacherName: String = "",
        active: Boolean = true,
    ): Result<String> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val classId = existingId?.takeIf { it.isNotBlank() } ?: generateSafeId("CLS")
        val path = "schools/${school.id}/classes/$classId"
        return try {
            require(className.trim().isNotBlank()) { "Class name is required." }
            val cleanSections = sections.map { it.trim().uppercase() }.filter { it.isNotBlank() }.distinct().ifEmpty { listOf("A") }
            val ref = db.collection("schools").document(school.id).collection("classes").document(classId)
            if (existingId.isNullOrBlank()) {
                val payload = buildMap<String, Any> {
                    put("className", className.trim().take(60))
                    put("sections", cleanSections.take(30))
                    if (classTeacherId.isNotBlank()) put("classTeacherId", classTeacherId.take(128))
                    if (classTeacherName.isNotBlank()) put("classTeacherName", classTeacherName.take(120))
                    put("active", active)
                    put("schoolId", school.id)
                    put("adminUid", school.adminUid)
                    put("memberUids", school.memberUids.ifEmpty { listOf(school.adminUid) })
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                ref.set(payload).await()
                db.collection("schools").document(school.id).update(
                    mapOf("classCount" to FieldValue.increment(1), "updatedAt" to FieldValue.serverTimestamp())
                ).await()
                writeAuditLog(school, uid, "CLASS_CREATED", "CLASS", classId, "Created class $className (${cleanSections.joinToString()})")
            } else {
                val updates = buildMap<String, Any> {
                    put("className", className.trim().take(60))
                    put("sections", cleanSections.take(30))
                    if (classTeacherId.isNotBlank()) put("classTeacherId", classTeacherId.take(128))
                    if (classTeacherName.isNotBlank()) put("classTeacherName", classTeacherName.take(120))
                    put("active", active)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                ref.update(updates).await()
                writeAuditLog(school, uid, "CLASS_UPDATED", "CLASS", classId, "Updated class $className")
            }
            Result.success(classId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteClass(school: School, classId: String): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val path = "schools/${school.id}/classes/$classId"
        return try {
            db.collection("schools").document(school.id).collection("classes").document(classId).delete().await()
            if (school.classCount > 0L) {
                db.collection("schools").document(school.id).update(
                    mapOf("classCount" to FieldValue.increment(-1), "updatedAt" to FieldValue.serverTimestamp())
                ).await()
            }
            writeAuditLog(school, uid, "CLASS_DELETED", "CLASS", classId, "Deleted class $classId")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // Students CRUD, CSV Bulk Import & QR Tokens
    // -------------------------------------------------------------------------
    suspend fun saveStudent(
        school: School,
        existingStudent: Student? = null,
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
    ): Result<String> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val studentId = existingStudent?.id?.takeIf { it.isNotBlank() } ?: generateSafeId("STU")
        val path = "schools/${school.id}/students/$studentId"
        return try {
            require(studentName.trim().isNotBlank()) { "Student name is required." }
            require(classId.isNotBlank() && className.isNotBlank()) { "Please select a class." }
            require(rollNumber.trim().isNotBlank()) { "Roll number is required." }

            val members = school.memberUids.ifEmpty { listOf(school.adminUid) }
            val ref = db.collection("schools").document(school.id).collection("students").document(studentId)

            if (existingStudent == null) {
                val qrTokenId = generateSafeId("QRT")
                val studentCode = "STU-${school.schoolCode.takeLast(4).uppercase()}-${(1000..9999).random()}"
                val payload = buildMap<String, Any> {
                    put("studentIdCode", studentCode.take(60))
                    put("studentName", studentName.trim().take(120))
                    put("fatherName", fatherName.trim().ifBlank { "N/A" }.take(120))
                    put("motherName", motherName.trim().ifBlank { "N/A" }.take(120))
                    put("dateOfBirth", dateOfBirth.trim().ifBlank { "2012-01-01" }.take(30))
                    if (gender.isNotBlank()) put("gender", gender.trim().take(30))
                    put("classId", classId)
                    put("className", className.trim().take(60))
                    put("section", section.trim().ifBlank { "A" }.uppercase().take(30))
                    put("rollNumber", rollNumber.trim().take(30))
                    put("address", address.trim().ifBlank { school.district }.take(300))
                    put("parentContact", parentContact.trim().ifBlank { "N/A" }.take(30))
                    if (photoUrl.isNotBlank()) put("photoUrl", photoUrl.trim().take(500))
                    put("admissionDate", admissionDate.trim().ifBlank { todayDateString() }.take(30))
                    put("status", status.name)
                    put("qrTokenId", qrTokenId)
                    put("qrVersion", 1L)
                    put("schoolId", school.id)
                    put("adminUid", school.adminUid)
                    put("memberUids", members)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                ref.set(payload).await()
                createQrTokenDocument(school, studentId, qrTokenId, 1L, members)
                db.collection("schools").document(school.id).update(
                    mapOf("studentCount" to FieldValue.increment(1), "updatedAt" to FieldValue.serverTimestamp())
                ).await()
                writeAuditLog(school, uid, "STUDENT_CREATED", "STUDENT", studentId, "Added student $studentName ($studentCode)")
            } else {
                val updates = buildMap<String, Any> {
                    put("studentName", studentName.trim().take(120))
                    put("fatherName", fatherName.trim().ifBlank { "N/A" }.take(120))
                    put("motherName", motherName.trim().ifBlank { "N/A" }.take(120))
                    put("dateOfBirth", dateOfBirth.trim().ifBlank { "2012-01-01" }.take(30))
                    if (gender.isNotBlank()) put("gender", gender.trim().take(30))
                    put("classId", classId)
                    put("className", className.trim().take(60))
                    put("section", section.trim().ifBlank { "A" }.uppercase().take(30))
                    put("rollNumber", rollNumber.trim().take(30))
                    put("address", address.trim().ifBlank { school.district }.take(300))
                    put("parentContact", parentContact.trim().ifBlank { "N/A" }.take(30))
                    if (photoUrl.isNotBlank()) put("photoUrl", photoUrl.trim().take(500))
                    put("admissionDate", admissionDate.trim().ifBlank { todayDateString() }.take(30))
                    put("status", status.name)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                ref.update(updates).await()
                writeAuditLog(school, uid, "STUDENT_UPDATED", "STUDENT", studentId, "Updated student $studentName")
            }
            Result.success(studentId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteOrDeactivateStudent(school: School, student: Student, hardDelete: Boolean): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val path = "schools/${school.id}/students/${student.id}"
        return try {
            if (hardDelete) {
                db.collection("schools").document(school.id).collection("students").document(student.id).delete().await()
                if (school.studentCount > 0L) {
                    db.collection("schools").document(school.id).update(
                        mapOf("studentCount" to FieldValue.increment(-1), "updatedAt" to FieldValue.serverTimestamp())
                    ).await()
                }
                writeAuditLog(school, uid, "STUDENT_DELETED", "STUDENT", student.id, "Deleted ${student.studentName}")
            } else {
                val nextStatus = if (student.statusEnum == StudentStatus.ACTIVE) StudentStatus.INACTIVE else StudentStatus.ACTIVE
                db.collection("schools").document(school.id).collection("students").document(student.id).update(
                    mapOf("status" to nextStatus.name, "updatedAt" to FieldValue.serverTimestamp())
                ).await()
                writeAuditLog(school, uid, "STUDENT_STATUS", "STUDENT", student.id, "Set ${student.studentName} to ${nextStatus.name}")
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    /**
     * Imports CSV lines: studentName,fatherName,motherName,dob,section,rollNumber,parentContact,address
     */
    suspend fun importStudentsFromCsv(
        school: School,
        targetClass: SchoolClass,
        csvText: String,
    ): Result<Int> {
        var imported = 0
        return try {
            val lines = csvText.lines().map { it.trim() }.filter { it.isNotBlank() }
            val startIndex = if (lines.firstOrNull()?.lowercase()?.contains("name") == true) 1 else 0
            for (i in startIndex until lines.size) {
                val cols = lines[i].split(",").map { it.trim().trim('"') }
                if (cols.isEmpty() || cols[0].isBlank()) continue
                val res = saveStudent(
                    school = school,
                    studentName = cols.getOrElse(0) { "" },
                    fatherName = cols.getOrElse(1) { "N/A" },
                    motherName = cols.getOrElse(2) { "N/A" },
                    dateOfBirth = cols.getOrElse(3) { "2012-01-01" },
                    gender = "",
                    classId = targetClass.id,
                    className = targetClass.className,
                    section = cols.getOrElse(4) { targetClass.sections.firstOrNull() ?: "A" },
                    rollNumber = cols.getOrElse(5) { (i + 1).toString() },
                    parentContact = cols.getOrElse(6) { "N/A" },
                    address = cols.getOrElse(7) { school.district },
                    photoUrl = "",
                    admissionDate = todayDateString(),
                )
                if (res.isSuccess) imported++
            }
            Result.success(imported)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun createQrTokenDocument(
        school: School,
        studentId: String,
        tokenId: String,
        version: Long,
        members: List<String>,
    ) {
        val rawPayload = QrSecurityManager.buildOpaqueQrPayload(school.id, tokenId, version)
        val tokenHash = QrSecurityManager.sha256Hex(rawPayload, 64)
        db.collection("schools").document(school.id)
            .collection("qrTokens").document(tokenId)
            .set(
                mapOf(
                    "studentId" to studentId,
                    "schoolId" to school.id,
                    "tokenHash" to tokenHash,
                    "version" to version,
                    "revoked" to false,
                    "adminUid" to school.adminUid,
                    "memberUids" to members,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
    }

    suspend fun regenerateStudentQrToken(school: School, student: Student): Result<String> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val newTokenId = generateSafeId("QRT")
        val nextVersion = student.qrVersion + 1L
        val members = school.memberUids.ifEmpty { listOf(school.adminUid) }
        return try {
            // Revoke old token if exists
            if (student.qrTokenId.isNotBlank()) {
                val oldRef = db.collection("schools").document(school.id).collection("qrTokens").document(student.qrTokenId)
                if (oldRef.get().await().exists()) {
                    oldRef.update(mapOf("revoked" to true, "updatedAt" to FieldValue.serverTimestamp())).await()
                }
            }
            // Create new active token
            createQrTokenDocument(school, student.id, newTokenId, nextVersion, members)
            // Update student reference
            db.collection("schools").document(school.id).collection("students").document(student.id).update(
                mapOf(
                    "qrTokenId" to newTokenId,
                    "qrVersion" to nextVersion,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            writeAuditLog(school, uid, "QR_REGENERATED", "STUDENT", student.id, "Regenerated QR v$nextVersion for ${student.studentName}")
            Result.success(newTokenId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "schools/${school.id}/qrTokens/$newTokenId")
            Result.failure(e)
        }
    }

    suspend fun revokeStudentQrToken(school: School, student: Student): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val path = "schools/${school.id}/qrTokens/${student.qrTokenId}"
        return try {
            db.collection("schools").document(school.id).collection("qrTokens").document(student.qrTokenId)
                .update(mapOf("revoked" to true, "updatedAt" to FieldValue.serverTimestamp())).await()
            writeAuditLog(school, uid, "QR_REVOKED", "STUDENT", student.id, "Revoked QR for ${student.studentName}")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // Teachers CRUD & Verified Teacher Invites
    // -------------------------------------------------------------------------
    suspend fun saveTeacher(
        school: School,
        existingTeacher: Teacher? = null,
        teacherName: String,
        email: String,
        phone: String,
        photoUrl: String,
        assignedClassIds: List<String>,
        assignedSections: List<String>,
        assignedSubjects: List<String>,
        active: Boolean = true,
    ): Result<String> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val teacherId = existingTeacher?.id?.takeIf { it.isNotBlank() } ?: generateSafeId("TCH")
        val normalizedEmail = email.trim().lowercase()
        val path = "schools/${school.id}/teachers/$teacherId"
        return try {
            require(teacherName.trim().isNotBlank() && normalizedEmail.contains("@")) {
                "Teacher name and valid Google email are required."
            }
            val members = school.memberUids.ifEmpty { listOf(school.adminUid) }
            val ref = db.collection("schools").document(school.id).collection("teachers").document(teacherId)
            val inviteId = "${school.id}_${QrSecurityManager.sha256Hex(normalizedEmail, 16)}"

            if (existingTeacher == null) {
                val teacherCode = "TCH-${(1000..9999).random()}"
                val payload = buildMap<String, Any> {
                    put("teacherIdCode", teacherCode)
                    put("teacherName", teacherName.trim().take(120))
                    put("email", normalizedEmail.take(150))
                    put("phone", phone.trim().ifBlank { "N/A" }.take(30))
                    if (photoUrl.isNotBlank()) put("photoUrl", photoUrl.trim().take(500))
                    put("assignedClassIds", assignedClassIds.distinct().take(50))
                    put("assignedSubjects", assignedSubjects.filter { it.isNotBlank() }.distinct().take(50))
                    put("assignedSections", assignedSections.filter { it.isNotBlank() }.distinct().take(50))
                    put("joiningDate", todayDateString())
                    put("active", active)
                    put("schoolId", school.id)
                    put("adminUid", school.adminUid)
                    put("memberUids", members)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                ref.set(payload).await()

                // Create verified invite so teacher can sign in with Google and immediately join
                db.collection("teacherInvites").document(inviteId).set(
                    mapOf(
                        "teacherEmail" to normalizedEmail.take(150),
                        "teacherName" to teacherName.trim().take(120),
                        "schoolId" to school.id,
                        "schoolName" to school.schoolName,
                        "teacherDocId" to teacherId,
                        "adminUid" to school.adminUid,
                        "assignedClassIds" to assignedClassIds.distinct().take(50),
                        "assignedSections" to assignedSections.filter { it.isNotBlank() }.distinct().take(50),
                        "assignedSubjects" to assignedSubjects.filter { it.isNotBlank() }.distinct().take(50),
                        "status" to "PENDING",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()

                db.collection("schools").document(school.id).update(
                    mapOf("teacherCount" to FieldValue.increment(1), "updatedAt" to FieldValue.serverTimestamp())
                ).await()
                writeAuditLog(school, uid, "TEACHER_CREATED", "TEACHER", teacherId, "Added teacher $teacherName ($normalizedEmail)")
            } else {
                val updates = buildMap<String, Any> {
                    put("teacherName", teacherName.trim().take(120))
                    put("phone", phone.trim().ifBlank { "N/A" }.take(30))
                    if (photoUrl.isNotBlank()) put("photoUrl", photoUrl.trim().take(500))
                    put("assignedClassIds", assignedClassIds.distinct().take(50))
                    put("assignedSubjects", assignedSubjects.filter { it.isNotBlank() }.distinct().take(50))
                    put("assignedSections", assignedSections.filter { it.isNotBlank() }.distinct().take(50))
                    put("active", active)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                ref.update(updates).await()

                // Sync linked user profile permissions if teacher has already accepted invite
                if (existingTeacher.userUid.isNotBlank()) {
                    db.collection("users").document(existingTeacher.userUid).update(
                        mapOf(
                            "displayName" to teacherName.trim().take(120),
                            "phone" to phone.trim().ifBlank { "N/A" }.take(30),
                            "assignedClassIds" to assignedClassIds.distinct().take(50),
                            "assignedSections" to assignedSections.filter { it.isNotBlank() }.distinct().take(50),
                            "assignedSubjects" to assignedSubjects.filter { it.isNotBlank() }.distinct().take(50),
                            "active" to active,
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    ).await()
                }
                writeAuditLog(school, uid, "TEACHER_UPDATED", "TEACHER", teacherId, "Updated teacher $teacherName (active=$active)")
            }
            Result.success(teacherId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // QR Attendance Validation, Submission, Duplicate Prevention & Offline Sync
    // -------------------------------------------------------------------------
    suspend fun processScannedQrAttendance(
        rawQrContent: String,
        school: School,
        actor: UserProfile,
        selectedClassId: String?,
        cachedStudents: List<Student>,
        todayAttendance: List<AttendanceRecord>,
        isOnline: Boolean,
    ): QrScanOutcome {
        val uid = try { requireUserId() } catch (_: Exception) {
            return QrScanOutcome.Rejected("Authentication required to record attendance.")
        }

        // 1. Validate cryptographic HMAC signature
        val parsed = QrSecurityManager.parseAndVerifyPayload(rawQrContent)
            ?: return QrScanOutcome.Rejected("Invalid or tampered QR code format.")

        // 2. Verify School ID matches active school (strict tenant isolation)
        if (parsed.schoolId != school.id) {
            return QrScanOutcome.Rejected("Security Alert: QR code belongs to another school (${parsed.schoolId}).")
        }

        val today = todayDateString()
        val nowTime = currentTimeString()

        // 3. Resolve student & verify token revocation
        var student: Student? = cachedStudents.firstOrNull { it.qrTokenId == parsed.tokenId }
        if (isOnline) {
            try {
                val tokenSnap = db.collection("schools").document(school.id)
                    .collection("qrTokens").document(parsed.tokenId)
                    .get().await()
                if (!tokenSnap.exists()) {
                    return QrScanOutcome.Rejected("QR token not registered in this school.")
                }
                val token = tokenSnap.toObject(QrToken::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (token == null || token.revoked) {
                    return QrScanOutcome.Rejected("QR code has been revoked or expired. Please print a new ID card.")
                }
                if (token.version != parsed.version.toLong()) {
                    return QrScanOutcome.Rejected("Outdated QR card version. Please use the regenerated QR card.")
                }
                if (student == null) {
                    val stuSnap = db.collection("schools").document(school.id)
                        .collection("students").document(token.studentId)
                        .get().await()
                    student = stuSnap.toObject(Student::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.GET, "schools/${school.id}/qrTokens/${parsed.tokenId}")
                if (student == null) {
                    return QrScanOutcome.Rejected("Unable to verify QR token offline. Student not in local cache.")
                }
            }
        }

        val targetStudent = student
            ?: return QrScanOutcome.Rejected("Student record not found for this QR token.")

        if (targetStudent.statusEnum != StudentStatus.ACTIVE) {
            return QrScanOutcome.Rejected("Student ${targetStudent.studentName} is currently INACTIVE.")
        }

        // 4. Verify teacher class authorization
        if (actor.roleEnum == UserRole.TEACHER) {
            if (targetStudent.classId !in actor.assignedClassIds) {
                return QrScanOutcome.Rejected("Access Denied: You are not assigned to ${targetStudent.className}.")
            }
        }
        if (!selectedClassId.isNullOrBlank() && targetStudent.classId != selectedClassId) {
            return QrScanOutcome.Rejected("Wrong Class: ${targetStudent.studentName} belongs to ${targetStudent.className}-${targetStudent.section}.")
        }

        val deterministicId = "${targetStudent.id}_$today"

        // 5. Duplicate attendance check (in-memory + Room + Firestore)
        val existingLocal = todayAttendance.firstOrNull {
            it.studentId == targetStudent.id && it.attendanceDate == today
        }
        if (existingLocal != null) {
            return QrScanOutcome.AlreadyPresent(
                student = targetStudent,
                existingTime = existingLocal.attendanceTime,
                status = existingLocal.statusEnum,
            )
        }
        val existingOffline = offlineDb.pendingAttendanceDao().getById(deterministicId)
        if (existingOffline != null) {
            return QrScanOutcome.AlreadyPresent(
                student = targetStudent,
                existingTime = existingOffline.attendanceTime,
                status = AttendanceStatus.fromString(existingOffline.status),
            )
        }

        val members = school.memberUids.ifEmpty { listOf(school.adminUid, uid).distinct() }

        if (!isOnline) {
            offlineDb.pendingAttendanceDao().insertIfAbsent(
                PendingAttendanceEntity(
                    deterministicId = deterministicId,
                    schoolId = school.id,
                    studentId = targetStudent.id,
                    studentIdCode = targetStudent.studentIdCode,
                    studentName = targetStudent.studentName,
                    studentPhotoUrl = targetStudent.photoUrl,
                    classId = targetStudent.classId,
                    className = targetStudent.className,
                    section = targetStudent.section,
                    rollNumber = targetStudent.rollNumber,
                    attendanceDate = today,
                    attendanceTime = nowTime,
                    status = AttendanceStatus.PRESENT.name,
                    recordedByUid = uid,
                    recordedByName = actor.displayName.ifBlank { "Teacher" },
                    adminUid = school.adminUid,
                    memberUidsCsv = members.joinToString(","),
                )
            )
            return QrScanOutcome.Recorded(
                student = targetStudent,
                status = AttendanceStatus.PRESENT,
                time = nowTime,
                offlineQueued = true,
                parentNotificationQueued = school.parentNotificationsEnabled,
            )
        }

        // 6. Online Firestore atomic duplicate check & write
        val docRef = db.collection("schools").document(school.id)
            .collection("attendance").document(deterministicId)
        return try {
            val remoteExisting = docRef.get().await()
            if (remoteExisting.exists()) {
                val existingRecord = remoteExisting.toObject(AttendanceRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                return QrScanOutcome.AlreadyPresent(
                    student = targetStudent,
                    existingTime = existingRecord?.attendanceTime ?: nowTime,
                    status = existingRecord?.statusEnum ?: AttendanceStatus.PRESENT,
                )
            }
            val payload = buildAttendanceMap(
                school = school,
                student = targetStudent,
                date = today,
                time = nowTime,
                status = AttendanceStatus.PRESENT,
                method = AttendanceMethod.QR,
                recordedByUid = uid,
                recordedByName = actor.displayName.ifBlank { "Teacher" },
                members = members,
            )
            docRef.set(payload).await()
            QrScanOutcome.Recorded(
                student = targetStudent,
                status = AttendanceStatus.PRESENT,
                time = nowTime,
                offlineQueued = false,
                parentNotificationQueued = school.parentNotificationsEnabled,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            // Fallback to Room offline queue on network drop
            offlineDb.pendingAttendanceDao().insertIfAbsent(
                PendingAttendanceEntity(
                    deterministicId = deterministicId,
                    schoolId = school.id,
                    studentId = targetStudent.id,
                    studentIdCode = targetStudent.studentIdCode,
                    studentName = targetStudent.studentName,
                    studentPhotoUrl = targetStudent.photoUrl,
                    classId = targetStudent.classId,
                    className = targetStudent.className,
                    section = targetStudent.section,
                    rollNumber = targetStudent.rollNumber,
                    attendanceDate = today,
                    attendanceTime = nowTime,
                    status = AttendanceStatus.PRESENT.name,
                    recordedByUid = uid,
                    recordedByName = actor.displayName.ifBlank { "Teacher" },
                    adminUid = school.adminUid,
                    memberUidsCsv = members.joinToString(","),
                )
            )
            QrScanOutcome.Recorded(
                student = targetStudent,
                status = AttendanceStatus.PRESENT,
                time = nowTime,
                offlineQueued = true,
                parentNotificationQueued = school.parentNotificationsEnabled,
            )
        }
    }

    suspend fun recordManualAttendance(
        school: School,
        actor: UserProfile,
        student: Student,
        status: AttendanceStatus,
        date: String = todayDateString(),
    ): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val deterministicId = "${student.id}_$date"
        val docRef = db.collection("schools").document(school.id).collection("attendance").document(deterministicId)
        return try {
            if (actor.roleEnum == UserRole.TEACHER && student.classId !in actor.assignedClassIds) {
                return Result.failure(IllegalAccessException("You are not assigned to ${student.className}."))
            }
            val nowTime = currentTimeString()
            val members = school.memberUids.ifEmpty { listOf(school.adminUid, uid).distinct() }
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "status" to status.name,
                        "attendanceTime" to nowTime,
                        "method" to AttendanceMethod.MANUAL.name,
                        "recordedByUid" to uid,
                        "recordedByName" to actor.displayName.ifBlank { "Staff" },
                        "correctionNote" to "Manual status update to ${status.name}",
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
                writeAuditLog(school, uid, "ATTENDANCE_CORRECTED", "ATTENDANCE", deterministicId, "Manual update ${student.studentName} -> ${status.name}")
            } else {
                val payload = buildAttendanceMap(
                    school = school,
                    student = student,
                    date = date,
                    time = nowTime,
                    status = status,
                    method = AttendanceMethod.MANUAL,
                    recordedByUid = uid,
                    recordedByName = actor.displayName.ifBlank { "Staff" },
                    members = members,
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun correctAttendanceWithAudit(
        school: School,
        actor: UserProfile,
        record: AttendanceRecord,
        newStatus: AttendanceStatus,
        correctionNote: String,
    ): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val docRef = db.collection("schools").document(school.id).collection("attendance").document(record.id)
        return try {
            require(correctionNote.trim().length >= 3) { "Please enter a brief audit reason (min 3 characters)." }
            docRef.update(
                mapOf(
                    "status" to newStatus.name,
                    "recordedByUid" to uid,
                    "recordedByName" to actor.displayName.ifBlank { "Staff" },
                    "correctionNote" to correctionNote.trim().take(300),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            writeAuditLog(
                school = school,
                actorUid = uid,
                action = "ATTENDANCE_CORRECTED",
                targetType = "ATTENDANCE",
                targetId = record.id,
                details = "${record.studentName} (${record.attendanceDate}): ${record.status} -> ${newStatus.name}. Reason: ${correctionNote.trim()}"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun syncPendingOfflineAttendance(schoolId: String): Result<Int> {
        try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val pending = offlineDb.pendingAttendanceDao().getPendingForSchoolOnce(schoolId)
        var syncedCount = 0
        for (item in pending) {
            val docRef = db.collection("schools").document(item.schoolId)
                .collection("attendance").document(item.deterministicId)
            try {
                val existing = docRef.get().await()
                if (!existing.exists()) {
                    val payload = buildMap<String, Any> {
                        put("schoolId", item.schoolId)
                        put("studentId", item.studentId)
                        put("studentIdCode", item.studentIdCode)
                        put("studentName", item.studentName)
                        if (item.studentPhotoUrl.isNotBlank()) put("studentPhotoUrl", item.studentPhotoUrl)
                        put("classId", item.classId)
                        put("className", item.className)
                        put("section", item.section)
                        put("rollNumber", item.rollNumber)
                        put("attendanceDate", item.attendanceDate)
                        put("attendanceTime", item.attendanceTime)
                        put("status", item.status)
                        put("method", AttendanceMethod.OFFLINE_SYNC.name)
                        put("recordedByUid", item.recordedByUid)
                        put("recordedByName", item.recordedByName)
                        put("adminUid", item.adminUid)
                        put("memberUids", item.memberUidsCsv.split(",").filter { it.isNotBlank() })
                        put("createdAt", FieldValue.serverTimestamp())
                        put("updatedAt", FieldValue.serverTimestamp())
                    }
                    docRef.set(payload).await()
                }
                offlineDb.pendingAttendanceDao().deleteById(item.deterministicId)
                syncedCount++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, docRef.path)
            }
        }
        return Result.success(syncedCount)
    }

    // -------------------------------------------------------------------------
    // School Announcements & Audit Logs
    // -------------------------------------------------------------------------
    suspend fun publishSchoolAnnouncement(
        school: School,
        actor: UserProfile,
        title: String,
        body: String,
        category: AnnouncementCategory,
    ): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val docId = generateSafeId("ANN")
        val path = "schools/${school.id}/announcements/$docId"
        return try {
            require(title.trim().isNotBlank() && body.trim().isNotBlank()) { "Title and message body are required." }
            val members = school.memberUids.ifEmpty { listOf(school.adminUid) }
            db.collection("schools").document(school.id).collection("announcements").document(docId).set(
                mapOf(
                    "title" to title.trim().take(150),
                    "body" to body.trim().take(2000),
                    "category" to category.name,
                    "authorUid" to uid,
                    "authorName" to actor.displayName.ifBlank { school.headmasterName },
                    "schoolId" to school.id,
                    "adminUid" to school.adminUid,
                    "memberUids" to members,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            writeAuditLog(school, uid, "ANNOUNCEMENT_POSTED", "ANNOUNCEMENT", docId, "Posted ${category.name}: ${title.trim()}")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    private fun buildAttendanceMap(
        school: School,
        student: Student,
        date: String,
        time: String,
        status: AttendanceStatus,
        method: AttendanceMethod,
        recordedByUid: String,
        recordedByName: String,
        members: List<String>,
    ): Map<String, Any> = buildMap {
        put("schoolId", school.id)
        put("studentId", student.id)
        put("studentIdCode", student.studentIdCode)
        put("studentName", student.studentName)
        if (student.photoUrl.isNotBlank()) put("studentPhotoUrl", student.photoUrl.take(500))
        put("classId", student.classId)
        put("className", student.className)
        put("section", student.section)
        put("rollNumber", student.rollNumber)
        put("attendanceDate", date)
        put("attendanceTime", time)
        put("status", status.name)
        put("method", method.name)
        put("recordedByUid", recordedByUid)
        put("recordedByName", recordedByName.take(120))
        put("adminUid", school.adminUid)
        put("memberUids", members)
        put("createdAt", FieldValue.serverTimestamp())
        put("updatedAt", FieldValue.serverTimestamp())
    }

    private suspend fun writeAuditLog(
        school: School,
        actorUid: String,
        action: String,
        targetType: String,
        targetId: String,
        details: String,
    ) {
        val logId = generateSafeId("ALOG")
        val members = school.memberUids.ifEmpty { listOf(school.adminUid) }
        try {
            db.collection("schools").document(school.id).collection("auditLogs").document(logId).set(
                mapOf(
                    "action" to action.take(80),
                    "actorUid" to actorUid,
                    "actorName" to (auth.currentUser?.displayName ?: "Staff").take(120),
                    "targetType" to targetType.take(60),
                    "targetId" to targetId.take(128),
                    "details" to details.take(500),
                    "schoolId" to school.id,
                    "adminUid" to school.adminUid,
                    "memberUids" to members,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "schools/${school.id}/auditLogs/$logId")
        }
    }
}
