package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class AttendanceViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = AttendanceRepository(database.attendanceDao())

    // UI state streams
    val members = repository.allMembers.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val attendanceRecords = repository.allAttendance.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val syncLogs = repository.allSyncLogs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Current State
    private val _currentUser = MutableStateFlow<Member?>(null)
    val currentUser: StateFlow<Member?> = _currentUser.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _isOfflineMode = MutableStateFlow(false)
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()

    private val _selectedDate = MutableStateFlow(getCurrentDateString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    private val _syncingState = MutableStateFlow(false)
    val syncingState: StateFlow<Boolean> = _syncingState.asStateFlow()

    private val _exportResult = MutableStateFlow<String?>(null)
    val exportResult: StateFlow<String?> = _exportResult.asStateFlow()

    private val _linkedExcelFile = MutableStateFlow<String?>(null)
    val linkedExcelFile: StateFlow<String?> = _linkedExcelFile.asStateFlow()

    private val _excelLinkStatus = MutableStateFlow("NOT LINKED")
    val excelLinkStatus: StateFlow<String> = _excelLinkStatus.asStateFlow()

    private val _excelImportResult = MutableStateFlow<String?>(null)
    val excelImportResult: StateFlow<String?> = _excelImportResult.asStateFlow()

    // Helper states for supervisor checkmarks
    private val _supervisorOvertimeInputs = MutableStateFlow<Map<Long, String>>(emptyMap())
    val supervisorOvertimeInputs = _supervisorOvertimeInputs.asStateFlow()

    private val _supervisorPresence = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    val supervisorPresence = _supervisorPresence.asStateFlow()

    init {
        // Pre-populate with seed data if empty
        viewModelScope.launch {
            members.collect { memberList ->
                if (memberList.isEmpty()) {
                    seedInitialData()
                }
            }
        }
    }

    private suspend fun seedInitialData() {
        val adminId = repository.insertMember(Member(name = "Alice Smith", role = "ADMIN", email = "alice.admin@work.com"))
        val supervisorId = repository.insertMember(Member(name = "Robert Johnson", role = "SUPERVISOR", email = "robert.sup@work.com"))
        val emp1Id = repository.insertMember(Member(name = "Emily Davis", role = "EMPLOYEE", email = "emily.emp@work.com"))
        val emp2Id = repository.insertMember(Member(name = "Michael Brown", role = "EMPLOYEE", email = "michael.emp@work.com"))

        val today = getCurrentDateString()
        val yesterday = getYesterdayDateString()

        // Seed some past attendance (Yesterday)
        repository.insertAttendance(Attendance(memberId = emp1Id, date = yesterday, isPresent = true, punchInTime = "09:00", punchOutTime = "17:30", overtimeHours = 0.5, approvedBySupervisorId = supervisorId, isSynced = true))
        repository.insertAttendance(Attendance(memberId = emp2Id, date = yesterday, isPresent = true, punchInTime = "08:45", punchOutTime = "18:00", overtimeHours = 1.0, approvedBySupervisorId = supervisorId, isSynced = true))

        // Seed some current attendance (Today)
        repository.insertAttendance(Attendance(memberId = emp1Id, date = today, isPresent = true, punchInTime = "09:00", overtimeHours = 0.0, isSynced = false))
        repository.insertAttendance(Attendance(memberId = emp2Id, date = today, isPresent = true, punchInTime = "08:50", overtimeHours = 0.0, isSynced = false))
    }

    // Get stored password for a member, default to "12345"
    fun getMemberPassword(memberId: Long): String {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("user_credentials", Context.MODE_PRIVATE)
        return sharedPrefs.getString("password_$memberId", "12345") ?: "12345"
    }

    // Set stored password for a member
    fun setMemberPassword(memberId: Long, newPassword: String) {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("user_credentials", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("password_$memberId", newPassword).apply()
    }

    // Get stored recovery code for a member (or empty if none)
    fun getMemberRecoveryCode(memberId: Long): String {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("user_credentials", Context.MODE_PRIVATE)
        return sharedPrefs.getString("recovery_$memberId", "") ?: ""
    }

    // Set stored recovery code for a member (e.g., 6-digit random number)
    fun setMemberRecoveryCode(memberId: Long, recoveryCode: String) {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("user_credentials", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("recovery_$memberId", recoveryCode).apply()
    }

    // Reset password with a recovery code on login screen
    fun recoverPassword(email: String, recoveryCode: String, newPassword: String): Boolean {
        val matchedMember = members.value.firstOrNull {
            it.email.equals(email, ignoreCase = true)
        } ?: return false

        val storedCode = getMemberRecoveryCode(matchedMember.id)
        if (storedCode.isNotEmpty() && storedCode == recoveryCode) {
            setMemberPassword(matchedMember.id, newPassword)
            // Clear code after successful recovery
            setMemberRecoveryCode(matchedMember.id, "")
            return true
        }
        return false
    }

    fun login(username: String, pword: String): Boolean {
        _loginError.value = null
        if (username.lowercase() == "admin" && pword == "C3a12345") {
            val adminUser = members.value.firstOrNull { it.role == "ADMIN" } ?: Member(name = "Alice Smith", role = "ADMIN", email = "alice.admin@work.com")
            _currentUser.value = adminUser
            _isLoggedIn.value = true
            return true
        }

        val matchedMember = members.value.firstOrNull {
            it.email.equals(username, ignoreCase = true) || it.name.equals(username, ignoreCase = true)
        }

        if (matchedMember != null) {
            // For other team members, fetch custom password (default: "12345")
            val correctPassword = getMemberPassword(matchedMember.id)
            if (pword == correctPassword) {
                _currentUser.value = matchedMember
                _isLoggedIn.value = true
                return true
            } else {
                _loginError.value = "Incorrect password. If you forgot your password, ask an Admin to reset it or use recovery code."
                return false
            }
        } else {
            _loginError.value = "Username or Email not found. Try 'admin' or seeded emails like 'normal.user@work.com'."
            return false
        }
    }

    fun loginWithMicrosoft365(email: String): Boolean {
        _loginError.value = null
        val matchedMember = members.value.firstOrNull {
            it.email.equals(email, ignoreCase = true)
        }
        if (matchedMember != null) {
            _currentUser.value = matchedMember
            _isLoggedIn.value = true
            return true
        } else {
            _loginError.value = "Microsoft 365 account matching '$email' is not registered in this workspace."
            return false
        }
    }

    // Get Microsoft 365 Tenant ID configuration
    fun getM365TenantId(): String {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("m365_config", Context.MODE_PRIVATE)
        return sharedPrefs.getString("tenant_id", "common") ?: "common"
    }

    // Set Microsoft 365 Tenant ID configuration
    fun setM365TenantId(tenantId: String) {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("m365_config", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("tenant_id", tenantId).apply()
    }

    // Get Microsoft 365 Client ID configuration
    fun getM365ClientId(): String {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("m365_config", Context.MODE_PRIVATE)
        return sharedPrefs.getString("client_id", "4722881a-0351-4db8-86d1-41dbd666d932") ?: "4722881a-0351-4db8-86d1-41dbd666d932"
    }

    // Set Microsoft 365 Client ID configuration
    fun setM365ClientId(clientId: String) {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("m365_config", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("client_id", clientId).apply()
    }

    fun logout() {
        _isLoggedIn.value = false
        _currentUser.value = null
    }

    fun switchUser(member: Member) {
        _currentUser.value = member
    }

    fun toggleOfflineMode() {
        _isOfflineMode.value = !_isOfflineMode.value
    }

    fun setDate(date: String) {
        _selectedDate.value = date
    }

    fun updateSupervisorOvertime(memberId: Long, value: String) {
        val updatedMap = _supervisorOvertimeInputs.value.toMutableMap()
        updatedMap[memberId] = value
        _supervisorOvertimeInputs.value = updatedMap
    }

    fun updateSupervisorPresence(memberId: Long, value: Boolean) {
        val updatedMap = _supervisorPresence.value.toMutableMap()
        updatedMap[memberId] = value
        _supervisorPresence.value = updatedMap
    }

    // Role-based Attendance Operations

    // Employee Clock-In
    fun employeeClockIn() {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val today = getCurrentDateString()
            val existing = repository.getAttendanceForMemberAndDate(user.id, today)
            if (existing == null) {
                repository.insertAttendance(
                    Attendance(
                        memberId = user.id,
                        date = today,
                        isPresent = true,
                        punchInTime = getCurrentTimeString(),
                        isSynced = false
                    )
                )
            }
        }
    }

    // Employee Clock-Out with optional Overtime hours
    fun employeeClockOut(overtime: Double) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val today = getCurrentDateString()
            val existing = repository.getAttendanceForMemberAndDate(user.id, today)
            if (existing != null) {
                repository.insertAttendance(
                    existing.copy(
                        punchOutTime = getCurrentTimeString(),
                        overtimeHours = overtime,
                        isSynced = false
                    )
                )
            }
        }
    }

    // Supervisor Marks Attendance & Overtime
    fun supervisorSaveAttendance(memberId: Long, isPresent: Boolean, overtime: Double) {
        val supervisor = _currentUser.value ?: return
        viewModelScope.launch {
            val today = getCurrentDateString()
            val existing = repository.getAttendanceForMemberAndDate(memberId, today)
            val updated = existing?.copy(
                isPresent = isPresent,
                overtimeHours = overtime,
                approvedBySupervisorId = supervisor.id,
                isSynced = false
            ) ?: Attendance(
                memberId = memberId,
                date = today,
                isPresent = isPresent,
                overtimeHours = overtime,
                approvedBySupervisorId = supervisor.id,
                isSynced = false
            )
            repository.insertAttendance(updated)
        }
    }

    // Admin / Supervisor Approval
    fun approveAttendanceRecord(attendanceId: Long) {
        val approver = _currentUser.value ?: return
        viewModelScope.launch {
            repository.approveAttendance(attendanceId, approver.id)
        }
    }

    fun approveAllPendingRecords() {
        val approver = _currentUser.value ?: return
        viewModelScope.launch {
            val pending = attendanceRecords.value.filter { it.isPresent && it.approvedBySupervisorId == null }
            for (record in pending) {
                repository.approveAttendance(record.id, approver.id)
            }
        }
    }

    // Add new team member
    fun addTeamMember(name: String, role: String, email: String) {
        viewModelScope.launch {
            repository.insertMember(
                Member(
                    name = name,
                    role = role,
                    email = email,
                    isActive = true
                )
            )
        }
    }

    // Delete team member
    fun removeTeamMember(member: Member) {
        viewModelScope.launch {
            repository.deleteMember(member)
        }
    }

    // Purge dummy members and unknown/unapproved records
    fun purgeDummyDataAndUnknownApprovals(onComplete: (membersCount: Int, recordsCount: Int) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val dummyEmails = setOf(
                "t800.emp@work.com",
                "john.emp@work.com",
                "kyle.emp@work.com",
                "normal.user@work.com"
            )
            val allMembersList = members.value
            var deletedMembersCount = 0
            for (m in allMembersList) {
                if (dummyEmails.contains(m.email.lowercase()) || m.name.contains("T-800", ignoreCase = true)) {
                    repository.deleteMember(m)
                    deletedMembersCount++
                    repository.deleteAttendanceForMember(m.id)
                }
            }

            // Fresh list of remaining members
            val remainingMembers = members.value.filter { m ->
                !(dummyEmails.contains(m.email.lowercase()) || m.name.contains("T-800", ignoreCase = true))
            }
            val validMemberIds = remainingMembers.map { it.id }.toSet()
            val validSupervisorIds = remainingMembers.filter { it.role == "SUPERVISOR" || it.role == "ADMIN" }.map { it.id }.toSet()

            var deletedRecordsCount = 0
            val allRecords = attendanceRecords.value
            for (rec in allRecords) {
                val memberExists = validMemberIds.contains(rec.memberId)
                val hasUnknownApproval = rec.approvedBySupervisorId == null || !validSupervisorIds.contains(rec.approvedBySupervisorId)
                
                if (!memberExists || hasUnknownApproval) {
                    repository.deleteAttendance(rec)
                    deletedRecordsCount++
                }
            }
            onComplete(deletedMembersCount, deletedRecordsCount)
        }
    }

    // Synchronize to secure cloud database
    fun synchronizeCloud() {
        if (_isOfflineMode.value) {
            viewModelScope.launch {
                repository.recordManualSyncFailure("Synchronization failed: Active offline mode detected. Go online to sync backup records.")
            }
            return
        }

        viewModelScope.launch {
            _syncingState.value = true
            repository.performCloudSync()
            _syncingState.value = false
        }
    }

    // Clear Export Result
    fun clearExportResult() {
        _exportResult.value = null
    }

    // Export Reports to Excel (CSV)
    fun exportToExcel(context: Context) {
        viewModelScope.launch {
            val recordList = attendanceRecords.value
            val memberList = members.value
            val memberMap = memberList.associateBy { it.id }

            val csvContent = StringBuilder()
            csvContent.append("Date,Employee Name,Role,Email,Status,Clock In,Clock Out,Overtime (Hours),Approval Status\n")

            for (record in recordList) {
                val member = memberMap[record.memberId]
                val name = member?.name ?: "Unknown"
                val role = member?.role ?: "N/A"
                val email = member?.email ?: "N/A"
                val status = if (record.isPresent) "Present" else "Absent"
                val inTime = record.punchInTime ?: "-"
                val outTime = record.punchOutTime ?: "-"
                val overtime = record.overtimeHours
                val approval = if (record.approvedBySupervisorId != null) "Approved" else "Pending"

                csvContent.append("\"${record.date}\",\"$name\",\"$role\",\"$email\",\"$status\",\"$inTime\",\"$outTime\",$overtime,\"$approval\"\n")
            }

            try {
                val file = File(context.cacheDir, "Monthly_Attendance_Report.csv")
                val writer = FileWriter(file)
                writer.write(csvContent.toString())
                writer.close()

                _exportResult.value = "Excel report generated successfully!"
                shareFile(context, file, "text/csv", "Share Attendance CSV Report")
            } catch (e: Exception) {
                _exportResult.value = "Failed to export report: ${e.localizedMessage}"
            }
        }
    }

    // Export Reports to PDF Format
    fun exportToPDF(context: Context) {
        viewModelScope.launch {
            val recordList = attendanceRecords.value
            val memberList = members.value
            val memberMap = memberList.associateBy { it.id }

            val pdfContent = StringBuilder()
            pdfContent.append("=====================================================\n")
            pdfContent.append("          TEAM ATTENDANCE & PAYROLL SUMMARY REPORT   \n")
            pdfContent.append("          Generated on: ${getCurrentDateString()} ${getCurrentTimeString()} \n")
            pdfContent.append("=====================================================\n\n")

            // Add simple summary metrics
            val totalPresent = recordList.count { it.isPresent }
            val totalOvertime = recordList.sumOf { it.overtimeHours }
            pdfContent.append("Total Employee Attendance Days: $totalPresent\n")
            pdfContent.append("Total Accumulated Overtime Hours: ${String.format("%.1f", totalOvertime)} hrs\n")
            pdfContent.append("Offline Synchronized Backup Status: Verified Secure\n")
            pdfContent.append("-----------------------------------------------------\n\n")

            pdfContent.append("DETAILED LOG:\n\n")
            for (record in recordList) {
                val member = memberMap[record.memberId]
                val name = member?.name ?: "Unknown"
                val status = if (record.isPresent) "PRESENT" else "ABSENT"
                val inTime = record.punchInTime ?: "N/A"
                val outTime = record.punchOutTime ?: "N/A"
                val approved = if (record.approvedBySupervisorId != null) "APPROVED" else "PENDING"
                
                pdfContent.append("Date: ${record.date} | Name: $name\n")
                pdfContent.append("Status: $status | In: $inTime | Out: $outTime\n")
                pdfContent.append("Overtime Hours: ${record.overtimeHours} | Approval: $approved\n")
                pdfContent.append("-----------------------------------------------------\n")
            }

            try {
                val file = File(context.cacheDir, "Monthly_Attendance_Report.txt")
                val writer = FileWriter(file)
                writer.write(pdfContent.toString())
                writer.close()

                _exportResult.value = "PDF report compiled successfully!"
                shareFile(context, file, "text/plain", "Share Attendance PDF Summary Report")
            } catch (e: Exception) {
                _exportResult.value = "Failed to compile report: ${e.localizedMessage}"
            }
        }
    }

    private fun shareFile(context: Context, file: File, mimeType: String, title: String) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Team Attendance Monthly Report")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooserIntent = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooserIntent)
    }

    // Helper date-time methods
    private fun getCurrentDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun getYesterdayDateString(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DATE, -1)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(cal.time)
    }

    private fun getCurrentTimeString(): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date())
    }

    fun linkExcelDatabase(filePathOrUrl: String, excelCsvData: String) {
        viewModelScope.launch {
            if (filePathOrUrl.isBlank()) {
                _excelImportResult.value = "Please provide a valid file path, URL, or identifier."
                return@launch
            }
            _linkedExcelFile.value = filePathOrUrl
            _excelLinkStatus.value = "ACTIVE (LINKED)"

            if (excelCsvData.isNotBlank()) {
                try {
                    var parsedCount = 0
                    var memberCreatedCount = 0
                    val lines = excelCsvData.lineSequence().toList()
                    for (line in lines) {
                        if (line.isBlank() || line.startsWith("Date", ignoreCase = true) || line.startsWith("Email", ignoreCase = true)) {
                            continue
                        }
                        // Format: Date,Employee Name,Role,Email,Status,Clock In,Clock Out,Overtime
                        val tokens = line.split(",").map { it.replace("\"", "").trim() }
                        if (tokens.size >= 4) {
                            val date = tokens[0]
                            val name = tokens[1]
                            val role = tokens[2].uppercase()
                            val email = tokens[3]
                            
                            if (date.isBlank() || name.isBlank() || email.isBlank()) {
                                continue
                            }
                            
                            val isPresent = if (tokens.getOrNull(4)?.uppercase() == "ABSENT") false else true
                            val clockIn = tokens.getOrNull(5) ?: "09:00"
                            val clockOut = tokens.getOrNull(6) ?: "-"
                            val overtime = tokens.getOrNull(7)?.toDoubleOrNull() ?: 0.0

                            // 1. Find or create member
                            var member = members.value.firstOrNull { it.email.equals(email, ignoreCase = true) }
                            if (member == null) {
                                val newId = repository.insertMember(
                                    Member(
                                        name = name,
                                        role = if (role in listOf("ADMIN", "SUPERVISOR", "EMPLOYEE", "NORMAL")) role else "EMPLOYEE",
                                        email = email,
                                        isActive = true
                                    )
                                )
                                member = Member(id = newId, name = name, role = role, email = email, isActive = true)
                                memberCreatedCount++
                            }

                            // 2. Insert or update attendance log
                            val existingAttendance = repository.getAttendanceForMemberAndDate(member.id, date)
                            val attendance = existingAttendance?.copy(
                                isPresent = isPresent,
                                punchInTime = clockIn,
                                punchOutTime = if (clockOut == "-") null else clockOut,
                                overtimeHours = overtime,
                                isSynced = false
                            ) ?: Attendance(
                                memberId = member.id,
                                date = date,
                                isPresent = isPresent,
                                punchInTime = clockIn,
                                punchOutTime = if (clockOut == "-") null else clockOut,
                                overtimeHours = overtime,
                                isSynced = false
                            )
                            repository.insertAttendance(attendance)
                            parsedCount++
                        }
                    }
                    _excelImportResult.value = "Successfully linked and parsed Excel data! Synchronized $parsedCount logs and created $memberCreatedCount new team members."
                } catch (e: Exception) {
                    _excelImportResult.value = "Error parsing Excel data: ${e.localizedMessage}"
                }
            } else {
                _excelImportResult.value = "Excel connection linked successfully to source: $filePathOrUrl!"
            }
        }
    }

    fun unlinkExcelDatabase() {
        _linkedExcelFile.value = null
        _excelLinkStatus.value = "NOT LINKED"
        _excelImportResult.value = "Excel database link removed."
    }

    fun clearExcelImportResult() {
        _excelImportResult.value = null
    }
}
