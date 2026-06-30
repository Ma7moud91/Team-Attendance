package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "members")
data class Member(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val role: String, // "ADMIN", "SUPERVISOR", "EMPLOYEE"
    val email: String,
    val isActive: Boolean = true
)

@Entity(tableName = "attendance")
data class Attendance(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberId: Long,
    val date: String, // "YYYY-MM-DD"
    val isPresent: Boolean,
    val punchInTime: String? = null, // "HH:MM"
    val punchOutTime: String? = null, // "HH:MM"
    val overtimeHours: Double = 0.0,
    val approvedBySupervisorId: Long? = null, // null if pending, Long supervisor ID if approved
    val isSynced: Boolean = false
)

@Entity(tableName = "sync_logs")
data class SyncLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val recordsSynced: Int,
    val status: String, // "SUCCESS", "FAILED"
    val message: String
)
