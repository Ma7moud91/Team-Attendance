/**
 * Team Attendance — Cloud Functions
 *
 * Setup (once): firebase init functions   (choose TypeScript)
 * Then drop this file in as functions/src/index.ts and:
 *   npm install firebase-admin firebase-functions
 *   firebase deploy --only functions
 */

import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, FieldValue } from "firebase-admin/firestore";

initializeApp();
const db = getFirestore();

const VALID_ROLES = ["ADMIN", "SUPERVISOR", "EMPLOYEE", "DEVELOPER"] as const;
type Role = (typeof VALID_ROLES)[number];

interface AssignRoleRequest {
  uid: string;
  role: Role;
  name: string;
  email: string;
  title?: string;
  supervisorId?: string | null;
}

/**
 * assignRole — the ONLY place a user's role or supervisor assignment
 * changes. Callable only by an existing ADMIN or DEVELOPER, verified from
 * the caller's own ID token custom claim (not from any client-supplied
 * field, which could be spoofed).
 *
 * Effects:
 *  1. Sets the custom claim { role } via the Admin SDK — this is what
 *     firestore.rules actually trusts for authorization.
 *  2. Mirrors the profile into members/{uid} for the app UI to read.
 *  3. Logs a supervisor_assignment_history entry when supervisorId changes.
 *
 * Bootstrap note: the very first Developer/Admin account has no existing
 * privileged caller to grant it. Set that one manually via the Firebase
 * CLI (`firebase auth:import` or the Admin SDK from a trusted script), not
 * through this function.
 */
export const assignRole = onCall(async (request) => {
  const callerRole = request.auth?.token?.role;
  if (callerRole !== "ADMIN" && callerRole !== "DEVELOPER") {
    throw new HttpsError("permission-denied", "Only an admin can assign roles.");
  }

  const { uid, role, name, email, title, supervisorId } =
    request.data as AssignRoleRequest;

  if (!uid || !VALID_ROLES.includes(role)) {
    throw new HttpsError("invalid-argument", "uid and a valid role are required.");
  }

  const memberRef = db.collection("members").doc(uid);
  const existing = await memberRef.get();
  const previous = existing.data();

  if (existing.exists && previous?.supervisorId !== (supervisorId ?? null)) {
    await db.collection("supervisor_assignment_history").add({
      employeeId: uid,
      employeeName: name,
      previousSupervisorId: previous?.supervisorId ?? null,
      previousSupervisorName: previous?.supervisorName ?? null,
      newSupervisorId: supervisorId ?? null,
      newSupervisorName: null,
      assignedByAdminId: request.auth!.uid,
      assignedByAdminName: request.auth!.token.name ?? "",
      timestamp: FieldValue.serverTimestamp(),
    });
  }

  await getAuth().setCustomUserClaims(uid, { role });

  await memberRef.set(
    {
      name,
      email,
      title: title ?? "",
      role,
      supervisorId: supervisorId ?? null,
      isActive: true,
    },
    { merge: true }
  );

  return { ok: true };
});

/**
 * onAttendanceCreate — stamps every new attendance doc with the
 * submitting member's CURRENT supervisorId, read server-side from their
 * members/{uid} doc. firestore.rules refuses to let the client set this
 * field itself, which is what makes a Supervisor's flat
 * whereEqualTo("supervisorId", uid) query trustworthy.
 */
export const onAttendanceCreate = onDocumentCreated(
  "attendance/{attendanceId}",
  async (event) => {
    const snap = event.data;
    if (!snap) return;

    const memberId = snap.data().memberId as string | undefined;
    if (!memberId) return;

    const memberDoc = await db.collection("members").doc(memberId).get();
    await snap.ref.update({
      supervisorId: memberDoc.data()?.supervisorId ?? null,
    });
  }
);
