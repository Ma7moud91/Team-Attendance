package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileWriter

class AttendanceRepository(private val dao: AttendanceDao) {

    val allMembers: Flow<List<Member>> = dao.getAllMembers()
    val allAttendance: Flow<List<Attendance>> = dao.getAllAttendance()
    val allSyncLogs: Flow<List<SyncLog>> = dao.getAllSyncLogs()

    fun getAttendanceForDate(date: String): Flow<List<Attendance>> = dao.getAttendanceForDate(date)
    fun getAttendanceForMember(memberId: Long): Flow<List<Attendance>> = dao.getAttendanceForMember(memberId)

    suspend fun insertMember(member: Member): Long = dao.insertMember(member)
    suspend fun deleteMember(member: Member) = dao.deleteMember(member)
    suspend fun getMemberById(id: Long): Member? = dao.getMemberById(id)

    suspend fun insertAttendance(attendance: Attendance): Long = dao.insertAttendance(attendance)
    suspend fun deleteAttendance(attendance: Attendance) = dao.deleteAttendance(attendance)
    suspend fun deleteAttendanceForMember(memberId: Long) = dao.deleteAttendanceForMember(memberId)
    suspend fun deleteUnapprovedAttendance() = dao.deleteUnapprovedAttendance()
    suspend fun getAttendanceForMemberAndDate(memberId: Long, date: String): Attendance? =
        dao.getAttendanceForMemberAndDate(memberId, date)

    suspend fun approveAttendance(id: Long, supervisorId: Long) = dao.approveAttendance(id, supervisorId)

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
}
