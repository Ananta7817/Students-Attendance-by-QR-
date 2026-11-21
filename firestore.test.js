const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";

const SUPER_ADMIN_UID = "Q6vhlqwqAlMvDy4WFhOq9xnjnit1";
const SUPER_ADMIN_EMAIL = "anantabarman.mtb736172@gmail.com";
const SCHOOL_A_ADMIN = "school_admin_a";
const SCHOOL_B_ADMIN = "school_admin_b";
const TEACHER_A_UID = "teacher_a_1";
const TEACHER_A_EMAIL = "teacher.a@schoola.edu";
const OUTSIDER_UID = "outsider_999";

const SCHOOL_A_ID = "SCH_A_1001";
const SCHOOL_B_ID = "SCH_B_2002";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msOffset = 2000) {
  return new Date(Date.now() - msOffset);
}

function makeSchoolPayload(adminUid, status = "PENDING", overrides = {}) {
  const ts = pastDate(5000);
  return {
    schoolName: "Saffron Vidya Mandir",
    schoolCode: "UDISE-190101001",
    headmasterName: "Dr. Subhash Roy",
    email: "principal@saffronvm.edu",
    phone: "+919876543210",
    address: "12 Education Road",
    villageOrTown: "Cooch Behar",
    district: "Cooch Behar",
    state: "West Bengal",
    pinCode: "736172",
    adminUid,
    memberUids: [adminUid],
    status,
    parentNotificationsEnabled: true,
    studentCount: 0,
    teacherCount: 0,
    classCount: 0,
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function makeStudentPayload(schoolId, adminUid, memberUids, overrides = {}) {
  const ts = pastDate(5000);
  return {
    studentIdCode: "STU-2026-001",
    studentName: "Aarav Sen",
    fatherName: "Rajesh Sen",
    motherName: "Meera Sen",
    dateOfBirth: "2012-04-15",
    gender: "Male",
    classId: "CLS_10",
    className: "Class 10",
    section: "A",
    rollNumber: "1",
    address: "Station Para, Cooch Behar",
    parentContact: "+919830011223",
    admissionDate: "2024-01-10",
    status: "ACTIVE",
    qrTokenId: "QRT_STU_001",
    qrVersion: 1,
    schoolId,
    adminUid,
    memberUids,
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function makeAttendancePayload(schoolId, studentId, recordedByUid, adminUid, memberUids, overrides = {}) {
  const ts = pastDate(3000);
  return {
    schoolId,
    studentId,
    studentIdCode: "STU-2026-001",
    studentName: "Aarav Sen",
    classId: "CLS_10",
    className: "Class 10",
    section: "A",
    rollNumber: "1",
    attendanceDate: "2026-11-20",
    attendanceTime: "09:15:00",
    status: "PRESENT",
    method: "QR",
    recordedByUid,
    recordedByName: "Priya Das",
    adminUid,
    memberUids,
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("schools").get());
  await assertFails(unauth.collection("users").doc(OUTSIDER_UID).get());
  await assertFails(
    unauth.collection("schools").doc(SCHOOL_A_ID).set(makeSchoolPayload(SCHOOL_A_ADMIN))
  );
});

test("Super Admin UID & verified email access + email spoofing rejection", async () => {
  const superDb = testEnv.authenticatedContext(SUPER_ADMIN_UID, {
    email: SUPER_ADMIN_EMAIL,
    email_verified: true,
  }).firestore();
  const ts = pastDate(4000);

  await assertSucceeds(
    superDb.collection("systemAdmins").doc(SUPER_ADMIN_UID).set({
      uid: SUPER_ADMIN_UID,
      displayName: "Ananta Barman",
      email: SUPER_ADMIN_EMAIL,
      role: "SUPER_ADMIN",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Spoofed unverified Super Admin email MUST fail
  const spoofDb = testEnv.authenticatedContext("spoof_uid", {
    email: SUPER_ADMIN_EMAIL,
    email_verified: false,
  }).firestore();
  await assertFails(spoofDb.collection("systemAdmins").doc("spoof_uid").set({
    uid: "spoof_uid",
    displayName: "Attacker",
    email: SUPER_ADMIN_EMAIL,
    role: "SUPER_ADMIN",
    createdAt: ts,
    updatedAt: ts,
  }));
  await assertFails(spoofDb.collection("schools").get());

  // School Admin cannot delete or modify permanent Super Admin
  const adminDb = testEnv.authenticatedContext(SCHOOL_A_ADMIN).firestore();
  await assertFails(adminDb.collection("systemAdmins").doc(SUPER_ADMIN_UID).delete());
});

test("Privilege escalation blocked: normal users cannot self-assign SUPER_ADMIN or SCHOOL_ADMIN", async () => {
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID, {
    email: "outsider@example.com",
    email_verified: true,
  }).firestore();
  const ts = pastDate(4000);

  await assertFails(
    outsiderDb.collection("users").doc(OUTSIDER_UID).set({
      uid: OUTSIDER_UID,
      displayName: "Hacker",
      email: "outsider@example.com",
      role: "SUPER_ADMIN",
      active: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertFails(
    outsiderDb.collection("users").doc(OUTSIDER_UID).set({
      uid: OUTSIDER_UID,
      displayName: "Hacker",
      email: "outsider@example.com",
      role: "SCHOOL_ADMIN",
      active: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Valid PENDING_ADMIN registration succeeds
  await assertSucceeds(
    outsiderDb.collection("users").doc(OUTSIDER_UID).set({
      uid: OUTSIDER_UID,
      displayName: "Headmaster",
      email: "outsider@example.com",
      role: "PENDING_ADMIN",
      active: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Cannot escalate own role via update
  await assertFails(
    outsiderDb.collection("users").doc(OUTSIDER_UID).update({
      role: "SCHOOL_ADMIN",
      updatedAt: pastDate(1000),
    })
  );
});

test("School registration starts PENDING; blocked from adding students until Super Admin approves", async () => {
  const adminADb = testEnv.authenticatedContext(SCHOOL_A_ADMIN, {
    email: "principal@saffronvm.edu",
    email_verified: true,
  }).firestore();

  // Cannot self-register as APPROVED
  await assertFails(
    adminADb.collection("schools").doc(SCHOOL_A_ID).set(makeSchoolPayload(SCHOOL_A_ADMIN, "APPROVED"))
  );

  // Registering as PENDING succeeds
  await assertSucceeds(
    adminADb.collection("schools").doc(SCHOOL_A_ID).set(makeSchoolPayload(SCHOOL_A_ADMIN, "PENDING"))
  );

  // Pending school admin CANNOT add students yet
  await assertFails(
    adminADb
      .collection("schools").doc(SCHOOL_A_ID)
      .collection("students").doc("STU_1")
      .set(makeStudentPayload(SCHOOL_A_ID, SCHOOL_A_ADMIN, [SCHOOL_A_ADMIN]))
  );

  // Super Admin approves School A
  const superDb = testEnv.authenticatedContext(SUPER_ADMIN_UID, {
    email: SUPER_ADMIN_EMAIL,
    email_verified: true,
  }).firestore();
  await assertSucceeds(
    superDb.collection("schools").doc(SCHOOL_A_ID).update({
      status: "APPROVED",
      updatedAt: pastDate(1000),
    })
  );

  // Approved School Admin CAN now add students
  await assertSucceeds(
    adminADb
      .collection("schools").doc(SCHOOL_A_ID)
      .collection("students").doc("STU_1")
      .set(makeStudentPayload(SCHOOL_A_ID, SCHOOL_A_ADMIN, [SCHOOL_A_ADMIN]))
  );
});

test("Strict Multi-Tenant School Isolation: School B cannot read or modify School A data", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("schools").doc(SCHOOL_A_ID).set(makeSchoolPayload(SCHOOL_A_ADMIN, "APPROVED"));
    await db.collection("schools").doc(SCHOOL_B_ID).set(makeSchoolPayload(SCHOOL_B_ADMIN, "APPROVED"));
    await db.collection("schools").doc(SCHOOL_A_ID).collection("students").doc("STU_1")
      .set(makeStudentPayload(SCHOOL_A_ID, SCHOOL_A_ADMIN, [SCHOOL_A_ADMIN, TEACHER_A_UID]));
  });

  const adminBDb = testEnv.authenticatedContext(SCHOOL_B_ADMIN).firestore();
  await assertFails(adminBDb.collection("schools").doc(SCHOOL_A_ID).get());
  await assertFails(
    adminBDb.collection("schools").doc(SCHOOL_A_ID).collection("students").doc("STU_1").get()
  );
  await assertFails(
    adminBDb.collection("schools").doc(SCHOOL_A_ID).collection("students")
      .where("adminUid", "==", SCHOOL_A_ADMIN).get()
  );
  await assertFails(
    adminBDb.collection("schools").doc(SCHOOL_A_ID).collection("students").doc("STU_1")
      .update({ studentName: "Tampered", updatedAt: pastDate(500) })
  );

  // School A admin filtered query succeeds; unfiltered query fails
  const adminADb = testEnv.authenticatedContext(SCHOOL_A_ADMIN).firestore();
  await assertSucceeds(
    adminADb.collection("schools").doc(SCHOOL_A_ID).collection("students")
      .where("adminUid", "==", SCHOOL_A_ADMIN).get()
  );
  await assertFails(
    adminADb.collection("schools").doc(SCHOOL_A_ID).collection("students").get()
  );
});

test("Teacher class-scoped attendance submission, deterministic ID, and unauthorized class rejection", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const ts = pastDate(5000);
    await db.collection("schools").doc(SCHOOL_A_ID).set(
      makeSchoolPayload(SCHOOL_A_ADMIN, "APPROVED", { memberUids: [SCHOOL_A_ADMIN, TEACHER_A_UID] })
    );
    await db.collection("users").doc(TEACHER_A_UID).set({
      uid: TEACHER_A_UID,
      displayName: "Priya Das",
      email: TEACHER_A_EMAIL,
      role: "TEACHER",
      schoolId: SCHOOL_A_ID,
      adminUid: SCHOOL_A_ADMIN,
      assignedClassIds: ["CLS_10"],
      assignedSections: ["A"],
      assignedSubjects: ["Mathematics"],
      active: true,
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const teacherDb = testEnv.authenticatedContext(TEACHER_A_UID, {
    email: TEACHER_A_EMAIL,
    email_verified: true,
  }).firestore();

  // Assigned class (CLS_10) with deterministic doc ID succeeds
  await assertSucceeds(
    teacherDb
      .collection("schools").doc(SCHOOL_A_ID)
      .collection("attendance").doc("STU_1_2026-11-20")
      .set(makeAttendancePayload(SCHOOL_A_ID, "STU_1", TEACHER_A_UID, SCHOOL_A_ADMIN, [SCHOOL_A_ADMIN, TEACHER_A_UID]))
  );

  // Unassigned class (CLS_12) MUST fail
  await assertFails(
    teacherDb
      .collection("schools").doc(SCHOOL_A_ID)
      .collection("attendance").doc("STU_2_2026-11-20")
      .set(makeAttendancePayload(SCHOOL_A_ID, "STU_2", TEACHER_A_UID, SCHOOL_A_ADMIN, [SCHOOL_A_ADMIN, TEACHER_A_UID], {
        classId: "CLS_12",
        className: "Class 12",
      }))
  );

  // Non-deterministic doc ID MUST fail (prevents duplicate attendance bypass)
  await assertFails(
    teacherDb
      .collection("schools").doc(SCHOOL_A_ID)
      .collection("attendance").doc("random_dup_id")
      .set(makeAttendancePayload(SCHOOL_A_ID, "STU_1", TEACHER_A_UID, SCHOOL_A_ADMIN, [SCHOOL_A_ADMIN, TEACHER_A_UID]))
  );

  // Teacher cannot create or delete students
  await assertFails(
    teacherDb
      .collection("schools").doc(SCHOOL_A_ID)
      .collection("students").doc("STU_99")
      .set(makeStudentPayload(SCHOOL_A_ID, SCHOOL_A_ADMIN, [SCHOOL_A_ADMIN, TEACHER_A_UID]))
  );

  // Teacher list query with memberUids filter succeeds
  await assertSucceeds(
    teacherDb
      .collection("schools").doc(SCHOOL_A_ID)
      .collection("attendance")
      .where("memberUids", "array-contains", TEACHER_A_UID)
      .get()
  );
});

test("Shadow field injection & future timestamp tampering rejected", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("schools").doc(SCHOOL_A_ID).set(makeSchoolPayload(SCHOOL_A_ADMIN, "APPROVED"));
  });

  const adminADb = testEnv.authenticatedContext(SCHOOL_A_ADMIN).firestore();

  // Ghost field rejected
  await assertFails(
    adminADb.collection("schools").doc(SCHOOL_A_ID).collection("students").doc("STU_GHOST")
      .set({
        ...makeStudentPayload(SCHOOL_A_ID, SCHOOL_A_ADMIN, [SCHOOL_A_ADMIN]),
        ghostField: true,
      })
  );

  // Future timestamp rejected
  await assertFails(
    adminADb.collection("schools").doc(SCHOOL_A_ID).collection("students").doc("STU_FUTURE")
      .set(makeStudentPayload(SCHOOL_A_ID, SCHOOL_A_ADMIN, [SCHOOL_A_ADMIN], {
        createdAt: new Date(Date.now() + 120000),
        updatedAt: new Date(Date.now() + 120000),
      }))
  );
});
