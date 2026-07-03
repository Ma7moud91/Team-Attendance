package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {

    // --- Members ---
    @Query("SELECT * FROM members ORDER BY name ASC")
    fun getAllMembers(): Flow<List<Member>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: Member)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<Member>)

    @Delete
    suspend fun deleteMember(member: Member)

    @Query("DELETE FROM members")
    suspend fun clearMembers()


    // --- Attendance ---
    @Query("SELECT * FROM attendance ORDER BY date DESC")
    fun getAllAttendance(): Flow<List<Attendance>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendance(attendance: Attendance)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendances(attendances: List<Attendance>)

    @Delete
    suspend fun deleteAttendance(attendance: Attendance)

    @Query("DELETE FROM attendance")
    suspend fun clearAttendance()


    // --- Sync Logs ---
    @Query("SELECT * FROM sync_logs ORDER BY timestamp DESC")
    fun getAllSyncLogs(): Flow<List<SyncLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncLog(syncLog: SyncLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncLogs(syncLogs: List<SyncLog>)

    @Query("DELETE FROM sync_logs")
    suspend fun clearSyncLogs()


    // --- Inbox Messages ---
    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<InboxMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: InboxMessage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<InboxMessage>)

    @Query("DELETE FROM messages")
    suspend fun clearMessages()


    // --- Supervisor Assignment History ---
    @Query("SELECT * FROM supervisor_assignment_history ORDER BY timestamp DESC")
    fun getAllAssignmentHistory(): Flow<List<SupervisorAssignmentHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssignmentHistory(history: SupervisorAssignmentHistory)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssignmentHistories(histories: List<SupervisorAssignmentHistory>)

    @Query("DELETE FROM supervisor_assignment_history")
    suspend fun clearAssignmentHistory()


    // --- Audit Logs ---
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC")
    fun getAllAuditLogs(): Flow<List<AuditLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(auditLog: AuditLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLogs(auditLogs: List<AuditLog>)

    @Query("DELETE FROM audit_logs")
    suspend fun clearAuditLogs()


    // --- Notifications ---
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<AppNotification>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: AppNotification)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotifications(notifications: List<AppNotification>)

    @Query("DELETE FROM notifications")
    suspend fun clearNotifications()
}
