/**
 * Firebase Cloud Functions for Students Attendance by QR
 * Multi-School QR Attendance Management System
 *
 * Deploy with: firebase deploy --only functions
 */
const { onDocumentCreated, onDocumentUpdated } = require("firebase-functions/v2/firestore");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const admin = require("firebase-admin");

admin.initializeApp();

const DATABASE_ID = "ai-studio-android-students-4c50967f-0a62-4305-825d-675450fd8d51";
const PERMANENT_SUPER_ADMIN_UID = "Q6vhlqwqAlMvDy4WFhOq9xnjnit1";
const PERMANENT_SUPER_ADMIN_EMAIL = "anantabarman.mtb736172@gmail.com";

function getDb() {
  return admin.firestore(DATABASE_ID);
}

function assertSuperAdmin(auth) {
  if (!auth) {
    throw new HttpsError("unauthenticated", "Authentication required.");
  }
  const isUidMatch = auth.uid === PERMANENT_SUPER_ADMIN_UID;
  const isEmailMatch =
    auth.token &&
    auth.token.email === PERMANENT_SUPER_ADMIN_EMAIL &&
    auth.token.email_verified === true;
  if (!isUidMatch && !isEmailMatch) {
    throw new HttpsError("permission-denied", "Super Admin privileges required.");
  }
}

/**
 * Provision permanent Super Admin custom claims and systemAdmins registry.
 */
exports.verifyAndProvisionSuperAdmin = onCall(async (request) => {
  assertSuperAdmin(request.auth);
  const uid = request.auth.uid;
  const db = getDb();

  await admin.auth().setCustomUserClaims(uid, { role: "SUPER_ADMIN" });
  const now = admin.firestore.FieldValue.serverTimestamp();

  await db.collection("systemAdmins").doc(uid).set(
    {
      uid,
      displayName: request.auth.token.name || "Ananta Barman",
      email: request.auth.token.email || PERMANENT_SUPER_ADMIN_EMAIL,
      role: "SUPER_ADMIN",
      createdAt: now,
      updatedAt: now,
    },
    { merge: true }
  );

  await db.collection("systemLogs").add({
    action: "SUPER_ADMIN_VERIFIED",
    actorUid: uid,
    actorName: "Ananta Barman",
    targetType: "SYSTEM_ADMIN",
    targetId: uid,
    details: "Verified permanent Super Admin identity and synchronized claims.",
    createdAt: now,
  });

  return { verified: true, uid, role: "SUPER_ADMIN" };
});

/**
 * Super Admin callable to approve, reject, suspend, or reactivate a school atomically.
 */
exports.updateSchoolStatus = onCall(async (request) => {
  assertSuperAdmin(request.auth);
  const { schoolId, status, reason } = request.data || {};
  const allowed = ["APPROVED", "REJECTED", "SUSPENDED", "PENDING"];
  if (!schoolId || !allowed.includes(status)) {
    throw new HttpsError("invalid-argument", "Valid schoolId and status are required.");
  }

  const db = getDb();
  const schoolRef = db.collection("schools").doc(schoolId);
  const snap = await schoolRef.get();
  if (!snap.exists) {
    throw new HttpsError("not-found", "School not found.");
  }

  const schoolData = snap.data();
  const now = admin.firestore.FieldValue.serverTimestamp();
  const batch = db.batch();

  const schoolUpdate = { status, updatedAt: now };
  if (reason) schoolUpdate.rejectionReason = String(reason).slice(0, 300);
  batch.update(schoolRef, schoolUpdate);

  if (schoolData.adminUid) {
    const userRef = db.collection("users").doc(schoolData.adminUid);
    const nextRole = status === "APPROVED" ? "SCHOOL_ADMIN" : "PENDING_ADMIN";
    batch.update(userRef, {
      role: nextRole,
      active: status !== "SUSPENDED",
      updatedAt: now,
    });
  }

  const logRef = db.collection("systemLogs").doc();
  batch.set(logRef, {
    action: `SCHOOL_${status}`,
    actorUid: request.auth.uid,
    actorName: "Ananta Barman",
    targetType: "SCHOOL",
    targetId: schoolId,
    details: `School ${schoolData.schoolName} status updated to ${status}.`,
    createdAt: now,
  });

  await batch.commit();
  return { success: true, schoolId, status };
});

/**
 * Notify Super Admin topic when a new school submits registration.
 */
exports.onSchoolRegistered = onDocumentCreated(
  { document: "schools/{schoolId}", database: DATABASE_ID },
  async (event) => {
    const school = event.data && event.data.data();
    if (!school) return;

    await admin.messaging().send({
      topic: "super_admin_alerts",
      notification: {
        title: "New School Registration Pending",
        body: `${school.schoolName} (${school.district}, ${school.state}) awaits approval.`,
      },
      data: {
        type: "SCHOOL_REGISTRATION",
        schoolId: event.params.schoolId,
      },
    });
  }
);

/**
 * Parent Notification & Rate-Limit Audit when attendance is recorded.
 */
exports.onAttendanceRecorded = onDocumentCreated(
  { document: "schools/{schoolId}/attendance/{attendanceId}", database: DATABASE_ID },
  async (event) => {
    const record = event.data && event.data.data();
    if (!record) return;

    const db = getDb();
    const schoolSnap = await db.collection("schools").doc(event.params.schoolId).get();
    const school = schoolSnap.exists ? schoolSnap.data() : null;
    if (!school || !school.parentNotificationsEnabled) return;

    const statusLabel = record.status || "PRESENT";
    await admin.messaging().send({
      topic: `student_${event.params.schoolId}_${record.studentId}`,
      notification: {
        title: `${school.schoolName} — Attendance Update`,
        body: `${record.studentName} marked ${statusLabel} on ${record.attendanceDate} at ${record.attendanceTime}.`,
      },
      data: {
        type: "ATTENDANCE_ALERT",
        schoolId: event.params.schoolId,
        studentId: record.studentId,
        status: statusLabel,
        attendanceDate: record.attendanceDate,
        attendanceTime: record.attendanceTime,
      },
    });
  }
);

/**
 * Broadcast School Announcements to school topic via FCM.
 */
exports.onAnnouncementCreated = onDocumentCreated(
  { document: "schools/{schoolId}/announcements/{announcementId}", database: DATABASE_ID },
  async (event) => {
    const notice = event.data && event.data.data();
    if (!notice) return;

    await admin.messaging().send({
      topic: `school_${event.params.schoolId}`,
      notification: {
        title: `[${notice.category}] ${notice.title}`,
        body: String(notice.body || "").slice(0, 180),
      },
      data: {
        type: "SCHOOL_ANNOUNCEMENT",
        schoolId: event.params.schoolId,
        announcementId: event.params.announcementId,
      },
    });
  }
);

/**
 * Audit trail when an attendance record is corrected.
 */
exports.onAttendanceCorrected = onDocumentUpdated(
  { document: "schools/{schoolId}/attendance/{attendanceId}", database: DATABASE_ID },
  async (event) => {
    const before = event.data && event.data.before.data();
    const after = event.data && event.data.after.data();
    if (!before || !after || before.status === after.status) return;

    const db = getDb();
    await db
      .collection("schools")
      .doc(event.params.schoolId)
      .collection("auditLogs")
      .add({
        action: "ATTENDANCE_CORRECTED",
        actorUid: after.recordedByUid,
        actorName: after.recordedByName,
        targetType: "ATTENDANCE",
        targetId: event.params.attendanceId,
        details: `Changed ${after.studentName} (${after.attendanceDate}) from ${before.status} to ${after.status}. Note: ${after.correctionNote || "N/A"}`,
        schoolId: event.params.schoolId,
        adminUid: after.adminUid,
        memberUids: after.memberUids,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      });
  }
);
