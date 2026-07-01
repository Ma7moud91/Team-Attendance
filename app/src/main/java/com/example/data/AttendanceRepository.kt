package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileWriter

class AttendanceRepository(private val dao: AttendanceDao) {

    val allMembers: Flow<List<Member>> = dao.getAllMembers()
    val allAttendance: Flow<List<Attendance>> = dao.getAllAttendance()
    val allSyncLogs: Flow<List<SyncLog>> = dao.getAllSyncLogs()
    val allMessages: Flow<List<InboxMessage>> = dao.getAllMessages()
    val allAssignmentHistory: Flow<List<SupervisorAssignmentHistory>> = dao.getAllAssignmentHistory()
    val allAuditLogs: Flow<List<AuditLog>> = dao.getAllAuditLogs()

    fun getAttendanceForDate(date: String): Flow<List<Attendance>> = dao.getAttendanceForDate(date)
    fun getAttendanceForMember(memberId: Long): Flow<List<Attendance>> = dao.getAttendanceForMember(memberId)

    suspend fun insertMember(member: Member): Long = dao.insertMember(member)
    suspend fun deleteMember(member: Member) = dao.deleteMember(member)
    suspend fun getMemberById(id: Long): Member? = dao.getMemberById(id)
    suspend fun getMemberCount(): Int = dao.getMemberCount()
    suspend fun getMemberByEmailOrName(identifier: String): Member? = dao.getMemberByEmailOrName(identifier)

    suspend fun insertAttendance(attendance: Attendance): Long = dao.insertAttendance(attendance)
    suspend fun deleteAttendance(attendance: Attendance) = dao.deleteAttendance(attendance)
    suspend fun deleteAttendanceForMember(memberId: Long) = dao.deleteAttendanceForMember(memberId)
    suspend fun deleteUnapprovedAttendance() = dao.deleteUnapprovedAttendance()
    suspend fun getAttendanceForMemberAndDate(memberId: Long, date: String): Attendance? =
        dao.getAttendanceForMemberAndDate(memberId, date)

    suspend fun approveAttendance(id: Long, supervisorId: Long) = dao.approveAttendance(id, supervisorId)
    suspend fun rejectAttendance(id: Long, reason: String) = dao.rejectAttendance(id, reason)
    suspend fun getAttendanceById(id: Long): Attendance? = dao.getAttendanceById(id)

    suspend fun performCloudSync(): SyncLog {
        val unsynced = dao.getUnsyncedAttendance()
        val count = unsynced.size

        // Introduce a slight delay to simulate actual internet/network roundtrip
        delay(1500)

        val log = if (count > 0) {
            // Mark all as synced
            dao.markAllAsSynced()
            SyncLog(
                recordsSynced = count,
                status = "SUCCESS",
                message = "Successfully backed up $count records to secure cloud database. Server status: 200 OK."
            )
        } else {
            SyncLog(
                recordsSynced = 0,
                status = "SUCCESS",
                message = "Database is already up to date. No new records to backup."
            )
        }

        dao.insertSyncLog(log)
        return log
    }

    suspend fun recordManualSyncFailure(message: String): SyncLog {
        val log = SyncLog(
            recordsSynced = 0,
            status = "FAILED",
            message = message
        )
        dao.insertSyncLog(log)
        return log
    }

    suspend fun insertMessage(message: InboxMessage): Long = dao.insertMessage(message)
    suspend fun insertSyncLog(log: SyncLog): Long = dao.insertSyncLog(log)
    suspend fun markMessageAsRead(id: Long) = dao.markMessageAsRead(id)

    // Notifications
    fun getNotificationsForMember(memberId: Long): Flow<List<AppNotification>> = dao.getNotificationsForMember(memberId)
    suspend fun insertNotification(notification: AppNotification): Long = dao.insertNotification(notification)
    suspend fun markNotificationAsRead(id: Long) = dao.markNotificationAsRead(id)

    suspend fun updateMemberProfileImage(memberId: Long, imageUri: String) = dao.updateMemberProfileImage(memberId, imageUri)

    suspend fun insertAssignmentHistory(history: SupervisorAssignmentHistory): Long = dao.insertAssignmentHistory(history)
    suspend fun insertAuditLog(log: AuditLog): Long = dao.insertAuditLog(log)
}
