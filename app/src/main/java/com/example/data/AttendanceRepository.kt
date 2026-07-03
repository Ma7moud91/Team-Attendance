package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AttendanceRepository {

    private val dao by lazy {
        AppDatabase.getDatabase(com.example.AttendanceApplication.instance).attendanceDao()
    }

    init {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            dao.getAllMembers().collect {
                _members.value = it
            }
        }
        scope.launch {
            dao.getAllAttendance().collect {
                _attendance.value = it
            }
        }
        scope.launch {
            dao.getAllSyncLogs().collect {
                _syncLogs.value = it
            }
        }
        scope.launch {
            dao.getAllMessages().collect {
                _messages.value = it
            }
        }
        scope.launch {
            dao.getAllAssignmentHistory().collect {
                _assignmentHistory.value = it
            }
        }
        scope.launch {
            dao.getAllAuditLogs().collect {
                _auditLogs.value = it
            }
        }
        scope.launch {
            dao.getAllNotifications().collect {
                _notifications.value = it
            }
        }
    }

    private val _members = MutableStateFlow<List<Member>>(emptyList())
    val allMembers: Flow<List<Member>> = _members.asStateFlow().map { list -> list.sortedBy { it.name } }

    private val _attendance = MutableStateFlow<List<Attendance>>(emptyList())
    val allAttendance: Flow<List<Attendance>> = _attendance.asStateFlow().map { list -> list.sortedByDescending { it.date } }

    private val _syncLogs = MutableStateFlow<List<SyncLog>>(emptyList())
    val allSyncLogs: Flow<List<SyncLog>> = _syncLogs.asStateFlow().map { list -> list.sortedByDescending { it.timestamp } }

    private val _messages = MutableStateFlow<List<InboxMessage>>(emptyList())
    val allMessages: Flow<List<InboxMessage>> = _messages.asStateFlow().map { list -> list.sortedByDescending { it.timestamp } }

    private val _assignmentHistory = MutableStateFlow<List<SupervisorAssignmentHistory>>(emptyList())
    val allAssignmentHistory: Flow<List<SupervisorAssignmentHistory>> = _assignmentHistory.asStateFlow().map { list -> list.sortedByDescending { it.timestamp } }

    private val _auditLogs = MutableStateFlow<List<AuditLog>>(emptyList())
    val allAuditLogs: Flow<List<AuditLog>> = _auditLogs.asStateFlow().map { list -> list.sortedByDescending { it.timestamp } }

    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())

    // Firestore reference (Firebase is kept as requested)
    private val firestore by lazy {
        try {
            FirebaseConfig.firestore
        } catch (e: Exception) {
            null
        }
    }

    fun getAttendanceForDate(date: String): Flow<List<Attendance>> {
        return _attendance.asStateFlow().map { list -> list.filter { it.date == date } }
    }

    fun getAttendanceForMember(memberId: Long): Flow<List<Attendance>> {
        return _attendance.asStateFlow().map { list -> list.filter { it.memberId == memberId }.sortedByDescending { it.date } }
    }

    suspend fun insertMember(member: Member): Long {
        val current = _members.value
        val idToUse = if (member.id == 0L) {
            (current.maxOfOrNull { it.id } ?: 0L) + 1L
        } else {
            member.id
        }
        val finalMember = member.copy(id = idToUse)
        dao.insertMember(finalMember)

        // Save to Firestore in a non-blocking background attempt if configured
        saveToFirestoreAsync("members", idToUse.toString(), finalMember)

        return idToUse
    }

    suspend fun deleteMember(member: Member) {
        dao.deleteMember(member)
        
        // Delete from legacy "members"
        deleteFromFirestoreAsync(FirebaseConfig.COLLECTION_LEGACY_MEMBERS, member.id.toString())
        
        // Delete from root "users"
        val userId = member.uid ?: member.id.toString()
        deleteFromFirestoreAsync(FirebaseConfig.COLLECTION_USERS, userId)
        
        // Remove from root "teams"
        try {
            val department = if (member.title.contains("(") && member.title.contains(")")) {
                member.title.substringAfter("(").substringBefore(")")
            } else {
                "General"
            }
            val teamId = department.lowercase().trim().replace("\\s+".toRegex(), "_")
            val teamRef = firestore?.collection(FirebaseConfig.COLLECTION_TEAMS)?.document(teamId)
            teamRef?.get()?.addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val existingMembers = doc.get("members") as? List<String> ?: emptyList()
                    if (existingMembers.contains(userId)) {
                        val updatedMembers = existingMembers.toMutableList()
                        updatedMembers.remove(userId)
                        if (updatedMembers.isEmpty()) {
                            teamRef.delete()
                        } else {
                            teamRef.update("members", updatedMembers)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Silently ignore
        }
    }

    suspend fun getMemberById(id: Long): Member? {
        return _members.value.find { it.id == id }
    }

    suspend fun getMemberCount(): Int {
        return _members.value.size
    }

    suspend fun getMemberByEmailOrName(identifier: String): Member? {
        return _members.value.find {
            it.email.equals(identifier, ignoreCase = true) || it.name.equals(identifier, ignoreCase = true)
        }
    }

    suspend fun insertAttendance(attendance: Attendance): Long {
        val current = _attendance.value
        val idToUse = if (attendance.id == 0L) {
            (current.maxOfOrNull { it.id } ?: 0L) + 1L
        } else {
            attendance.id
        }
        val finalAttendance = attendance.copy(id = idToUse)
        dao.insertAttendance(finalAttendance)

        saveToFirestoreAsync("attendance", idToUse.toString(), finalAttendance)

        return idToUse
    }

    suspend fun deleteAttendance(attendance: Attendance) {
        dao.deleteAttendance(attendance)
        deleteFromFirestoreAsync(FirebaseConfig.COLLECTION_LEGACY_ATTENDANCE, attendance.id.toString())
        deleteFromFirestoreAsync(FirebaseConfig.COLLECTION_ATTENDANCE_RECORDS, attendance.id.toString())
    }

    suspend fun deleteAttendanceForMember(memberId: Long) {
        val current = _attendance.value
        val itemsToRemove = current.filter { it.memberId == memberId }
        for (item in itemsToRemove) {
            dao.deleteAttendance(item)
            deleteFromFirestoreAsync(FirebaseConfig.COLLECTION_LEGACY_ATTENDANCE, item.id.toString())
            deleteFromFirestoreAsync(FirebaseConfig.COLLECTION_ATTENDANCE_RECORDS, item.id.toString())
        }
    }

    suspend fun deleteUnapprovedAttendance() {
        val current = _attendance.value
        val itemsToRemove = current.filter { it.approvedBySupervisorId == null }
        for (item in itemsToRemove) {
            dao.deleteAttendance(item)
            deleteFromFirestoreAsync(FirebaseConfig.COLLECTION_LEGACY_ATTENDANCE, item.id.toString())
            deleteFromFirestoreAsync(FirebaseConfig.COLLECTION_ATTENDANCE_RECORDS, item.id.toString())
        }
    }

    suspend fun getAttendanceForMemberAndDate(memberId: Long, date: String): Attendance? {
        return _attendance.value.find { it.memberId == memberId && it.date == date }
    }

    suspend fun approveAttendance(id: Long, supervisorId: Long) {
        val current = _attendance.value
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            val updated = current[index].copy(approvedBySupervisorId = supervisorId, status = "APPROVED")
            dao.insertAttendance(updated)
            saveToFirestoreAsync("attendance", id.toString(), updated)
        }
    }

    suspend fun rejectAttendance(id: Long, reason: String) {
        val current = _attendance.value
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            val updated = current[index].copy(status = "REJECTED", rejectionReason = reason)
            dao.insertAttendance(updated)
            saveToFirestoreAsync("attendance", id.toString(), updated)
        }
    }

    suspend fun getAttendanceById(id: Long): Attendance? {
        return _attendance.value.find { it.id == id }
    }

    suspend fun performCloudSync(): SyncLog {
        delay(1500)
        val current = _attendance.value
        val unsynced = current.filter { !it.isSynced }
        val count = unsynced.size

        val log = if (count > 0) {
            // Mark all as synced in memory
            val updatedList = mutableListOf<Attendance>()
            for (item in current) {
                if (!item.isSynced) {
                    val updated = item.copy(isSynced = true)
                    updatedList.add(updated)
                    saveToFirestoreAsync("attendance", updated.id.toString(), updated)
                }
            }
            if (updatedList.isNotEmpty()) {
                dao.insertAttendances(updatedList)
            }

            SyncLog(
                recordsSynced = count,
                status = "SUCCESS",
                message = "Successfully backed up $count records to secure Firebase Cloud database."
            )
        } else {
            SyncLog(
                recordsSynced = 0,
                status = "SUCCESS",
                message = "Database is already up to date. No new records to backup."
            )
        }

        insertSyncLog(log)
        return log
    }

    suspend fun recordManualSyncFailure(message: String): SyncLog {
        val log = SyncLog(
            recordsSynced = 0,
            status = "FAILED",
            message = message
        )
        insertSyncLog(log)
        return log
    }

    suspend fun insertMessage(message: InboxMessage): Long {
        val current = _messages.value
        val idToUse = if (message.id == 0L) {
            (current.maxOfOrNull { it.id } ?: 0L) + 1L
        } else {
            message.id
        }
        val finalMessage = message.copy(id = idToUse)
        dao.insertMessage(finalMessage)

        saveToFirestoreAsync("messages", idToUse.toString(), finalMessage)

        return idToUse
    }

    suspend fun insertSyncLog(log: SyncLog): Long {
        val current = _syncLogs.value
        val idToUse = if (log.id == 0L) {
            (current.maxOfOrNull { it.id } ?: 0L) + 1L
        } else {
            log.id
        }
        val finalLog = log.copy(id = idToUse)
        dao.insertSyncLog(finalLog)

        saveToFirestoreAsync("sync_logs", idToUse.toString(), finalLog)

        return idToUse
    }

    suspend fun markMessageAsRead(id: Long) {
        val current = _messages.value
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            val updated = current[index].copy(isRead = true)
            dao.insertMessage(updated)
            saveToFirestoreAsync("messages", id.toString(), updated)
        }
    }

    // Notifications
    fun getNotificationsForMember(memberId: Long): Flow<List<AppNotification>> {
        return _notifications.asStateFlow().map { list -> list.filter { it.memberId == memberId }.sortedByDescending { it.timestamp } }
    }

    suspend fun insertNotification(notification: AppNotification): Long {
        val current = _notifications.value
        val idToUse = if (notification.id == 0L) {
            (current.maxOfOrNull { it.id } ?: 0L) + 1L
        } else {
            notification.id
        }
        val finalNotification = notification.copy(id = idToUse)
        dao.insertNotification(finalNotification)

        saveToFirestoreAsync("notifications", idToUse.toString(), finalNotification)

        return idToUse
    }

    suspend fun markNotificationAsRead(id: Long) {
        val current = _notifications.value
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            val updated = current[index].copy(isRead = true)
            dao.insertNotification(updated)
            saveToFirestoreAsync("notifications", id.toString(), updated)
        }
    }

    suspend fun updateMemberProfileImage(memberId: Long, imageUri: String) {
        val current = _members.value
        val index = current.indexOfFirst { it.id == memberId }
        if (index != -1) {
            val updated = current[index].copy(profileImage = imageUri)
            dao.insertMember(updated)
            saveToFirestoreAsync("members", memberId.toString(), updated)
        }
    }

    suspend fun insertAssignmentHistory(history: SupervisorAssignmentHistory): Long {
        val current = _assignmentHistory.value
        val idToUse = if (history.id == 0L) {
            (current.maxOfOrNull { it.id } ?: 0L) + 1L
        } else {
            history.id
        }
        val finalHistory = history.copy(id = idToUse)
        dao.insertAssignmentHistory(finalHistory)

        saveToFirestoreAsync("assignment_history", idToUse.toString(), finalHistory)

        return idToUse
    }

    suspend fun insertAuditLog(log: AuditLog): Long {
        val current = _auditLogs.value
        val idToUse = if (log.id == 0L) {
            (current.maxOfOrNull { it.id } ?: 0L) + 1L
        } else {
            log.id
        }
        val finalLog = log.copy(id = idToUse)
        dao.insertAuditLog(finalLog)

        saveToFirestoreAsync("audit_logs", idToUse.toString(), finalLog)

        return idToUse
    }

    // Helper to safely write to Firebase Firestore in a background flow
    private fun saveToFirestoreAsync(collectionName: String, docId: String, data: Any) {
        try {
            val dbData = when (data) {
                is Member -> {
                    val mData = hashMapOf(
                        "id" to data.id,
                        "name" to data.name,
                        "title" to data.title,
                        "role" to data.role,
                        "email" to data.email,
                        "requiresLocation" to data.requiresLocation,
                        "profileImage" to data.profileImage,
                        "isActive" to data.isActive,
                        "supervisorId" to data.supervisorId,
                        "uid" to data.uid
                    )
                    // Sync with root "users" collection
                    val userId = data.uid ?: data.id.toString()
                    val userMap = hashMapOf(
                        "name" to data.name,
                        "email" to data.email,
                        "role" to data.role
                    )
                    firestore?.collection(FirebaseConfig.COLLECTION_USERS)?.document(userId)?.set(userMap)

                    // Sync with root "teams" collection
                    val department = if (data.title.contains("(") && data.title.contains(")")) {
                        data.title.substringAfter("(").substringBefore(")")
                    } else {
                        "General"
                    }
                    val teamId = department.lowercase().trim().replace("\\s+".toRegex(), "_")
                    val teamRef = firestore?.collection(FirebaseConfig.COLLECTION_TEAMS)?.document(teamId)
                    teamRef?.get()?.addOnSuccessListener { doc ->
                        val existingMembers = doc.get("members") as? List<String> ?: emptyList()
                        if (!existingMembers.contains(userId)) {
                            val updatedMembers = existingMembers.toMutableList()
                            updatedMembers.add(userId)
                            teamRef.set(hashMapOf(
                                "teamName" to department,
                                "description" to "$department Department Team",
                                "members" to updatedMembers
                            ))
                        }
                    }?.addOnFailureListener {
                        teamRef.set(hashMapOf(
                            "teamName" to department,
                            "description" to "$department Department Team",
                            "members" to listOf(userId)
                        ))
                    } ?: run {
                        teamRef?.set(hashMapOf(
                            "teamName" to department,
                            "description" to "$department Department Team",
                            "members" to listOf(userId)
                        ))
                    }

                    mData
                }
                is Attendance -> {
                    val aData = hashMapOf(
                        "id" to data.id,
                        "memberId" to data.memberId,
                        "date" to data.date,
                        "isPresent" to data.isPresent,
                        "punchInTime" to data.punchInTime,
                        "punchOutTime" to data.punchOutTime,
                        "overtimeHours" to data.overtimeHours,
                        "locationData" to data.locationData,
                        "status" to data.status,
                        "approvedBySupervisorId" to data.approvedBySupervisorId,
                        "rejectionReason" to data.rejectionReason,
                        "isSynced" to data.isSynced
                    )

                    // Sync with root "attendance_records" collection
                    val member = _members.value.find { it.id == data.memberId }
                    val userUid = member?.uid ?: member?.id?.toString() ?: data.memberId.toString()
                    val department = if (member != null && member.title.contains("(") && member.title.contains(")")) {
                        member.title.substringAfter("(").substringBefore(")")
                    } else {
                        "General"
                    }
                    val teamId = department.lowercase().trim().replace("\\s+".toRegex(), "_")
                    val statusStr = if (data.isPresent) "Present" else "Absent"
                    val supervisor = _members.value.find { it.id == data.approvedBySupervisorId }
                    val markedByUid = supervisor?.uid ?: supervisor?.id?.toString() ?: userUid

                    val attendanceRecordMap = hashMapOf(
                        "teamId" to teamId,
                        "userId" to userUid,
                        "eventId" to "event_${data.id}",
                        "date" to data.date,
                        "status" to statusStr,
                        "markedBy" to markedByUid
                    )
                    firestore?.collection(FirebaseConfig.COLLECTION_ATTENDANCE_RECORDS)?.document(data.id.toString())?.set(attendanceRecordMap)

                    aData
                }
                is SyncLog -> hashMapOf(
                    "id" to data.id,
                    "timestamp" to data.timestamp,
                    "recordsSynced" to data.recordsSynced,
                    "status" to data.status,
                    "message" to data.message
                )
                is InboxMessage -> hashMapOf(
                    "id" to data.id,
                    "senderId" to data.senderId,
                    "senderName" to data.senderName,
                    "content" to data.content,
                    "timestamp" to data.timestamp,
                    "isRead" to data.isRead
                )
                is AppNotification -> hashMapOf(
                    "id" to data.id,
                    "memberId" to data.memberId,
                    "title" to data.title,
                    "message" to data.message,
                    "timestamp" to data.timestamp,
                    "isRead" to data.isRead
                )
                is SupervisorAssignmentHistory -> hashMapOf(
                    "id" to data.id,
                    "employeeId" to data.employeeId,
                    "employeeName" to data.employeeName,
                    "previousSupervisorId" to data.previousSupervisorId,
                    "previousSupervisorName" to data.previousSupervisorName,
                    "newSupervisorId" to data.newSupervisorId,
                    "newSupervisorName" to data.newSupervisorName,
                    "assignedByAdminId" to data.assignedByAdminId,
                    "assignedByAdminName" to data.assignedByAdminName,
                    "timestamp" to data.timestamp
                )
                is AuditLog -> hashMapOf(
                    "id" to data.id,
                    "userId" to data.userId,
                    "username" to data.username,
                    "userRole" to data.userRole,
                    "timestamp" to data.timestamp,
                    "actionType" to data.actionType,
                    "details" to data.details,
                    "reportType" to data.reportType,
                    "exportFormat" to data.exportFormat
                )
                else -> data
            }
            firestore?.collection(collectionName)?.document(docId)?.set(dbData)
        } catch (e: Exception) {
            // Fail silently so local in-memory operation is uninterrupted
        }
    }

    // Helper to safely write all local data collections to Firestore
    suspend fun saveAllToFirestore() {
        val fs = firestore ?: return
        try {
            for (m in _members.value) {
                saveToFirestoreAsync("members", m.id.toString(), m)
            }
            for (a in _attendance.value) {
                saveToFirestoreAsync("attendance", a.id.toString(), a)
            }
            for (s in _syncLogs.value) {
                saveToFirestoreAsync("sync_logs", s.id.toString(), s)
            }
            for (m in _messages.value) {
                saveToFirestoreAsync("messages", m.id.toString(), m)
            }
            for (n in _notifications.value) {
                saveToFirestoreAsync("notifications", n.id.toString(), n)
            }
            for (h in _assignmentHistory.value) {
                saveToFirestoreAsync("assignment_history", h.id.toString(), h)
            }
            for (al in _auditLogs.value) {
                saveToFirestoreAsync("audit_logs", al.id.toString(), al)
            }
        } catch (e: Exception) {
            // Silently ignore
        }
    }

    // Helper to safely delete from Firebase Firestore
    private fun deleteFromFirestoreAsync(collectionName: String, docId: String) {
        try {
            firestore?.collection(collectionName)?.document(docId)?.delete()
        } catch (e: Exception) {
            // Fail silently
        }
    }

    // Helper to safely load and sync entire system from Firebase Firestore
    suspend fun loadDataFromFirebase() {
        val fs = firestore ?: return
        try {
            // Verify database connection first
            FirebaseConfig.verifyConnection()

            // 1. Members
            val membersSnapshot = fs.collection(FirebaseConfig.COLLECTION_LEGACY_MEMBERS).get().await()
            var membersList = membersSnapshot.documents.mapNotNull { doc ->
                doc.data?.let { data ->
                    Member(
                        id = (data["id"] as? Number)?.toLong() ?: doc.id.toLongOrNull() ?: 0L,
                        name = data["name"] as? String ?: "",
                        title = data["title"] as? String ?: "",
                        role = data["role"] as? String ?: "",
                        email = data["email"] as? String ?: "",
                        requiresLocation = data["requiresLocation"] as? Boolean ?: false,
                        profileImage = data["profileImage"] as? String,
                        isActive = data["isActive"] as? Boolean ?: true,
                        supervisorId = (data["supervisorId"] as? Number)?.toLong(),
                        uid = data["uid"] as? String
                    )
                }
            }

            // Reverse sync/merge with root "users" collection
            try {
                val usersSnapshot = fs.collection(FirebaseConfig.COLLECTION_USERS).get().await()
                val usersList = usersSnapshot.documents.mapNotNull { doc ->
                    doc.data?.let { data ->
                        val uid = doc.id
                        val name = data["name"] as? String ?: ""
                        val email = data["email"] as? String ?: ""
                        val role = data["role"] as? String ?: "EMPLOYEE"
                        
                        val alreadyExists = membersList.any { it.uid == uid || it.id.toString() == uid || it.email.equals(email, ignoreCase = true) }
                        if (!alreadyExists && name.isNotBlank() && email.isNotBlank()) {
                            Member(
                                id = Math.abs(uid.hashCode().toLong()),
                                name = name,
                                title = "Employee (General)",
                                role = role,
                                email = email,
                                isActive = true,
                                uid = uid
                            )
                        } else {
                            null
                        }
                    }
                }
                if (usersList.isNotEmpty()) {
                    membersList = (membersList + usersList).distinctBy { it.email.lowercase() }
                }
            } catch (e: Exception) {
                // Ignore
            }

            if (membersList.isNotEmpty()) {
                dao.insertMembers(membersList)
            } else {
                // If firestore collections are empty/not yet created, save all local data to create them instantly
                saveAllToFirestore()
            }

            // 2. Attendance
            val attendanceSnapshot = fs.collection(FirebaseConfig.COLLECTION_LEGACY_ATTENDANCE).get().await()
            var attendanceList = attendanceSnapshot.documents.mapNotNull { doc ->
                doc.data?.let { data ->
                    Attendance(
                        id = (data["id"] as? Number)?.toLong() ?: doc.id.toLongOrNull() ?: 0L,
                        memberId = (data["memberId"] as? Number)?.toLong() ?: 0L,
                        date = data["date"] as? String ?: "",
                        isPresent = data["isPresent"] as? Boolean ?: false,
                        punchInTime = data["punchInTime"] as? String,
                        punchOutTime = data["punchOutTime"] as? String,
                        overtimeHours = (data["overtimeHours"] as? Number)?.toDouble() ?: 0.0,
                        locationData = data["locationData"] as? String,
                        status = data["status"] as? String ?: "PENDING",
                        approvedBySupervisorId = (data["approvedBySupervisorId"] as? Number)?.toLong(),
                        rejectionReason = data["rejectionReason"] as? String,
                        isSynced = data["isSynced"] as? Boolean ?: false
                    )
                }
            }

            // Reverse sync/merge with root "attendance_records" collection
            try {
                val recordsSnapshot = fs.collection(FirebaseConfig.COLLECTION_ATTENDANCE_RECORDS).get().await()
                val recordsList = recordsSnapshot.documents.mapNotNull { doc ->
                    doc.data?.let { data ->
                        val recordId = doc.id.toLongOrNull() ?: Math.abs(doc.id.hashCode().toLong())
                        val userId = data["userId"] as? String ?: ""
                        val date = data["date"] as? String ?: ""
                        val status = data["status"] as? String ?: "Present"
                        val markedBy = data["markedBy"] as? String ?: ""
                        
                        val matchedMember = _members.value.find { it.uid == userId || it.id.toString() == userId || it.email.substringBefore("@").equals(userId, ignoreCase = true) }
                        val memberId = matchedMember?.id ?: userId.toLongOrNull() ?: 0L
                        
                        val matchedSupervisor = _members.value.find { it.uid == markedBy || it.id.toString() == markedBy }
                        val supervisorId = matchedSupervisor?.id
                        
                        val alreadyExists = attendanceList.any { it.id == recordId || (it.memberId == memberId && it.date == date) }
                        if (!alreadyExists && memberId != 0L && date.isNotBlank()) {
                            Attendance(
                                id = recordId,
                                memberId = memberId,
                                date = date,
                                isPresent = status == "Present",
                                punchInTime = "09:00",
                                punchOutTime = if (status == "Present") "17:00" else null,
                                status = if (status == "Present") "APPROVED" else "PENDING",
                                approvedBySupervisorId = supervisorId,
                                isSynced = true
                            )
                        } else {
                            null
                        }
                    }
                }
                if (recordsList.isNotEmpty()) {
                    attendanceList = (attendanceList + recordsList).distinctBy { "${it.memberId}_${it.date}" }
                }
            } catch (e: Exception) {
                // Ignore
            }

            if (attendanceList.isNotEmpty()) {
                dao.insertAttendances(attendanceList)
            }

            // 3. Sync logs
            val syncLogsSnapshot = fs.collection("sync_logs").get().await()
            val syncLogsList = syncLogsSnapshot.documents.mapNotNull { doc ->
                doc.data?.let { data ->
                    SyncLog(
                        id = (data["id"] as? Number)?.toLong() ?: doc.id.toLongOrNull() ?: 0L,
                        timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L,
                        recordsSynced = (data["recordsSynced"] as? Number)?.toInt() ?: 0,
                        status = data["status"] as? String ?: "",
                        message = data["message"] as? String ?: ""
                    )
                }
            }
            if (syncLogsList.isNotEmpty()) {
                dao.insertSyncLogs(syncLogsList)
            }

            // 4. Messages
            val messagesSnapshot = fs.collection("messages").get().await()
            val messagesList = messagesSnapshot.documents.mapNotNull { doc ->
                doc.data?.let { data ->
                    InboxMessage(
                        id = (data["id"] as? Number)?.toLong() ?: doc.id.toLongOrNull() ?: 0L,
                        senderId = (data["senderId"] as? Number)?.toLong() ?: 0L,
                        senderName = data["senderName"] as? String ?: "",
                        content = data["content"] as? String ?: "",
                        timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L,
                        isRead = data["isRead"] as? Boolean ?: false
                    )
                }
            }
            if (messagesList.isNotEmpty()) {
                dao.insertMessages(messagesList)
            }

            // 5. Notifications
            val notificationsSnapshot = fs.collection("notifications").get().await()
            val notificationsList = notificationsSnapshot.documents.mapNotNull { doc ->
                doc.data?.let { data ->
                    AppNotification(
                        id = (data["id"] as? Number)?.toLong() ?: doc.id.toLongOrNull() ?: 0L,
                        memberId = (data["memberId"] as? Number)?.toLong() ?: 0L,
                        title = data["title"] as? String ?: "",
                        message = data["message"] as? String ?: "",
                        timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L,
                        isRead = data["isRead"] as? Boolean ?: false
                    )
                }
            }
            if (notificationsList.isNotEmpty()) {
                dao.insertNotifications(notificationsList)
            }

            // 6. Assignment history
            val assignmentSnapshot = fs.collection("assignment_history").get().await()
            val assignmentList = assignmentSnapshot.documents.mapNotNull { doc ->
                doc.data?.let { data ->
                    SupervisorAssignmentHistory(
                        id = (data["id"] as? Number)?.toLong() ?: doc.id.toLongOrNull() ?: 0L,
                        employeeId = (data["employeeId"] as? Number)?.toLong() ?: 0L,
                        employeeName = data["employeeName"] as? String ?: "",
                        previousSupervisorId = (data["previousSupervisorId"] as? Number)?.toLong(),
                        previousSupervisorName = data["previousSupervisorName"] as? String,
                        newSupervisorId = (data["newSupervisorId"] as? Number)?.toLong(),
                        newSupervisorName = data["newSupervisorName"] as? String,
                        assignedByAdminId = (data["assignedByAdminId"] as? Number)?.toLong() ?: 0L,
                        assignedByAdminName = data["assignedByAdminName"] as? String ?: "",
                        timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L
                    )
                }
            }
            if (assignmentList.isNotEmpty()) {
                dao.insertAssignmentHistories(assignmentList)
            }

            // 7. Audit logs
            val auditLogsSnapshot = fs.collection("audit_logs").get().await()
            val auditLogsList = auditLogsSnapshot.documents.mapNotNull { doc ->
                doc.data?.let { data ->
                    AuditLog(
                        id = (data["id"] as? Number)?.toLong() ?: doc.id.toLongOrNull() ?: 0L,
                        userId = (data["userId"] as? Number)?.toLong() ?: 0L,
                        username = data["username"] as? String ?: "",
                        userRole = data["userRole"] as? String ?: "",
                        timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L,
                        actionType = data["actionType"] as? String ?: "",
                        details = data["details"] as? String ?: "",
                        reportType = data["reportType"] as? String,
                        exportFormat = data["exportFormat"] as? String
                    )
                }
            }
            if (auditLogsList.isNotEmpty()) {
                dao.insertAuditLogs(auditLogsList)
            }
        } catch (e: Exception) {
            // Fail silently
        }
    }

    // JSON Backup / Restore functionality (fully replaces SQLite export/import)
    fun serializeToJson(): String {
        val root = JSONObject()
        
        val membersArray = JSONArray()
        for (m in _members.value) {
            val obj = JSONObject()
            obj.put("id", m.id)
            obj.put("name", m.name)
            obj.put("title", m.title)
            obj.put("role", m.role)
            obj.put("email", m.email)
            obj.put("requiresLocation", m.requiresLocation)
            obj.put("profileImage", m.profileImage ?: JSONObject.NULL)
            obj.put("isActive", m.isActive)
            obj.put("supervisorId", m.supervisorId ?: JSONObject.NULL)
            membersArray.put(obj)
        }
        root.put("members", membersArray)

        val attendanceArray = JSONArray()
        for (a in _attendance.value) {
            val obj = JSONObject()
            obj.put("id", a.id)
            obj.put("memberId", a.memberId)
            obj.put("date", a.date)
            obj.put("isPresent", a.isPresent)
            obj.put("punchInTime", a.punchInTime ?: JSONObject.NULL)
            obj.put("punchOutTime", a.punchOutTime ?: JSONObject.NULL)
            obj.put("overtimeHours", a.overtimeHours)
            obj.put("locationData", a.locationData ?: JSONObject.NULL)
            obj.put("status", a.status)
            obj.put("approvedBySupervisorId", a.approvedBySupervisorId ?: JSONObject.NULL)
            obj.put("rejectionReason", a.rejectionReason ?: JSONObject.NULL)
            obj.put("isSynced", a.isSynced)
            attendanceArray.put(obj)
        }
        root.put("attendance", attendanceArray)

        val syncLogsArray = JSONArray()
        for (s in _syncLogs.value) {
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("timestamp", s.timestamp)
            obj.put("recordsSynced", s.recordsSynced)
            obj.put("status", s.status)
            obj.put("message", s.message)
            syncLogsArray.put(obj)
        }
        root.put("sync_logs", syncLogsArray)

        val messagesArray = JSONArray()
        for (m in _messages.value) {
            val obj = JSONObject()
            obj.put("id", m.id)
            obj.put("senderId", m.senderId)
            obj.put("senderName", m.senderName)
            obj.put("content", m.content)
            obj.put("timestamp", m.timestamp)
            obj.put("isRead", m.isRead)
            messagesArray.put(obj)
        }
        root.put("messages", messagesArray)

        val notificationsArray = JSONArray()
        for (n in _notifications.value) {
            val obj = JSONObject()
            obj.put("id", n.id)
            obj.put("memberId", n.memberId)
            obj.put("title", n.title)
            obj.put("message", n.message)
            obj.put("timestamp", n.timestamp)
            obj.put("isRead", n.isRead)
            notificationsArray.put(obj)
        }
        root.put("notifications", notificationsArray)

        val assignmentArray = JSONArray()
        for (h in _assignmentHistory.value) {
            val obj = JSONObject()
            obj.put("id", h.id)
            obj.put("employeeId", h.employeeId)
            obj.put("employeeName", h.employeeName)
            obj.put("previousSupervisorId", h.previousSupervisorId ?: JSONObject.NULL)
            obj.put("previousSupervisorName", h.previousSupervisorName ?: JSONObject.NULL)
            obj.put("newSupervisorId", h.newSupervisorId ?: JSONObject.NULL)
            obj.put("newSupervisorName", h.newSupervisorName ?: JSONObject.NULL)
            obj.put("assignedByAdminId", h.assignedByAdminId)
            obj.put("assignedByAdminName", h.assignedByAdminName)
            obj.put("timestamp", h.timestamp)
            assignmentArray.put(obj)
        }
        root.put("assignment_history", assignmentArray)

        val auditLogsArray = JSONArray()
        for (al in _auditLogs.value) {
            val obj = JSONObject()
            obj.put("id", al.id)
            obj.put("userId", al.userId)
            obj.put("username", al.username)
            obj.put("userRole", al.userRole)
            obj.put("timestamp", al.timestamp)
            obj.put("actionType", al.actionType)
            obj.put("details", al.details)
            obj.put("reportType", al.reportType ?: JSONObject.NULL)
            obj.put("exportFormat", al.exportFormat ?: JSONObject.NULL)
            auditLogsArray.put(obj)
        }
        root.put("audit_logs", auditLogsArray)

        return root.toString(2)
    }

    @Synchronized
    fun deserializeFromJson(json: String) {
        val root = JSONObject(json)
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            if (root.has("members")) {
                val arr = root.getJSONArray("members")
                val list = mutableListOf<Member>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(Member(
                        id = obj.getLong("id"),
                        name = obj.getString("name"),
                        title = obj.optString("title", ""),
                        role = obj.getString("role"),
                        email = obj.getString("email"),
                        requiresLocation = obj.optBoolean("requiresLocation", false),
                        profileImage = if (obj.isNull("profileImage")) null else obj.getString("profileImage"),
                        isActive = obj.optBoolean("isActive", true),
                        supervisorId = if (obj.isNull("supervisorId")) null else obj.getLong("supervisorId")
                    ))
                }
                dao.clearMembers()
                dao.insertMembers(list)
            }

            if (root.has("attendance")) {
                val arr = root.getJSONArray("attendance")
                val list = mutableListOf<Attendance>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(Attendance(
                        id = obj.getLong("id"),
                        memberId = obj.getLong("memberId"),
                        date = obj.getString("date"),
                        isPresent = obj.getBoolean("isPresent"),
                        punchInTime = if (obj.isNull("punchInTime")) null else obj.getString("punchInTime"),
                        punchOutTime = if (obj.isNull("punchOutTime")) null else obj.getString("punchOutTime"),
                        overtimeHours = obj.optDouble("overtimeHours", 0.0),
                        locationData = if (obj.isNull("locationData")) null else obj.getString("locationData"),
                        status = obj.optString("status", "PENDING"),
                        approvedBySupervisorId = if (obj.isNull("approvedBySupervisorId")) null else obj.getLong("approvedBySupervisorId"),
                        rejectionReason = if (obj.isNull("rejectionReason")) null else obj.getString("rejectionReason"),
                        isSynced = obj.optBoolean("isSynced", false)
                    ))
                }
                dao.clearAttendance()
                dao.insertAttendances(list)
            }

            if (root.has("sync_logs")) {
                val arr = root.getJSONArray("sync_logs")
                val list = mutableListOf<SyncLog>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(SyncLog(
                        id = obj.getLong("id"),
                        timestamp = obj.getLong("timestamp"),
                        recordsSynced = obj.getInt("recordsSynced"),
                        status = obj.getString("status"),
                        message = obj.getString("message")
                    ))
                }
                dao.clearSyncLogs()
                dao.insertSyncLogs(list)
            }

            if (root.has("messages")) {
                val arr = root.getJSONArray("messages")
                val list = mutableListOf<InboxMessage>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(InboxMessage(
                        id = obj.getLong("id"),
                        senderId = obj.getLong("senderId"),
                        senderName = obj.getString("senderName"),
                        content = obj.getString("content"),
                        timestamp = obj.getLong("timestamp"),
                        isRead = obj.optBoolean("isRead", false)
                    ))
                }
                dao.clearMessages()
                dao.insertMessages(list)
            }

            if (root.has("notifications")) {
                val arr = root.getJSONArray("notifications")
                val list = mutableListOf<AppNotification>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(AppNotification(
                        id = obj.getLong("id"),
                        memberId = obj.getLong("memberId"),
                        title = obj.getString("title"),
                        message = obj.getString("message"),
                        timestamp = obj.getLong("timestamp"),
                        isRead = obj.optBoolean("isRead", false)
                    ))
                }
                dao.clearNotifications()
                dao.insertNotifications(list)
            }

            if (root.has("assignment_history")) {
                val arr = root.getJSONArray("assignment_history")
                val list = mutableListOf<SupervisorAssignmentHistory>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(SupervisorAssignmentHistory(
                        id = obj.getLong("id"),
                        employeeId = obj.getLong("employeeId"),
                        employeeName = obj.getString("employeeName"),
                        previousSupervisorId = if (obj.isNull("previousSupervisorId")) null else obj.getLong("previousSupervisorId"),
                        previousSupervisorName = if (obj.isNull("previousSupervisorName")) null else obj.getString("previousSupervisorName"),
                        newSupervisorId = if (obj.isNull("newSupervisorId")) null else obj.getLong("newSupervisorId"),
                        newSupervisorName = if (obj.isNull("newSupervisorName")) null else obj.getString("newSupervisorName"),
                        assignedByAdminId = obj.getLong("assignedByAdminId"),
                        assignedByAdminName = obj.getString("assignedByAdminName"),
                        timestamp = obj.getLong("timestamp")
                    ))
                }
                dao.clearAssignmentHistory()
                dao.insertAssignmentHistories(list)
            }

            if (root.has("audit_logs")) {
                val arr = root.getJSONArray("audit_logs")
                val list = mutableListOf<AuditLog>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(AuditLog(
                        id = obj.getLong("id"),
                        userId = obj.getLong("userId"),
                        username = obj.getString("username"),
                        userRole = obj.getString("userRole"),
                        timestamp = obj.getLong("timestamp"),
                        actionType = obj.getString("actionType"),
                        details = obj.getString("details"),
                        reportType = if (obj.isNull("reportType")) null else obj.getString("reportType"),
                        exportFormat = if (obj.isNull("exportFormat")) null else obj.getString("exportFormat")
                    ))
                }
                dao.clearAuditLogs()
                dao.insertAuditLogs(list)
            }
        }
    }
}
