package com.example.data.firestore

import com.google.firebase.firestore.DocumentId

/**
 * Firestore-mapped models — plain data classes, no Room annotations.
 * Kept in a separate `firestore` package with a `Firestore` prefix so they
 * don't collide with the existing Room entities of the same shape in
 * com.example.data (Member, Attendance, ...).
 *
 * Ids are String (Firestore document ids), not Room's autoGenerate Long.
 * For FirestoreMember, the doc id IS the Firebase Auth uid — that identity
 * is what lets firestore.rules compare request.auth.uid straight against
 * the document, with no extra lookup.
 */

data class FirestoreMember(
    @DocumentId val id: String = "",
    val name: String = "",
    val title: String = "",
    val role: String = "EMPLOYEE", // display/query convenience only — auth trusts the custom claim, not this field
    val email: String = "",
    val requiresLocation: Boolean = false,
    val profileImage: String? = null,
    val isActive: Boolean = true,
    val supervisorId: String? = null
)

data class FirestoreAttendance(
    @DocumentId val id: String = "",
    val memberId: String = "",
    val supervisorId: String? = null, // stamped server-side by onAttendanceCreate — never set this from the client
    val date: String = "",            // "YYYY-MM-DD"
    val isPresent: Boolean = false,
    val punchInTime: String? = null,  // "HH:MM"
    val punchOutTime: String? = null, // "HH:MM"
    val overtimeHours: Double = 0.0,
    val locationData: String? = null, // "Lat, Lng"
    val status: String = "PENDING",   // "PENDING" | "APPROVED" | "REJECTED"
    val approvedBySupervisorId: String? = null,
    val rejectionReason: String? = null
)

data class FirestoreNotification(
    @DocumentId val id: String = "",
    val memberId: String = "",
    val title: String = "",
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

data class FirestoreMessage(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

data class FirestoreAssignmentHistory(
    @DocumentId val id: String = "",
    val employeeId: String = "",
    val employeeName: String = "",
    val previousSupervisorId: String? = null,
    val previousSupervisorName: String? = null,
    val newSupervisorId: String? = null,
    val newSupervisorName: String? = null,
    val assignedByAdminId: String = "",
    val assignedByAdminName: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class FirestoreAuditLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val userRole: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val actionType: String = "", // "SUPERVISOR_ASSIGNMENT", "REPORT_GENERATION", "REPORT_EXPORT"
    val details: String = "",
    val reportType: String? = null,
    val exportFormat: String? = null
)
