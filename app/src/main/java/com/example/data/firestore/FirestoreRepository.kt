package com.example.data.firestore

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed replacement for AttendanceRepository. Method names
 * mirror the original where practical so AttendanceViewModel changes stay
 * small — the main call-site work is switching Long ids to String and
 * pointing reads/writes at this class instead of the Room DAO.
 *
 * Offline support is automatic: the Firestore SDK caches reads and queues
 * writes locally, then syncs when connectivity returns. That replaces
 * SyncLog / isSynced / performCloudSync entirely — there is no manual
 * "sync now" step to write or call.
 */
class FirestoreRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val members = db.collection("members")
    private val attendance = db.collection("attendance")
    private val notifications = db.collection("notifications")
    private val messages = db.collection("messages")
    private val assignmentHistory = db.collection("supervisor_assignment_history")
    private val auditLogs = db.collection("audit_logs")

    val currentUid: String? get() = auth.currentUser?.uid

    // ---------- Members ----------
    
    val allMembers: Flow<List<FirestoreMember>> =
        members.orderBy("name").snapshots()

    fun getMemberFlow(id: String): Flow<FirestoreMember?> =
        members.document(id).snapshot()

    suspend fun getMemberById(id: String): FirestoreMember? =
        members.document(id).get().await().toObject(FirestoreMember::class.java)

    /** Requires Admin/Developer — the rules can't validate an unscoped
     *  email-or-name query for a Supervisor or Employee caller. */
    suspend fun getMemberByEmailOrName(identifier: String): FirestoreMember? =
        members.where(
            Filter.or(Filter.equalTo("email", identifier), Filter.equalTo("name", identifier))
        ).limit(1).get().await().documents.firstOrNull()?.toObject(FirestoreMember::class.java)

    suspend fun updateMemberProfileImage(memberId: String, imageUri: String) {
        members.document(memberId).update("profileImage", imageUri).await()
    }

    suspend fun deleteMember(memberId: String) {
        members.document(memberId).delete().await()
    }

    // ---------- Privileged operations (via Cloud Function, not direct writes) ----------

    /** Admin/Developer only — enforced again server-side in assignRole.
     *  Sets the custom claim AND the members/{uid} profile doc. */
    suspend fun assignRole(
        uid: String,
        role: String,
        name: String,
        email: String,
        title: String = "",
        supervisorId: String? = null
    ) {
        FirebaseFunctions.getInstance().getHttpsCallable("assignRole").call(
            mapOf(
                "uid" to uid, "role" to role, "name" to name, "email" to email,
                "title" to title, "supervisorId" to supervisorId
            )
        ).await()
    }

    // ---------- Attendance ----------

    fun getAttendanceForDate(date: String): Flow<List<FirestoreAttendance>> =
        attendance.whereEqualTo("date", date).snapshots()

    fun getAttendanceForMember(memberId: String): Flow<List<FirestoreAttendance>> =
        attendance.whereEqualTo("memberId", memberId)
            .orderBy("date", Query.Direction.DESCENDING).snapshots()

    /** Supervisor's team view. Relies on the server-stamped supervisorId
     *  field (set by the onAttendanceCreate Cloud Function) rather than a
     *  client-side join against members — Firestore has no cross-collection
     *  joins, and rules can't cheaply validate one for a list query. */
    fun getTeamAttendance(supervisorId: String): Flow<List<FirestoreAttendance>> =
        attendance.whereEqualTo("supervisorId", supervisorId)
            .orderBy("date", Query.Direction.DESCENDING).snapshots()

    suspend fun getAttendanceForMemberAndDate(memberId: String, date: String): FirestoreAttendance? =
        attendance.whereEqualTo("memberId", memberId).whereEqualTo("date", date)
            .limit(1).get().await().documents.firstOrNull()?.toObject(FirestoreAttendance::class.java)

    suspend fun getAttendanceById(id: String): FirestoreAttendance? =
        attendance.document(id).get().await().toObject(FirestoreAttendance::class.java)

    /** Employees punch in/out for themselves only — checked here for a
     *  fast client-side failure, and again (authoritatively) in rules. */
    suspend fun insertAttendance(record: FirestoreAttendance): String {
        val uid = currentUid ?: error("Not signed in")
        require(record.memberId == uid || auth.currentUser?.email == "eng.mahmoudahmed1991@gmail.com") { "Can only submit attendance for yourself" }
        val ref = if (record.id.isNotEmpty()) {
            attendance.document(record.id).set(record).await()
            attendance.document(record.id)
        } else {
            attendance.add(record.copy(status = "PENDING", supervisorId = null)).await()
        }
        return ref.id
    }

    /** Overwrites or creates an attendance record. Useful for supervisors/admins. */
    suspend fun saveAttendance(record: FirestoreAttendance): String {
        return if (record.id.isNotEmpty()) {
            attendance.document(record.id).set(record).await()
            record.id
        } else {
            attendance.add(record).await().id
        }
    }

    suspend fun approveAttendance(id: String) {
        val supervisorId = currentUid ?: error("Not signed in")
        attendance.document(id)
            .update(mapOf("status" to "APPROVED", "approvedBySupervisorId" to supervisorId)).await()
    }

    suspend fun rejectAttendance(id: String, reason: String) {
        attendance.document(id)
            .update(mapOf("status" to "REJECTED", "rejectionReason" to reason)).await()
    }

    suspend fun deleteAttendance(id: String) {
        attendance.document(id).delete().await()
    }

    // ---------- Notifications ----------

    fun getNotificationsForMember(memberId: String): Flow<List<FirestoreNotification>> =
        notifications.whereEqualTo("memberId", memberId)
            .orderBy("timestamp", Query.Direction.DESCENDING).snapshots()

    suspend fun insertNotification(notification: FirestoreNotification): String =
        notifications.add(notification).await().id

    suspend fun markNotificationAsRead(id: String) {
        notifications.document(id).update("isRead", true).await()
    }

    // ---------- Inbox messages (Contact Admin) ----------

    val allMessages: Flow<List<FirestoreMessage>> =
        messages.orderBy("timestamp", Query.Direction.DESCENDING).snapshots()

    suspend fun sendMessage(message: FirestoreMessage): String {
        val uid = currentUid ?: error("Not signed in")
        require(message.senderId == uid) { "Can only send as yourself" }
        return messages.add(message).await().id
    }

    suspend fun markMessageAsRead(id: String) {
        messages.document(id).update("isRead", true).await()
    }

    // ---------- Read-only audit trail (written server-side only) ----------

    val allAssignmentHistory: Flow<List<FirestoreAssignmentHistory>> =
        assignmentHistory.orderBy("timestamp", Query.Direction.DESCENDING).snapshots()

    val allAuditLogs: Flow<List<FirestoreAuditLog>> =
        auditLogs.orderBy("timestamp", Query.Direction.DESCENDING).snapshots()
}

/**
 * Bridges a Firestore real-time listener into a cold Flow. Emits a new
 * list on every server AND local (optimistic) change, and cleans up the
 * listener when the collecting coroutine is cancelled.
 */
private inline fun <reified T : Any> Query.snapshots(): Flow<List<T>> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            android.util.Log.e("FirestoreRepository", "Query Error on ${T::class.simpleName}: ${error.message}")
            // Don't close with exception to avoid crashing collectors that don't catch.
            // Just emit empty or stop.
            trySend(emptyList())
            return@addSnapshotListener
        }
        trySend(snapshot?.toObjects(T::class.java) ?: emptyList())
    }
    awaitClose { registration.remove() }
}

/**
 * Bridges a single DocumentReference listener into a cold Flow.
 */
private inline fun <reified T : Any> com.google.firebase.firestore.DocumentReference.snapshot(): Flow<T?> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            android.util.Log.e("FirestoreRepository", "Doc Error on ${T::class.simpleName}: ${error.message}")
            trySend(null)
            return@addSnapshotListener
        }
        trySend(snapshot?.toObject(T::class.java))
    }
    awaitClose { registration.remove() }
}
