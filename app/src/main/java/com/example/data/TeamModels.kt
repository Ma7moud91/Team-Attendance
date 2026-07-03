package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "members")
data class Member(
    @PrimaryKey val id: Long = 0,
    val name: String,
    val title: String = "",
    val role: String, // "ADMIN", "SUPERVISOR", "EMPLOYEE"
    val email: String,
    val requiresLocation: Boolean = false,
    val profileImage: String? = null,
    val isActive: Boolean = true,
    val supervisorId: Long? = null,
    val uid: String? = null
)

@Entity(tableName = "supervisor_assignment_history")
data class SupervisorAssignmentHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: Long,
    val employeeName: String,
    val previousSupervisorId: Long?,
    val previousSupervisorName: String?,
    val newSupervisorId: Long?,
    val newSupervisorName: String?,
    val assignedByAdminId: Long,
    val assignedByAdminName: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "audit_logs")
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val username: String,
    val userRole: String,
    val timestamp: Long = System.currentTimeMillis(),
    val actionType: String, // "SUPERVISOR_ASSIGNMENT", "REPORT_GENERATION", "REPORT_EXPORT"
    val details: String,
    val reportType: String? = null, // "FULL_ATTENDANCE", "SINGLE_EMPLOYEE"
    val exportFormat: String? = null // "XLSX", "PDF"
)

@Entity(tableName = "attendance")
data class Attendance(
    @PrimaryKey val id: Long = 0,
    val memberId: Long,
    val date: String, // "YYYY-MM-DD"
    val isPresent: Boolean,
    val punchInTime: String? = null, // "HH:MM"
    val punchOutTime: String? = null, // "HH:MM"
    val overtimeHours: Double = 0.0,
    val locationData: String? = null, // "Lat, Lng"
    val status: String = "PENDING", // "PENDING", "APPROVED", "REJECTED"
    val approvedBySupervisorId: Long? = null, // Long supervisor ID if approved
    val rejectionReason: String? = null,
    val isSynced: Boolean = false
)

@Entity(tableName = "notifications")
data class AppNotification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberId: Long,
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

@Entity(tableName = "sync_logs")
data class SyncLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val recordsSynced: Int,
    val status: String, // "SUCCESS", "FAILED"
    val message: String
)

@Entity(tableName = "messages")
data class InboxMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val senderId: Long,
    val senderName: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)
