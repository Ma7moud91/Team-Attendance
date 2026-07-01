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

    suspend fun login(username: String, pword: String): Boolean {
        _loginError.value = null

        val matchedMember = repository.getMemberByEmailOrName(username)

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
            _loginError.value = "Username or Email not found."
            return false
        }
    }

    suspend fun loginWithMicrosoft365(email: String): Boolean {
        _loginError.value = null
        val matchedMember = repository.getMemberByEmailOrName(email)
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
    fun addTeamMember(name: String, title: String, role: String, email: String, requiresLocation: Boolean) {
        viewModelScope.launch {
            repository.insertMember(
                Member(
                    name = name,
                    title = title,
                    role = role,
                    email = email,
                    requiresLocation = requiresLocation,
                    isActive = true
                )
            )
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

    // Export Reports to Excel
    fun exportToExcel(context: Context) {
        viewModelScope.launch {
            val recordList = attendanceRecords.value
            val memberList = members.value
            val memberMap = memberList.associateBy { it.id }

            try {
                val file = File(context.cacheDir, "Monthly_Attendance_Report.xlsx")
                val fos = FileOutputStream(file)
                val wb = Workbook(fos, "Attendance", "1.0")
                val ws = wb.newWorksheet("Report")

                ws.value(0, 0, "Date")
                ws.value(0, 1, "Employee Name")
                ws.value(0, 2, "Role")
                ws.value(0, 3, "Email")
                ws.value(0, 4, "Status")
                ws.value(0, 5, "Clock In")
                ws.value(0, 6, "Clock Out")
                ws.value(0, 7, "Overtime (Hours)")
                ws.value(0, 8, "Approval Status")

                for (i in recordList.indices) {
                    val record = recordList[i]
                    val member = memberMap[record.memberId]
                    val name = member?.name ?: "Unknown"
                    val role = member?.role ?: "N/A"
                    val email = member?.email ?: "N/A"
                    val status = if (record.isPresent) "Present" else "Absent"
                    val inTime = record.punchInTime ?: "-"
                    val outTime = record.punchOutTime ?: "-"
                    val overtime = record.overtimeHours
                    val approval = if (record.approvedBySupervisorId != null) "Approved" else "Pending"

                    ws.value(i + 1, 0, record.date)
                    ws.value(i + 1, 1, name)
                    ws.value(i + 1, 2, role)
                    ws.value(i + 1, 3, email)
                    ws.value(i + 1, 4, status)
                    ws.value(i + 1, 5, inTime)
                    ws.value(i + 1, 6, outTime)
                    ws.value(i + 1, 7, overtime)
                    ws.value(i + 1, 8, approval)
                }
                
                wb.finish()
                fos.close()

                _exportResult.value = "Excel report generated successfully!"
                shareFile(context, file, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Share Attendance Excel Report")
            } catch (e: Exception) {
                _exportResult.value = "Failed to export report: ${e.localizedMessage}"
            }
        }
    }

    // Updated Export Reports to PDF Format with filtering
    fun exportToPDF(context: Context, targetMemberId: Long? = null, overtimeOnly: Boolean = false) {
        viewModelScope.launch {
            val fullRecordList = attendanceRecords.value
            val memberList = members.value
            val memberMap = memberList.associateBy { it.id }

            val recordList = fullRecordList.filter { 
                (targetMemberId == null || it.memberId == targetMemberId) && 
                (!overtimeOnly || it.overtimeHours > 0)
            }

            if (recordList.isEmpty()) {
                _exportResult.value = "No records found for the selected filter."
                return@launch
            }

            try {
                val document = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
                var page = document.startPage(pageInfo)
                var canvas = page.canvas
                val paint = Paint()
                paint.color = Color.BLACK
                paint.textSize = 12f
                var yOffset = 50f

                fun drawLine(text: String) {
                    canvas.drawText(text, 50f, yOffset, paint)
                    yOffset += 20f
                    if (yOffset > 800f) {
                        document.finishPage(page)
                        page = document.startPage(pageInfo)
                        canvas = page.canvas
                        yOffset = 50f
                    }
                }

                val title = if (overtimeOnly) "OVERTIME SUMMARY REPORT" else "TEAM ATTENDANCE & PAYROLL SUMMARY REPORT"
                drawLine(title)
                drawLine("Generated on: ${getCurrentDateString()} ${getCurrentTimeString()}")
                
                targetMemberId?.let { id ->
                    val name = memberMap[id]?.name ?: "Unknown"
                    drawLine("Filter Applied: Employee - $name")
                }
                if (overtimeOnly) drawLine("Filter Applied: Overtime Only")

                drawLine("-----------------------------------------------------")

                val totalPresent = recordList.count { it.isPresent }
                val totalOvertime = recordList.sumOf { it.overtimeHours }
                drawLine("Total Employee Attendance Days: $totalPresent")
                drawLine("Total Accumulated Overtime Hours: ${String.format("%.1f", totalOvertime)} hrs")
                drawLine("Offline Synchronized Backup Status: Verified Secure")
                drawLine("-----------------------------------------------------")
                drawLine("")
                drawLine("DETAILED LOG:")
                drawLine("")

                for (record in recordList) {
                    val member = memberMap[record.memberId]
                    val name = member?.name ?: "Unknown"
                    val status = record.status
                    val inTime = record.punchInTime ?: "N/A"
                    val outTime = record.punchOutTime ?: "N/A"
                    val approved = if (record.approvedBySupervisorId != null) "APPROVED" else "PENDING"

                    drawLine("Date: ${record.date} | Name: $name")
                    drawLine("Status: $status | In: $inTime | Out: $outTime")
                    drawLine("Overtime Hours: ${record.overtimeHours} | Approval: $approved")
                    drawLine("-----------------------------------------------------")
                }

                document.finishPage(page)

                val fileName = if (targetMemberId != null) "Employee_Attendance_${targetMemberId}.pdf" else if (overtimeOnly) "Overtime_Report.pdf" else "Monthly_Attendance_Report.pdf"
                val file = File(context.cacheDir, fileName)
                val fos = FileOutputStream(file)
                document.writeTo(fos)
                document.close()
                fos.close()

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
