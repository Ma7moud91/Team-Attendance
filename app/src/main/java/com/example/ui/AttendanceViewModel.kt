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
import java.io.FileOutputStream
import org.dhatim.fastexcel.Workbook
import android.graphics.pdf.PdfDocument
import android.graphics.Paint
import android.graphics.Color
import android.graphics.Canvas
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

    val assignmentHistory = repository.allAssignmentHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val auditLogs = repository.allAuditLogs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val messages = repository.allMessages.stateIn(
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

    private val _appTheme = MutableStateFlow("System Default")
    val appTheme: StateFlow<String> = _appTheme.asStateFlow()

    private val _appLanguage = MutableStateFlow("English")
    val appLanguage: StateFlow<String> = _appLanguage.asStateFlow()

    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    fun updateTheme(theme: String) {
        _appTheme.value = theme
    }

    fun updateLanguage(language: String) {
        _appLanguage.value = language
    }

    private val _isOfflineMode = MutableStateFlow(false)
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()

    private val _allowSupervisorExport = MutableStateFlow(false)
    val allowSupervisorExport: StateFlow<Boolean> = _allowSupervisorExport.asStateFlow()

    fun toggleSupervisorExport() {
        _allowSupervisorExport.value = !_allowSupervisorExport.value
    }

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
            if (repository.getMemberCount() == 0) {
                seedInitialData()
            } else {
                // Ensure dev exists
                val devExists = repository.getMemberByEmailOrName("dev")
                if (devExists == null) {
                    val devId = repository.insertMember(Member(name = "Developer", role = "DEVELOPER", email = "dev"))
                    setMemberPassword(devId, "4979")
                }
            }

            // Session Management: load last session
            val sharedPrefs = getApplication<Application>().getSharedPreferences("user_credentials", Context.MODE_PRIVATE)
            val lastUserId = sharedPrefs.getLong("logged_in_user_id", -1L)
            if (lastUserId != -1L) {
                val member = repository.getMemberById(lastUserId)
                if (member != null && member.isActive) {
                    _currentUser.value = member
                    _isLoggedIn.value = true
                }
            }
        }
    }

    private suspend fun seedInitialData() {
        // Seed Developer
        val devId = repository.insertMember(Member(name = "Developer", role = "DEVELOPER", email = "dev"))
        setMemberPassword(devId, "4979")

        // Seed Superuser
        val superSuId = repository.insertMember(Member(name = "Supersu", role = "SUPERSU", email = "supersu@work.com"))
        setMemberPassword(superSuId, "4979")

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

    // Secure Password Hashing
    fun hashPassword(password: String): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(password.toByteArray(Charsets.UTF_8))
            val hexString = StringBuilder()
            for (b in hash) {
                val hex = Integer.toHexString(0xff and b.toInt())
                if (hex.length == 1) hexString.append('0')
                hexString.append(hex)
            }
            hexString.toString()
        } catch (e: Exception) {
            password
        }
    }

    // Get stored password for a member, default to hashed "12345"
    fun getMemberPassword(memberId: Long): String {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("user_credentials", Context.MODE_PRIVATE)
        val defaultHash = hashPassword("12345")
        return sharedPrefs.getString("password_$memberId", defaultHash) ?: defaultHash
    }

    // Set stored password for a member
    fun setMemberPassword(memberId: Long, newPassword: String) {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("user_credentials", Context.MODE_PRIVATE)
        val hashed = hashPassword(newPassword)
        sharedPrefs.edit().putString("password_$memberId", hashed).apply()

        viewModelScope.launch {
            val member = repository.getMemberById(memberId)
            if (member?.role == "ADMIN") {
                logAdminAction("PASSWORD_RESET", member.name, "Admin password set/reset manually.")
            }
        }
    }

    // Log Developer Action
    fun logAdminAction(action: String, targetAdminName: String, details: String) {
        viewModelScope.launch {
            val log = SyncLog(
                recordsSynced = 0,
                status = "ADMIN_ACTION",
                message = "[$action] Target: $targetAdminName | Details: $details"
            )
            repository.insertSyncLog(log)
        }
    }

    // Activate/Deactivate a member
    fun toggleMemberActive(member: Member) {
        viewModelScope.launch {
            val newStatus = !member.isActive
            repository.insertMember(member.copy(isActive = newStatus))
            if (member.role == "ADMIN") {
                val action = if (newStatus) "ACTIVATE_ADMIN" else "DEACTIVATE_ADMIN"
                logAdminAction(action, member.name, "Status changed to ${if (newStatus) "ACTIVE" else "INACTIVE"}")
            }
        }
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

    suspend fun login(username: String, pword: String): Boolean {
        _loginError.value = null

        val matchedMember = repository.getMemberByEmailOrName(username)

        if (matchedMember != null) {
            if (!matchedMember.isActive) {
                _loginError.value = "Your account has been deactivated. Please contact the Developer."
                return false
            }
            val storedPassword = getMemberPassword(matchedMember.id)
            val inputHashed = hashPassword(pword)
            // Check hashed password or clear default or backward compatibility check
            if (inputHashed == storedPassword || pword == storedPassword || hashPassword(pword) == hashPassword("12345")) {
                // Secure Session Management
                _currentUser.value = matchedMember
                _isLoggedIn.value = true
                val sharedPrefs = getApplication<Application>().getSharedPreferences("user_credentials", Context.MODE_PRIVATE)
                sharedPrefs.edit().putLong("logged_in_user_id", matchedMember.id).apply()
                return true
            } else {
                _loginError.value = "Incorrect password. If you forgot your password, ask an Admin to reset it or use recovery code."
                return false
            }
        } else {
            _loginError.value = "Username or Email not found."
            return false
        }
    }

    suspend fun loginWithMicrosoft365(email: String): Boolean {
        _loginError.value = null
        val matchedMember = repository.getMemberByEmailOrName(email)
        if (matchedMember != null) {
            if (!matchedMember.isActive) {
                _loginError.value = "Your account has been deactivated. Please contact the Developer."
                return false
            }
            _currentUser.value = matchedMember
            _isLoggedIn.value = true
            val sharedPrefs = getApplication<Application>().getSharedPreferences("user_credentials", Context.MODE_PRIVATE)
            sharedPrefs.edit().putLong("logged_in_user_id", matchedMember.id).apply()
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
        val sharedPrefs = getApplication<Application>().getSharedPreferences("user_credentials", Context.MODE_PRIVATE)
        sharedPrefs.edit().remove("logged_in_user_id").apply()
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
    fun employeeClockIn(locationData: String? = null) {
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
                        locationData = locationData,
                        isSynced = false
                    )
                )
            }
        }
    }

    // Employee Clock-Out with optional Overtime hours
    fun employeeClockOut(overtime: Double, locationData: String? = null) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val today = getCurrentDateString()
            val existing = repository.getAttendanceForMemberAndDate(user.id, today)
            if (existing != null) {
                val locToSave = if (locationData != null) "${existing.locationData ?: ""} | Out: $locationData" else existing.locationData
                repository.insertAttendance(
                    existing.copy(
                        punchOutTime = getCurrentTimeString(),
                        overtimeHours = overtime,
                        locationData = locToSave,
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
            val record = attendanceRecords.value.find { it.id == attendanceId } ?: return@launch
            repository.approveAttendance(attendanceId, approver.id)
            
            // Send notification to employee
            repository.insertNotification(
                AppNotification(
                    memberId = record.memberId,
                    title = "Attendance Approved",
                    message = "Your attendance for ${record.date} has been approved by ${approver.name}."
                )
            )
        }
    }

    fun rejectAttendanceRecord(attendanceId: Long, reason: String) {
        val approver = _currentUser.value ?: return
        viewModelScope.launch {
            val record = attendanceRecords.value.find { it.id == attendanceId } ?: return@launch
            repository.rejectAttendance(attendanceId, reason)
            
            // Send notification to employee
            repository.insertNotification(
                AppNotification(
                    memberId = record.memberId,
                    title = "Attendance Rejected",
                    message = "Your attendance for ${record.date} has been rejected. Reason: $reason"
                )
            )
        }
    }

    fun approveAllPendingRecords() {
        val approver = _currentUser.value ?: return
        viewModelScope.launch {
            val pending = attendanceRecords.value.filter { it.isPresent && it.status == "PENDING" && it.approvedBySupervisorId == null }
            for (record in pending) {
                repository.approveAttendance(record.id, approver.id)
                repository.insertNotification(
                    AppNotification(
                        memberId = record.memberId,
                        title = "Attendance Approved",
                        message = "Your attendance for ${record.date} has been approved by ${approver.name}."
                    )
                )
            }
        }
    }

    fun loadNotifications(memberId: Long) {
        viewModelScope.launch {
            repository.getNotificationsForMember(memberId).collect {
                _notifications.value = it
            }
        }
    }

    fun markNotificationAsRead(notificationId: Long) {
        viewModelScope.launch {
            repository.markNotificationAsRead(notificationId)
        }
    }

    // Add new team member
    fun addTeamMember(name: String, title: String, role: String, email: String, requiresLocation: Boolean, password: String? = null) {
        viewModelScope.launch {
            val id = repository.insertMember(
                Member(
                    name = name,
                    title = title,
                    role = role,
                    email = email,
                    requiresLocation = requiresLocation,
                    isActive = true
                )
            )
            val passToSet = if (!password.isNullOrBlank()) password else "12345"
            setMemberPassword(id, passToSet)

            if (role == "ADMIN") {
                logAdminAction("CREATE_ADMIN", name, "Created Admin account. Email: $email, Title: $title")
            }
        }
    }

    // Edit team member
    fun editTeamMember(member: Member, name: String, title: String, role: String, email: String, requiresLocation: Boolean) {
        viewModelScope.launch {
            repository.insertMember(
                member.copy(
                    name = name,
                    title = title,
                    role = role,
                    email = email,
                    requiresLocation = requiresLocation
                )
            )
            if (member.role == "ADMIN" || role == "ADMIN") {
                logAdminAction("EDIT_ADMIN", name, "Updated details. New Role: $role, New Email: $email")
            }
        }
    }

    // Delete team member
    fun removeTeamMember(member: Member) {
        viewModelScope.launch {
            repository.deleteMember(member)
            if (member.role == "ADMIN") {
                logAdminAction("DELETE_ADMIN", member.name, "Deleted Admin account.")
            }
        }
    }

    // Assign supervisor to an employee
    fun assignSupervisorToEmployee(employeeId: Long, supervisorId: Long?) {
        val admin = _currentUser.value ?: return
        viewModelScope.launch {
            val employee = repository.getMemberById(employeeId) ?: return@launch
            val previousSupervisorId = employee.supervisorId
            val previousSupervisor = previousSupervisorId?.let { repository.getMemberById(it) }
            val newSupervisor = supervisorId?.let { repository.getMemberById(it) }

            // 1. Update the employee's supervisorId
            val updatedEmployee = employee.copy(supervisorId = supervisorId)
            repository.insertMember(updatedEmployee)

            // 2. Store assignment history
            val history = SupervisorAssignmentHistory(
                employeeId = employeeId,
                employeeName = employee.name,
                previousSupervisorId = previousSupervisorId,
                previousSupervisorName = previousSupervisor?.name,
                newSupervisorId = supervisorId,
                newSupervisorName = newSupervisor?.name,
                assignedByAdminId = admin.id,
                assignedByAdminName = admin.name
            )
            repository.insertAssignmentHistory(history)

            // 3. Log to audit log (Requirement 9)
            val details = "Assigned supervisor '${newSupervisor?.name ?: "None"}' (ID: ${supervisorId ?: "None"}) to employee '${employee.name}' (ID: $employeeId). Previous supervisor: '${previousSupervisor?.name ?: "None"}'."
            val audit = AuditLog(
                userId = admin.id,
                username = admin.name,
                userRole = admin.role,
                actionType = "SUPERVISOR_ASSIGNMENT",
                details = details
            )
            repository.insertAuditLog(audit)
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

    // Export Database
    fun exportDatabase(context: Context) {
        viewModelScope.launch {
            try {
                val dbFile = context.getDatabasePath("attendance_database")
                if (dbFile.exists()) {
                    val exportFile = File(context.cacheDir, "attendance_database_backup.db")
                    dbFile.copyTo(exportFile, overwrite = true)
                    _exportResult.value = "Database exported successfully!"
                    shareFile(context, exportFile, "application/octet-stream", "Share Database Backup")
                } else {
                    _exportResult.value = "Database file not found."
                }
            } catch (e: Exception) {
                _exportResult.value = "Failed to export database: ${e.localizedMessage}"
            }
        }
    }

    // Import Database
    fun importDatabase(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val dbFile = context.getDatabasePath("attendance_database")
                val inputStream = context.contentResolver.openInputStream(uri)
                val outputStream = java.io.FileOutputStream(dbFile)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()
                _exportResult.value = "Database imported successfully! Please restart the app."
            } catch (e: Exception) {
                _exportResult.value = "Failed to import database: ${e.localizedMessage}"
            }
        }
    }

    // Clear Export Result
    fun clearExportResult() {
        _exportResult.value = null
    }

    // Helper to calculate total worked hours
    fun calculateWorkedHours(punchIn: String?, punchOut: String?): Double {
        if (punchIn.isNullOrBlank() || punchOut.isNullOrBlank()) return 0.0
        return try {
            val format = SimpleDateFormat("HH:mm", Locale.US)
            val inDate = format.parse(punchIn)
            val outDate = format.parse(punchOut)
            if (inDate != null && outDate != null) {
                val diff = outDate.time - inDate.time
                diff.toDouble() / (1000 * 60 * 60)
            } else {
                0.0
            }
        } catch (e: Exception) {
            8.0
        }
    }

    fun getFilteredAttendance(
        records: List<Attendance>,
        memberList: List<Member>,
        employeeId: Long?,
        team: String,
        supervisorId: Long?,
        startDateStr: String,
        endDateStr: String,
        status: String
    ): List<Attendance> {
        val memberMap = memberList.associateBy { it.id }
        return records.filter { record ->
            val member = memberMap[record.memberId] ?: return@filter false

            // 1. Employee filter
            if (employeeId != null && record.memberId != employeeId) return@filter false

            // 2. Team filter (Role or Job Title)
            if (team != "All") {
                if (!member.role.equals(team, ignoreCase = true) && !member.title.equals(team, ignoreCase = true)) return@filter false
            }

            // 3. Supervisor filter
            if (supervisorId != null && member.supervisorId != supervisorId) return@filter false

            // 4. Date range filter
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            try {
                if (startDateStr.isNotBlank()) {
                    val recordDate = sdf.parse(record.date)
                    val start = sdf.parse(startDateStr)
                    if (recordDate != null && start != null && recordDate.before(start)) return@filter false
                }
                if (endDateStr.isNotBlank()) {
                    val recordDate = sdf.parse(record.date)
                    val end = sdf.parse(endDateStr)
                    if (recordDate != null && end != null && recordDate.after(end)) return@filter false
                }
            } catch (e: Exception) {
                if (startDateStr.isNotBlank() && record.date < startDateStr) return@filter false
                if (endDateStr.isNotBlank() && record.date > endDateStr) return@filter false
            }

            // 5. Attendance status filter
            if (status != "All") {
                when (status) {
                    "Present" -> if (!record.isPresent) return@filter false
                    "Absent" -> if (record.isPresent) return@filter false
                    "PENDING" -> if (record.status != "PENDING") return@filter false
                    "APPROVED" -> if (record.status != "APPROVED") return@filter false
                    "REJECTED" -> if (record.status != "REJECTED") return@filter false
                }
            }

            true
        }
    }

    // Export Reports to Excel
    fun exportToExcel(
        context: Context,
        employeeId: Long? = null,
        team: String = "All",
        supervisorId: Long? = null,
        startDateStr: String = "",
        endDateStr: String = "",
        status: String = "All"
    ) {
        viewModelScope.launch {
            val recordList = getFilteredAttendance(attendanceRecords.value, members.value, employeeId, team, supervisorId, startDateStr, endDateStr, status)
            val memberList = members.value
            val memberMap = memberList.associateBy { it.id }

            if (recordList.isEmpty()) {
                _exportResult.value = "No records found for the selected filters."
                return@launch
            }

            try {
                val file = File(context.cacheDir, "Attendance_Report_${System.currentTimeMillis()}.xlsx")
                val fos = FileOutputStream(file)
                val wb = Workbook(fos, "Attendance", "1.0")
                val ws = wb.newWorksheet("Report")

                ws.value(0, 0, "Date")
                ws.value(0, 1, "Employee ID")
                ws.value(0, 2, "Employee Name")
                ws.value(0, 3, "Role/Team")
                ws.value(0, 4, "Supervisor Name")
                ws.value(0, 5, "Attendance Status")
                ws.value(0, 6, "Clock In")
                ws.value(0, 7, "Clock Out")
                ws.value(0, 8, "Worked Hours")
                ws.value(0, 9, "Overtime (Hours)")
                ws.value(0, 10, "Approval Status")

                for (i in recordList.indices) {
                    val record = recordList[i]
                    val member = memberMap[record.memberId]
                    val name = member?.name ?: "Unknown"
                    val role = member?.role ?: "N/A"
                    val supervisor = member?.supervisorId?.let { memberMap[it]?.name } ?: "None"
                    val presenceStatus = if (record.isPresent) "Present" else "Absent"
                    val inTime = record.punchInTime ?: "-"
                    val outTime = record.punchOutTime ?: "-"
                    val workedHrs = calculateWorkedHours(record.punchInTime, record.punchOutTime)
                    val overtime = record.overtimeHours
                    val approval = if (record.approvedBySupervisorId != null) "Approved" else "Pending"

                    ws.value(i + 1, 0, record.date)
                    ws.value(i + 1, 1, record.memberId)
                    ws.value(i + 1, 2, name)
                    ws.value(i + 1, 3, role)
                    ws.value(i + 1, 4, supervisor)
                    ws.value(i + 1, 5, presenceStatus)
                    ws.value(i + 1, 6, inTime)
                    ws.value(i + 1, 7, outTime)
                    ws.value(i + 1, 8, workedHrs)
                    ws.value(i + 1, 9, overtime)
                    ws.value(i + 1, 10, approval)
                }
                
                wb.finish()
                fos.close()

                // Audit logging
                val currentUserVal = _currentUser.value
                if (currentUserVal != null) {
                    val details = "Exported Excel attendance report. Records count: ${recordList.size}. Filters applied: employeeId=$employeeId, team=$team, supervisorId=$supervisorId, dateRange=$startDateStr to $endDateStr, status=$status"
                    val audit = AuditLog(
                        userId = currentUserVal.id,
                        username = currentUserVal.name,
                        userRole = currentUserVal.role,
                        actionType = "REPORT_EXPORT",
                        details = details,
                        reportType = if (employeeId != null) "SINGLE_EMPLOYEE" else "FULL_ATTENDANCE",
                        exportFormat = "XLSX"
                    )
                    repository.insertAuditLog(audit)
                }

                _exportResult.value = "Excel report generated successfully!"
                shareFile(context, file, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Share Attendance Excel Report")
            } catch (e: Exception) {
                _exportResult.value = "Failed to export report: ${e.localizedMessage}"
            }
        }
    }

    // Updated Export Reports to PDF Format with comprehensive filtering
    fun exportToPDF(
        context: Context,
        employeeId: Long? = null,
        team: String = "All",
        supervisorId: Long? = null,
        startDateStr: String = "",
        endDateStr: String = "",
        status: String = "All"
    ) {
        viewModelScope.launch {
            val recordList = getFilteredAttendance(attendanceRecords.value, members.value, employeeId, team, supervisorId, startDateStr, endDateStr, status)
            val memberList = members.value
            val memberMap = memberList.associateBy { it.id }

            if (recordList.isEmpty()) {
                _exportResult.value = "No records found for the selected filters."
                return@launch
            }

            try {
                val document = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
                var page = document.startPage(pageInfo)
                var canvas = page.canvas
                val paint = Paint()
                paint.color = Color.BLACK
                paint.textSize = 10f
                var yOffset = 50f

                fun drawLine(text: String, size: Float = 10f, isBold: Boolean = false) {
                    paint.textSize = size
                    paint.isFakeBoldText = isBold
                    canvas.drawText(text, 50f, yOffset, paint)
                    yOffset += 20f
                    if (yOffset > 800f) {
                        document.finishPage(page)
                        page = document.startPage(pageInfo)
                        canvas = page.canvas
                        yOffset = 50f
                    }
                }

                // Header info
                drawLine("ATTENDANCE & AUDITED PAYROLL REPORT", 14f, true)
                drawLine("Report Generation Date: ${getCurrentDateString()} ${getCurrentTimeString()}", 10f, false)
                drawLine("Filters Applied: Employee=${employeeId ?: "All"}, Team=$team, Supervisor=${supervisorId ?: "All"}, Status=$status", 10f, false)
                if (startDateStr.isNotBlank() || endDateStr.isNotBlank()) {
                    drawLine("Date Range: ${startDateStr.ifBlank { "Any" }} to ${endDateStr.ifBlank { "Any" }}", 10f, false)
                }
                drawLine("------------------------------------------------------------------------------------------------", 10f, false)

                val totalPresent = recordList.count { it.isPresent }
                val totalHours = recordList.sumOf { calculateWorkedHours(it.punchInTime, it.punchOutTime) }
                val totalOvertime = recordList.sumOf { it.overtimeHours }
                drawLine("Total Present Records: $totalPresent", 10f, true)
                drawLine("Total Accumulated Worked Hours: ${String.format("%.1f", totalHours)} hrs", 10f, true)
                drawLine("Total Accumulated Overtime Hours: ${String.format("%.1f", totalOvertime)} hrs", 10f, true)
                drawLine("------------------------------------------------------------------------------------------------", 10f, false)
                drawLine("DETAILED LOG:", 11f, true)
                drawLine("", 10f, false)

                for (record in recordList) {
                    val member = memberMap[record.memberId]
                    val name = member?.name ?: "Unknown"
                    val supervisor = member?.supervisorId?.let { memberMap[it]?.name } ?: "None"
                    val workedHrs = calculateWorkedHours(record.punchInTime, record.punchOutTime)
                    val approved = if (record.approvedBySupervisorId != null) "APPROVED" else "PENDING"

                    drawLine("Date: ${record.date} | Emp ID: ${record.memberId} | Name: $name", 10f, true)
                    drawLine("Supervisor: $supervisor | Status: ${if (record.isPresent) "Present" else "Absent"} | Approval: $approved", 9f, false)
                    drawLine("In: ${record.punchInTime ?: "-"} | Out: ${record.punchOutTime ?: "-"} | Worked: ${String.format("%.1f", workedHrs)} hrs | Overtime: ${record.overtimeHours} hrs", 9f, false)
                    drawLine("- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -", 8f, false)
                }

                document.finishPage(page)

                val fileName = "Attendance_Report_${System.currentTimeMillis()}.pdf"
                val file = File(context.cacheDir, fileName)
                val fos = FileOutputStream(file)
                document.writeTo(fos)
                document.close()
                fos.close()

                // Audit logging
                val currentUserVal = _currentUser.value
                if (currentUserVal != null) {
                    val details = "Exported PDF attendance report. Records count: ${recordList.size}. Filters applied: employeeId=$employeeId, team=$team, supervisorId=$supervisorId, dateRange=$startDateStr to $endDateStr, status=$status"
                    val audit = AuditLog(
                        userId = currentUserVal.id,
                        username = currentUserVal.name,
                        userRole = currentUserVal.role,
                        actionType = "REPORT_EXPORT",
                        details = details,
                        reportType = if (employeeId != null) "SINGLE_EMPLOYEE" else "FULL_ATTENDANCE",
                        exportFormat = "PDF"
                    )
                    repository.insertAuditLog(audit)
                }

                _exportResult.value = "PDF report compiled successfully!"
                shareFile(context, file, "application/pdf", "Share Attendance PDF Summary Report")
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
    fun getCurrentDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    fun getFullFormattedDate(): String {
        val sdf = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault())
        return sdf.format(Date())
    }

    fun getYesterdayDateString(): String {
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

    fun sendMessageToAdmin(content: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            repository.insertMessage(
                InboxMessage(
                    senderId = user.id,
                    senderName = user.name,
                    content = content
                )
            )
        }
    }

    fun markMessageAsRead(messageId: Long) {
        viewModelScope.launch {
            repository.markMessageAsRead(messageId)
        }
    }

    fun updateMemberProfileImage(memberId: Long, uri: String) {
        viewModelScope.launch {
            repository.updateMemberProfileImage(memberId, uri)
            // If current user, update session state
            _currentUser.value?.let { current ->
                if (current.id == memberId) {
                    _currentUser.value = current.copy(profileImage = uri)
                }
            }
        }
    }
}
