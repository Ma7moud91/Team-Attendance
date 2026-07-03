package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.data.firestore.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import org.dhatim.fastexcel.Workbook
import android.graphics.pdf.PdfDocument
import android.graphics.Paint
import android.graphics.Color
import android.graphics.Canvas
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AttendanceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FirestoreRepository = FirestoreRepository()
    private val authRepository: com.example.data.AuthRepository = com.example.data.AuthRepository()

    // Current State
    private val _currentUser = MutableStateFlow<FirestoreMember?>(null)
    val currentUser: StateFlow<FirestoreMember?> = _currentUser.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _appTheme = MutableStateFlow("System Default")
    val appTheme: StateFlow<String> = _appTheme.asStateFlow()

    private val _appLanguage = MutableStateFlow("Arabic")
    val appLanguage: StateFlow<String> = _appLanguage.asStateFlow()

    private val _notifications = MutableStateFlow<List<FirestoreNotification>>(emptyList())
    val notifications: StateFlow<List<FirestoreNotification>> = _notifications.asStateFlow()

    // UI state streams
    val members = _isLoggedIn.flatMapLatest { loggedIn ->
        if (loggedIn) repository.allMembers else flowOf(emptyList())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _selectedDate = MutableStateFlow(getCurrentDateString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    val attendanceRecords = combine(_isLoggedIn, _selectedDate) { loggedIn, date ->
        loggedIn to date
    }.flatMapLatest { (loggedIn, date) ->
        if (loggedIn) repository.getAttendanceForDate(date) else flowOf(emptyList())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val assignmentHistory = _isLoggedIn.flatMapLatest { loggedIn ->
        if (loggedIn) repository.allAssignmentHistory else flowOf(emptyList())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val auditLogs = _isLoggedIn.flatMapLatest { loggedIn ->
        if (loggedIn) repository.allAuditLogs else flowOf(emptyList())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val messages = _isLoggedIn.flatMapLatest { loggedIn ->
        if (loggedIn) repository.allMessages else flowOf(emptyList())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun updateTheme(theme: String) {
        _appTheme.value = theme
    }

    fun updateLanguage(language: String) {
        _appLanguage.value = language
        val sharedPrefs = getApplication<Application>().getSharedPreferences("user_credentials", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("app_language", language).apply()
    }

    private val _isOfflineMode = MutableStateFlow(false)
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()

    private val _isFirebaseConnected = MutableStateFlow(true)
    val isFirebaseConnected: StateFlow<Boolean> = _isFirebaseConnected.asStateFlow()

    fun checkFirebaseConnection() {
        viewModelScope.launch {
            _isFirebaseConnected.value = com.example.data.FirebaseConfig.verifyConnection()
        }
    }

    private val _allowSupervisorExport = MutableStateFlow(false)
    val allowSupervisorExport: StateFlow<Boolean> = _allowSupervisorExport.asStateFlow()

    fun toggleSupervisorExport() {
        _allowSupervisorExport.value = !_allowSupervisorExport.value
    }

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
    private val _supervisorOvertimeInputs = MutableStateFlow<Map<String, String>>(emptyMap())
    val supervisorOvertimeInputs = _supervisorOvertimeInputs.asStateFlow()

    private val _supervisorPresence = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val supervisorPresence = _supervisorPresence.asStateFlow()

    private val _isBiometricPreferred = MutableStateFlow(true)
    val isBiometricPreferred: StateFlow<Boolean> = _isBiometricPreferred.asStateFlow()

    fun toggleBiometricPreferred() {
        _isBiometricPreferred.value = !_isBiometricPreferred.value
        val sharedPrefs = getApplication<Application>().getSharedPreferences("user_credentials", Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("biometric_preferred", _isBiometricPreferred.value).apply()
    }

    fun getLastLoggedInUserId(): String {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("user_credentials", Context.MODE_PRIVATE)
        return sharedPrefs.getString("last_logged_in_user_id", "") ?: ""
    }

    suspend fun loginWithBiometric(userId: String): Boolean {
        _loginError.value = null
        try {
            val member = repository.getMemberById(userId)
            if (member != null) {
                if (!member.isActive) {
                    _loginError.value = "Your account has been deactivated. Please contact the Developer."
                    return false
                }
                _currentUser.value = member
                _isLoggedIn.value = true
                val sharedPrefs = getApplication<Application>().getSharedPreferences("user_credentials", Context.MODE_PRIVATE)
                sharedPrefs.edit().putString("logged_in_user_id", member.id).apply()
                sharedPrefs.edit().putString("last_logged_in_user_id", member.id).apply()
                
                // Start observing the profile for real-time updates/offline resilience
                observeCurrentUserProfile(member.id)
                
                return true
            } else {
                _loginError.value = "Registered biometric user not found."
                return false
            }
        } catch (e: Exception) {
            _loginError.value = "Connection failed: ${e.message}. Using cached data if available."
            return false
        }
    }

    private fun observeCurrentUserProfile(uid: String) {
        viewModelScope.launch {
            repository.getMemberFlow(uid).collect { updatedMember ->
                if (updatedMember != null) {
                    _currentUser.value = updatedMember
                    if (!updatedMember.isActive && _isLoggedIn.value) {
                        logout()
                        _loginError.value = "Your account was deactivated."
                    }
                }
            }
        }
    }

    init {
        checkFirebaseConnection()
        viewModelScope.launch {
            try {
                // Session Management: load last session
                val sharedPrefs = getApplication<Application>().getSharedPreferences("user_credentials", Context.MODE_PRIVATE)
                _appLanguage.value = sharedPrefs.getString("app_language", "Arabic") ?: "Arabic"
                _isBiometricPreferred.value = sharedPrefs.getBoolean("biometric_preferred", true)
                
                val currentFirebaseUser = authRepository.getCurrentUser()
                if (currentFirebaseUser != null) {
                    val uid = currentFirebaseUser.uid
                    try {
                        var matchedMember = repository.getMemberById(uid)
                        
                        if (matchedMember == null && currentFirebaseUser.email == "eng.mahmoudahmed1991@gmail.com") {
                            repository.assignRole(
                                uid = uid,
                                role = "DEVELOPER",
                                name = currentFirebaseUser.displayName ?: "Developer Admin",
                                email = currentFirebaseUser.email ?: "",
                                title = "System Architect"
                            )
                            authRepository.refreshToken(true)
                            matchedMember = repository.getMemberById(uid)
                        }

                        if (matchedMember != null) {
                            if (matchedMember.isActive) {
                                _currentUser.value = matchedMember
                                _isLoggedIn.value = true
                                cacheMemberProfile(matchedMember)
                                loadNotifications(matchedMember.id)
                                // Start observing for real-time changes
                                observeCurrentUserProfile(uid)
                            } else {
                                _loginError.value = "Account deactivated."
                            }
                        }
                    } catch (e: Exception) {
                        // If offline, try to load from cache
                        getCachedMemberProfile(uid)?.let {
                            _currentUser.value = it
                            _isLoggedIn.value = true
                        }
                        observeCurrentUserProfile(uid)
                    }
                } else {
                    val lastUserId = sharedPrefs.getString("logged_in_user_id", "") ?: ""
                    if (lastUserId.isNotEmpty()) {
                        try {
                            val cached = getCachedMemberProfile(lastUserId)
                            if (cached != null && cached.isActive) {
                                _currentUser.value = cached
                                _isLoggedIn.value = true
                                loadNotifications(cached.id)
                                observeCurrentUserProfile(lastUserId)
                            } else {
                                val member = repository.getMemberById(lastUserId)
                                if (member != null && member.isActive) {
                                    _currentUser.value = member
                                    _isLoggedIn.value = true
                                    cacheMemberProfile(member)
                                    loadNotifications(member.id)
                                    observeCurrentUserProfile(lastUserId)
                                }
                            }
                        } catch (e: Exception) {
                            getCachedMemberProfile(lastUserId)?.let {
                                _currentUser.value = it
                                _isLoggedIn.value = true
                            }
                            observeCurrentUserProfile(lastUserId)
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore general initialization errors
            }
        }
    }

    private fun cacheMemberProfile(member: FirestoreMember) {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("user_credentials", Context.MODE_PRIVATE)
        sharedPrefs.edit().apply {
            putString("cached_member_id", member.id)
            putString("cached_member_name", member.name)
            putString("cached_member_email", member.email)
            putString("cached_member_role", member.role)
            putBoolean("cached_member_is_active", member.isActive)
            putString("cached_member_profile_image", member.profileImage)
            apply()
        }
    }

    private fun getCachedMemberProfile(uid: String): FirestoreMember? {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("user_credentials", Context.MODE_PRIVATE)
        val cachedId = sharedPrefs.getString("cached_member_id", "") ?: ""
        if (cachedId == uid) {
            return FirestoreMember(
                id = cachedId,
                name = sharedPrefs.getString("cached_member_name", "") ?: "",
                email = sharedPrefs.getString("cached_member_email", "") ?: "",
                role = sharedPrefs.getString("cached_member_role", "EMPLOYEE") ?: "EMPLOYEE",
                isActive = sharedPrefs.getBoolean("cached_member_is_active", true),
                profileImage = sharedPrefs.getString("cached_member_profile_image", "") ?: ""
            )
        }
        return null
    }

    fun getLastCachedUser(): FirestoreMember? {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("user_credentials", Context.MODE_PRIVATE)
        val lastId = sharedPrefs.getString("last_logged_in_user_id", "") ?: ""
        return if (lastId.isNotEmpty()) getCachedMemberProfile(lastId) else null
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

    // Set stored password for a member
    fun setMemberPassword(memberId: String, newPassword: String) {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("user_credentials", Context.MODE_PRIVATE)
        val hashed = hashPassword(newPassword)
        sharedPrefs.edit().putString("password_$memberId", hashed).apply()
    }

    // Activate/Deactivate a member
    fun toggleMemberActive(member: FirestoreMember) {
        viewModelScope.launch {
            repository.assignRole(
                uid = member.id,
                role = member.role,
                name = member.name,
                email = member.email,
                title = member.title,
                supervisorId = member.supervisorId
            )
            // Refresh token if self
            if (member.id == _currentUser.value?.id) {
                authRepository.refreshToken(true)
            }
        }
    }

    // Get stored recovery code for a member (or empty if none)
    fun getMemberRecoveryCode(memberId: String): String {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("user_credentials", Context.MODE_PRIVATE)
        return sharedPrefs.getString("recovery_$memberId", "") ?: ""
    }

    // Set stored recovery code for a member (e.g., 6-digit random number)
    fun setMemberRecoveryCode(memberId: String, recoveryCode: String) {
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
        return try {
            // "Direct Login" for Admins/Developers if authorized via passcode
            if (pword.isEmpty() && com.example.ui.DeveloperAuthService.isDeveloperAuthorized(getApplication())) {
                val matchedMember = repository.getMemberByEmailOrName(username)
                if (matchedMember != null && (matchedMember.role == "ADMIN" || matchedMember.role == "DEVELOPER")) {
                    _currentUser.value = matchedMember
                    _isLoggedIn.value = true
                    
                    val sharedPrefs = getApplication<Application>().getSharedPreferences("user_credentials", Context.MODE_PRIVATE)
                    sharedPrefs.edit()
                        .putString("logged_in_user_id", matchedMember.id)
                        .putString("last_logged_in_user_id", matchedMember.id)
                        .apply()
                    
                    observeCurrentUserProfile(matchedMember.id)
                    return true
                }
            }

            val user = authRepository.signIn(username, pword)
            
            // If they exist, get or match their profile to local members
            var matchedMember = repository.getMemberById(user.uid)
            
            if (matchedMember == null && user.email == "eng.mahmoudahmed1991@gmail.com") {
                // Auto-provision developer account
                repository.assignRole(
                    uid = user.uid,
                    role = "DEVELOPER",
                    name = user.displayName ?: "Developer Admin",
                    email = user.email ?: "",
                    title = "System Architect"
                )
                authRepository.refreshToken(true)
                matchedMember = repository.getMemberById(user.uid)
            }
            
            if (matchedMember != null) {
                // Allow Admin/Developer to login even if deactivated ("without check on system")
                if (!matchedMember.isActive && matchedMember.role != "ADMIN" && matchedMember.role != "DEVELOPER") {
                    _loginError.value = "Your account has been deactivated. Please contact the Developer."
                    return false
                }
                _currentUser.value = matchedMember
                _isLoggedIn.value = true
                val sharedPrefs = getApplication<Application>().getSharedPreferences("user_credentials", Context.MODE_PRIVATE)
                sharedPrefs.edit()
                    .putString("logged_in_user_id", matchedMember.id)
                    .putString("last_logged_in_user_id", matchedMember.id)
                    .apply()
                
                // Start observing the profile for real-time updates/offline resilience
                observeCurrentUserProfile(matchedMember.id)
                
                return true
            } else {
                _loginError.value = "User profile not found. Please contact an Administrator."
                return false
            }
        } catch (e: Exception) {
            _loginError.value = "Authentication error: ${e.message}"
            false
        }
    }

    suspend fun signUp(email: String, pword: String): Boolean {
        _loginError.value = null
        return try {
            val user = authRepository.signUp(email, pword)
            val uid = user.uid
            val nameFromEmail = email.substringBefore("@").replaceFirstChar { it.uppercase() }
            
            // Auto-navigate to login or wait for admin approval
            _loginError.value = "Account created. Please wait for an administrator to assign your role."
            true
        } catch (e: Exception) {
            _loginError.value = "Sign Up error: ${e.message}"
            false
        }
    }

    suspend fun loginWithMicrosoft365(email: String): Boolean {
        _loginError.value = null
        var matchedMember = repository.getMemberByEmailOrName(email)
        
        if (matchedMember == null && email == "eng.mahmoudahmed1991@gmail.com") {
            // Auto-provision developer account if it doesn't exist but email matches
            val firebaseUser = authRepository.getCurrentUser()
            if (firebaseUser != null) {
                repository.assignRole(
                    uid = firebaseUser.uid,
                    role = "DEVELOPER",
                    name = firebaseUser.displayName ?: "Developer Admin",
                    email = email,
                    title = "System Architect"
                )
                authRepository.refreshToken(true)
                matchedMember = repository.getMemberById(firebaseUser.uid)
            }
        }

        if (matchedMember != null) {
            if (!matchedMember.isActive) {
                _loginError.value = "Your account has been deactivated. Please contact the Developer."
                return false
            }
            _currentUser.value = matchedMember
            _isLoggedIn.value = true
            val sharedPrefs = getApplication<Application>().getSharedPreferences("user_credentials", Context.MODE_PRIVATE)
            sharedPrefs.edit()
                .putString("logged_in_user_id", matchedMember.id)
                .putString("last_logged_in_user_id", matchedMember.id)
                .apply()
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
        authRepository.signOut()
        val sharedPrefs = getApplication<Application>().getSharedPreferences("user_credentials", Context.MODE_PRIVATE)
        sharedPrefs.edit().remove("logged_in_user_id").apply()
    }

    fun switchUser(member: FirestoreMember) {
        _currentUser.value = member
        observeCurrentUserProfile(member.id)
    }

    fun toggleOfflineMode() {
        _isOfflineMode.value = !_isOfflineMode.value
    }

    fun setDate(date: String) {
        _selectedDate.value = date
    }

    fun updateSupervisorOvertime(memberId: String, value: String) {
        val updatedMap = _supervisorOvertimeInputs.value.toMutableMap()
        updatedMap[memberId] = value
        _supervisorOvertimeInputs.value = updatedMap
    }

    fun updateSupervisorPresence(memberId: String, value: Boolean) {
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
                    FirestoreAttendance(
                        memberId = user.id,
                        date = today,
                        isPresent = true,
                        punchInTime = getCurrentTimeString(),
                        locationData = locationData,
                        status = "PENDING"
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
                        locationData = locToSave
                    )
                )
            }
        }
    }

    // Supervisor Marks Attendance & Overtime
    fun supervisorSaveAttendance(memberId: String, isPresent: Boolean, overtime: Double) {
        val supervisor = _currentUser.value ?: return
        viewModelScope.launch {
            val today = getCurrentDateString()
            val existing = repository.getAttendanceForMemberAndDate(memberId, today)
            val updated = existing?.copy(
                isPresent = isPresent,
                overtimeHours = overtime,
                approvedBySupervisorId = supervisor.id,
                status = "APPROVED"
            ) ?: FirestoreAttendance(
                memberId = memberId,
                date = today,
                isPresent = isPresent,
                overtimeHours = overtime,
                approvedBySupervisorId = supervisor.id,
                status = "APPROVED"
            )
            repository.saveAttendance(updated)
        }
    }

    // Admin / Supervisor Approval
    fun approveAttendanceRecord(attendanceId: String) {
        val approver = _currentUser.value ?: return
        viewModelScope.launch {
            val record = attendanceRecords.value.find { it.id == attendanceId } ?: return@launch
            repository.approveAttendance(attendanceId)
            
            // Send notification to employee
            repository.insertNotification(
                FirestoreNotification(
                    memberId = record.memberId,
                    title = "Attendance Approved",
                    message = "Your attendance for ${record.date} has been approved by ${approver.name}."
                )
            )
        }
    }

    fun rejectAttendanceRecord(attendanceId: String, reason: String) {
        val approver = _currentUser.value ?: return
        viewModelScope.launch {
            val record = attendanceRecords.value.find { it.id == attendanceId } ?: return@launch
            repository.rejectAttendance(attendanceId, reason)
            
            // Send notification to employee
            repository.insertNotification(
                FirestoreNotification(
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
                repository.approveAttendance(record.id)
                repository.insertNotification(
                    FirestoreNotification(
                        memberId = record.memberId,
                        title = "Attendance Approved",
                        message = "Your attendance for ${record.date} has been approved by ${approver.name}."
                    )
                )
            }
        }
    }

    fun loadNotifications(memberId: String) {
        viewModelScope.launch {
            repository.getNotificationsForMember(memberId).collect {
                _notifications.value = it
            }
        }
    }

    fun markNotificationAsRead(notificationId: String) {
        viewModelScope.launch {
            repository.markNotificationAsRead(notificationId)
        }
    }

    // Add new team member
    fun addTeamMember(name: String, title: String, role: String, email: String, requiresLocation: Boolean, password: String? = null) {
        viewModelScope.launch {
            // In a real app, you'd create the Auth user first. 
            // For now, we'll assume the UID is generated or provided.
            // But assignRole requires a UID.
            // This is a bit tricky without a proper admin tool to create Auth users.
        }
    }

    // Edit team member
    fun editTeamMember(member: FirestoreMember, name: String, title: String, role: String, email: String, requiresLocation: Boolean) {
        viewModelScope.launch {
            repository.assignRole(
                uid = member.id,
                role = role,
                name = name,
                email = email,
                title = title,
                supervisorId = member.supervisorId
            )
            // Refresh token if self
            if (member.id == _currentUser.value?.id) {
                authRepository.refreshToken(true)
            }
        }
    }

    // Delete team member
    fun removeTeamMember(member: FirestoreMember) {
        viewModelScope.launch {
            repository.deleteMember(member.id)
        }
    }

    // Assign supervisor to an employee
    fun assignSupervisorToEmployee(employeeId: String, supervisorId: String?) {
        val admin = _currentUser.value ?: return
        viewModelScope.launch {
            val employee = repository.getMemberById(employeeId) ?: return@launch
            
            repository.assignRole(
                uid = employeeId,
                role = employee.role,
                name = employee.name,
                email = employee.email,
                title = employee.title,
                supervisorId = supervisorId
            )
        }
    }

    // Purge dummy members and unknown/unapproved records
    fun purgeDummyDataAndUnknownApprovals(onComplete: (membersCount: Int, recordsCount: Int) -> Unit = { _, _ -> }) {
        // Not implemented for Firestore
        onComplete(0, 0)
    }

    // Synchronize to secure cloud database
    fun synchronizeCloud() {
        // Automatic in Firestore
    }

    // Export Database
    fun exportDatabase(context: Context) {
        // Not implemented for Firestore
        _exportResult.value = "Export not available for cloud database."
    }

    // Import Database
    fun importDatabase(context: Context, uri: Uri) {
        // Not implemented for Firestore
        _exportResult.value = "Import not available for cloud database."
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
        records: List<FirestoreAttendance>,
        memberList: List<FirestoreMember>,
        employeeId: String?,
        team: String,
        supervisorId: String?,
        startDateStr: String,
        endDateStr: String,
        status: String
    ): List<FirestoreAttendance> {
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
        employeeId: String? = null,
        team: String = "All",
        supervisorId: String? = null,
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
        employeeId: String? = null,
        team: String = "All",
        supervisorId: String? = null,
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
                                // Skip member creation from Excel in Firestore version as it requires Auth UID
                                continue
                            }

                            // 2. Insert or update attendance log
                            val existingAttendance = repository.getAttendanceForMemberAndDate(member.id, date)
                            val attendance = existingAttendance?.copy(
                                isPresent = isPresent,
                                punchInTime = clockIn,
                                punchOutTime = if (clockOut == "-") null else clockOut,
                                overtimeHours = overtime,
                                status = "APPROVED"
                            ) ?: FirestoreAttendance(
                                memberId = member.id,
                                date = date,
                                isPresent = isPresent,
                                punchInTime = clockIn,
                                punchOutTime = if (clockOut == "-") null else clockOut,
                                overtimeHours = overtime,
                                status = "APPROVED"
                            )
                            repository.insertAttendance(attendance)
                            parsedCount++
                        }
                    }
                    _excelImportResult.value = "Successfully linked and parsed Excel data! Synchronized $parsedCount logs. Member creation from Excel is disabled in Firestore version."
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
            repository.sendMessage(
                FirestoreMessage(
                    senderId = user.id,
                    senderName = user.name,
                    content = content
                )
            )
        }
    }

    fun markMessageAsRead(messageId: String) {
        viewModelScope.launch {
            repository.markMessageAsRead(messageId)
        }
    }

    fun updateMemberProfileImage(memberId: String, uri: String) {
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
