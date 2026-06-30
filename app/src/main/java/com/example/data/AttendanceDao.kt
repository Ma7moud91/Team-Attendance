package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {
    // Member Queries
    @Query("SELECT * FROM members ORDER BY name ASC")
    fun getAllMembers(): Flow<List<Member>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: Member): Long

    @Delete
    suspend fun deleteMember(member: Member)

    @Query("SELECT * FROM members WHERE id = :id LIMIT 1")
    suspend fun getMemberById(id: Long): Member?

    // Attendance Queries
    @Query("SELECT * FROM attendance WHERE date = :date")
    fun getAttendanceForDate(date: String): Flow<List<Attendance>>

    @Query("SELECT * FROM attendance ORDER BY date DESC")
    fun getAllAttendance(): Flow<List<Attendance>>

    @Query("SELECT * FROM attendance WHERE memberId = :memberId ORDER BY date DESC")
    fun getAttendanceForMember(memberId: Long): Flow<List<Attendance>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendance(attendance: Attendance): Long

    @Delete
    suspend fun deleteAttendance(attendance: Attendance)

    @Query("DELETE FROM attendance WHERE memberId = :memberId")
    suspend fun deleteAttendanceForMember(memberId: Long)

    @Query("DELETE FROM attendance WHERE approvedBySupervisorId IS NULL")
    suspend fun deleteUnapprovedAttendance()

    @Query("SELECT * FROM attendance WHERE date = :date AND memberId = :memberId LIMIT 1")
    suspend fun getAttendanceForMemberAndDate(memberId: Long, date: String): Attendance?

    @Query("UPDATE attendance SET approvedBySupervisorId = :supervisorId WHERE id = :id")
    suspend fun approveAttendance(id: Long, supervisorId: Long)

    @Query("UPDATE attendance SET isSynced = 1 WHERE isSynced = 0")
    suspend fun markAllAsSynced()

    @Query("SELECT * FROM attendance WHERE isSynced = 0")
    suspend fun getUnsyncedAttendance(): List<Attendance>

    // Sync Log Queries
    @Query("SELECT * FROM sync_logs ORDER BY timestamp DESC")
    fun getAllSyncLogs(): Flow<List<SyncLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncLog(log: SyncLog): Long
}
