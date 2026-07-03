package com.example

import android.os.Bundle
import android.widget.Toast
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.example.ui.fetchLocationAndClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.firestore.*
import com.example.ui.AttendanceViewModel

import com.example.ui.ReportsDashboard
import com.example.ui.TeamMembersDashboard
import com.example.ui.SettingsView
import com.example.ui.InboxView
import com.example.ui.ContactAdminView
import com.example.ui.BiometricHelper
import com.example.ui.OvertimeDashboardWidget
import com.example.ui.theme.MyApplicationTheme
import com.example.util.AuthStateObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

typealias Member = FirestoreMember
typealias Attendance = FirestoreAttendance
typealias AppNotification = FirestoreNotification
typealias InboxMessage = FirestoreMessage
typealias AuditLog = FirestoreAuditLog
typealias SupervisorAssignmentHistory = FirestoreAssignmentHistory

class MainActivity : FragmentActivity() {
    private val viewModel: AttendanceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Diagnostic Observer
        AuthStateObserver()
        
        enableEdgeToEdge()
        setContent {
            val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
            val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()

            val isDark = when (appTheme) {
                "Dark" -> true
                "Light" -> false
                else -> isSystemInDarkTheme()
            }

            // Set Locale dynamically
            LaunchedEffect(appLanguage) {
                val locale = if (appLanguage == "Arabic") java.util.Locale("ar") else java.util.Locale("en")
                java.util.Locale.setDefault(locale)
                val config = android.content.res.Configuration(resources.configuration)
                config.setLocale(locale)
                @Suppress("DEPRECATION")
                resources.updateConfiguration(config, resources.displayMetrics)
            }

            val layoutDirection = if (appLanguage == "Arabic") androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr

            MyApplicationTheme(darkTheme = isDark) {
                CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides layoutDirection) {
                    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
                    Scaffold(
                        modifier = Modifier.fillMaxSize()
                    ) { innerPadding ->
                        if (!isLoggedIn) {
                            LoginScreen(
                                viewModel = viewModel,
                                modifier = Modifier.padding(innerPadding)
                            )
                        } else {
                            MainScreen(
                                viewModel = viewModel,
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RoleBasedActionsPanel(
    role: String,
    viewModel: AttendanceViewModel,
    onAddMemberClick: () -> Unit,
    todayRecord: FirestoreAttendance?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pendingCount = viewModel.attendanceRecords.collectAsStateWithLifecycle().value.count { it.isPresent && it.status == "PENDING" && it.approvedBySupervisorId == null }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Action Hub Icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${stringResource(id = when(role){
                        "DEVELOPER" -> R.string.developer
                        "SUPERSU" -> R.string.supersu
                        "ADMIN" -> R.string.admin
                        "SUPERVISOR" -> R.string.supervisor
                        else -> R.string.employee
                    })} ${stringResource(id = R.string.control_panel)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = "Detected role: $role. Operations are dynamically tailored for your permissions level.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Dynamic Buttons Layout based on role
            when (role) {
                "SUPERSU" -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onAddMemberClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("dash_action_add_admin"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Admin", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Admin", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            FilledTonalButton(
                                onClick = {
                                    viewModel.synchronizeCloud()
                                    Toast.makeText(context, "Global Sync triggered!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("dash_action_global_sync")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Global Sync", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Global Sync", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                "ADMIN" -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onAddMemberClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("dash_action_add_member"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Member", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(id = R.string.add_member), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            FilledTonalButton(
                                onClick = {
                                    Toast.makeText(context, "Spreadsheet active. Configure links via Excel Link tab.", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("dash_action_excel_link")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Excel Connection", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Excel DB Link", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.exportToPDF(context) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("dash_action_export_pdf"),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.List, contentDescription = "PDF Export", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.synchronizeCloud()
                                    Toast.makeText(context, "Cloud sync triggered successfully!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("dash_action_sync"),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Cloud Sync", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cloud Sync", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                "SUPERVISOR" -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    Toast.makeText(context, "Roster checklist is fully active below.", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("dash_action_roster")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Roster Check", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Roster Check", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    viewModel.approveAllPendingRecords()
                                    Toast.makeText(context, "Approved all pending records!", Toast.LENGTH_SHORT).show()
                                },
                                enabled = pendingCount > 0,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("dash_action_approve_all"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(Icons.Default.Star, contentDescription = "Bulk Approve", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Approve All ($pendingCount)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                else -> { // EMPLOYEE or NORMAL
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { viewModel.employeeClockIn() },
                                enabled = todayRecord == null,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("dash_action_clock_in"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Clock In", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Clock In", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { viewModel.employeeClockOut(0.0) },
                                enabled = todayRecord != null && todayRecord.punchOutTime == null,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("dash_action_clock_out"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(Icons.Default.ExitToApp, contentDescription = "Clock Out", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Clock Out", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    if (todayRecord != null && todayRecord.punchOutTime == null) {
                                        viewModel.employeeClockOut(1.0)
                                        Toast.makeText(context, "Clocked out with 1 hr overtime!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Overtime only logs on active work shifts.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = todayRecord != null && todayRecord.punchOutTime == null,
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(44.dp)
                                    .testTag("dash_action_log_ot")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Log 1h Overtime", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Log 1h Overtime", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    Toast.makeText(context, "Review history details listed below.", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(0.8f)
                                    .height(44.dp)
                                    .testTag("dash_action_view_log")
                            ) {
                                Icon(Icons.Default.List, contentDescription = "View Logs", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("History", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: AttendanceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val members by viewModel.members.collectAsStateWithLifecycle()
    val attendanceRecords by viewModel.attendanceRecords.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val isOfflineMode by viewModel.isOfflineMode.collectAsStateWithLifecycle()
    val syncingState by viewModel.syncingState.collectAsStateWithLifecycle()
    val exportResult by viewModel.exportResult.collectAsStateWithLifecycle()
    val isFirebaseConnected by viewModel.isFirebaseConnected.collectAsStateWithLifecycle()

    var showAddMemberDialog by remember { mutableStateOf(false) }

    // Centralized Navigation Tab States for bottom rounded dock
    var adminTab by remember { mutableStateOf(0) }
    var supervisorTab by remember { mutableStateOf(0) }
    var employeeTab by remember { mutableStateOf(0) }
    var superuserTab by remember { mutableStateOf(0) }
    var developerTab by remember { mutableStateOf(0) }

    // Display messages from export or sync
    LaunchedEffect(exportResult) {
        exportResult?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearExportResult()
        }
    }

    val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
    val isDark = when (appTheme) {
        "Dark" -> true
        "Light" -> false
        else -> isSystemInDarkTheme()
    }
    val appleBackgroundBrush = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(Color(0xFF0F0F1A), Color(0xFF1E1F29), Color(0xFF121214))
        } else {
            listOf(Color(0xFFE2E9F3), Color(0xFFF3EDF5), Color(0xFFEAF5ED))
        }
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(appleBackgroundBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 110.dp) // Leave spacious negative space for the bottom dock
        ) {
            // App Header & Branding Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_attendance_hero_1782803089061),
                contentDescription = "Attendance Banner",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Success Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.app_name),
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Offline-first check-in & report sync",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
            }
        }

        // Connectivity & Offline Status Banner
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = when {
                isOfflineMode -> MaterialTheme.colorScheme.errorContainer
                !isFirebaseConnected -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.primaryContainer
            },
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = when {
                            isOfflineMode -> Icons.Default.Warning
                            !isFirebaseConnected -> Icons.Default.Refresh
                            else -> Icons.Default.Cloud
                        },
                        contentDescription = "Status Icon",
                        tint = when {
                            isOfflineMode -> MaterialTheme.colorScheme.error
                            !isFirebaseConnected -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = when {
                                isOfflineMode -> "Working Offline"
                                !isFirebaseConnected -> "Cloud Disconnected"
                                else -> "Cloud Connected"
                            },
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when {
                                isOfflineMode -> "Manual offline override active."
                                !isFirebaseConnected -> "Could not reach Firestore. Check internet."
                                else -> "Synchronized with secure cloud backup."
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                if (!isOfflineMode && !isFirebaseConnected) {
                    IconButton(onClick = { viewModel.checkFirebaseConnection() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Retry Connection")
                    }
                } else {
                    Switch(
                        checked = isOfflineMode,
                        onCheckedChange = { viewModel.toggleOfflineMode() },
                        modifier = Modifier.testTag("offline_toggle")
                    )
                }
            }
        }

        // Active Profile Card
        currentUser?.let { activeMember ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    when (activeMember.role) {
                                        "ADMIN" -> MaterialTheme.colorScheme.primary
                                        "SUPERVISOR" -> MaterialTheme.colorScheme.secondary
                                        else -> MaterialTheme.colorScheme.tertiary
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Active Profile Icon",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = activeMember.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "${activeMember.role} • ${activeMember.email}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { viewModel.logout() },
                        modifier = Modifier.testTag("logout_button_profile_card")
                    ) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = "Lock", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Logout", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Context-driven Layout based on Active User Profile
        currentUser?.let { activeMember ->
            Text(
                text = "Workspace Layer: ${activeMember.role}",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            val todayRecord = attendanceRecords.filter { it.memberId == activeMember.id }.firstOrNull { it.date == viewModel.getCurrentDateString() }
            RoleBasedActionsPanel(
                role = activeMember.role,
                viewModel = viewModel,
                onAddMemberClick = { showAddMemberDialog = true },
                todayRecord = todayRecord
            )

            HorizontalDivider(
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            when (activeMember.role) {
                "DEVELOPER", "SUPERSU" -> {
                    val currentTab = if (activeMember.role == "DEVELOPER") developerTab else superuserTab
                    val setTab: (Int) -> Unit = { if (activeMember.role == "DEVELOPER") developerTab = it else superuserTab = it }
                    DeveloperView(
                        viewModel = viewModel,
                        activeMember = activeMember,
                        members = members,
                        currentTab = currentTab,
                        onTabSelected = setTab
                    )
                }
                "ADMIN" -> {
                    AdminView(
                        viewModel = viewModel,
                        activeMember = activeMember,
                        members = members,
                        attendanceRecords = attendanceRecords,
                        syncingState = syncingState,
                        onAddMemberClick = { showAddMemberDialog = true },
                        currentTab = adminTab,
                        onTabSelected = { adminTab = it }
                    )
                }
                "SUPERVISOR" -> {
                    SupervisorView(
                        viewModel = viewModel,
                        activeMember = activeMember,
                        members = members,
                        attendanceRecords = attendanceRecords,
                        activeSubTab = supervisorTab,
                        onSubTabSelected = { supervisorTab = it }
                    )
                }
                "EMPLOYEE" -> {
                    EmployeeView(
                        viewModel = viewModel,
                        activeMember = activeMember,
                        attendanceRecords = attendanceRecords,
                        currentTab = employeeTab,
                        onTabSelected = { employeeTab = it }
                    )
                }
                else -> {
                    NormalUserView(
                        activeMember = activeMember,
                        attendanceRecords = attendanceRecords,
                        members = members
                    )
                }
            }
        } ?: run {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
    }

    // Floating rounded dock at the bottom of the screen!
    currentUser?.let { activeMember ->
        when (activeMember.role) {
            "DEVELOPER" -> {
                GlassDock(
                    isDark = isDark,
                    tabs = listOf(
                        stringResource(R.string.overview),
                        stringResource(R.string.admins),
                        stringResource(R.string.sync),
                        "Activity Logs",
                        stringResource(R.string.settings)
                    ),
                    selectedTab = developerTab,
                    onTabSelected = { developerTab = it },
                    icons = listOf(Icons.Default.Home, Icons.Default.Person, Icons.Default.Refresh, Icons.Default.List, Icons.Default.Settings),
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
            "SUPERSU" -> {
                GlassDock(
                    isDark = isDark,
                    tabs = listOf(
                        stringResource(R.string.overview),
                        stringResource(R.string.admins),
                        stringResource(R.string.sync),
                        stringResource(R.string.settings)
                    ),
                    selectedTab = superuserTab,
                    onTabSelected = { superuserTab = it },
                    icons = listOf(Icons.Default.Home, Icons.Default.Person, Icons.Default.Refresh, Icons.Default.Settings),
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
            "ADMIN" -> {
                GlassDock(
                    isDark = isDark,
                    tabs = listOf(
                        stringResource(R.string.overview),
                        stringResource(R.string.admins),
                        stringResource(R.string.sync),
                        stringResource(R.string.settings),
                        stringResource(R.string.inbox),
                        "Reports"
                    ),
                    selectedTab = adminTab,
                    onTabSelected = { adminTab = it },
                    icons = listOf(Icons.Default.Home, Icons.Default.Person, Icons.Default.Refresh, Icons.Default.Settings, Icons.Default.MailOutline, Icons.Default.Assessment),
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
            "SUPERVISOR" -> {
                GlassDock(
                    isDark = isDark,
                    tabs = listOf("Roster", "Settings", "Reports"),
                    selectedTab = supervisorTab,
                    onTabSelected = { supervisorTab = it },
                    icons = listOf(Icons.Default.Check, Icons.Default.Settings, Icons.Default.Assessment),
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
            "EMPLOYEE" -> {
                GlassDock(
                    isDark = isDark,
                    tabs = listOf("Dashboard", "Contact Admin", "Settings"),
                    selectedTab = employeeTab,
                    onTabSelected = { employeeTab = it },
                    icons = listOf(Icons.Default.Home, Icons.Default.MailOutline, Icons.Default.Settings),
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

    // Add Member Dialog for Admin
    if (showAddMemberDialog) {
        var nameInput by remember { mutableStateOf("") }
        var titleInput by remember { mutableStateOf("") }
        var emailInput by remember { mutableStateOf("") }
        var requiresLocation by remember { mutableStateOf(false) }
        var roleSelection by remember { mutableStateOf("EMPLOYEE") }

        Dialog(onDismissRequest = { showAddMemberDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Add Team Member",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Full Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("Job Title (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Email Address (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = requiresLocation,
                            onCheckedChange = { requiresLocation = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Require Location for Attendance",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Select Control Layer Role",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val availableRoles = if (currentUser?.role == "DEVELOPER" || currentUser?.role == "SUPERSU") {
                            listOf("EMPLOYEE", "SUPERVISOR", "ADMIN")
                        } else {
                            listOf("EMPLOYEE", "SUPERVISOR")
                        }
                        availableRoles.forEach { r ->
                            val isSel = roleSelection == r
                            OutlinedButton(
                                onClick = { roleSelection = r },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    contentColor = if (isSel) Color.White else MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = r,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showAddMemberDialog = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (nameInput.isNotBlank()) {
                                    viewModel.addTeamMember(nameInput, titleInput, roleSelection, emailInput, requiresLocation)
                                    showAddMemberDialog = false
                                }
                            },
                            modifier = Modifier.testTag("add_member_button")
                        ) {
                            Text(stringResource(id = R.string.add_member))
                        }
                    }
                }
            }
        }
    }
}

// ---------------------- ADMIN VIEW ----------------------
@Composable
fun AdminView(
    viewModel: AttendanceViewModel,
    activeMember: Member,
    members: List<Member>,
    attendanceRecords: List<Attendance>,
    syncingState: Boolean,
    onAddMemberClick: () -> Unit,
    currentTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val context = LocalContext.current

    var resetPasswordMember by remember { mutableStateOf<Member?>(null) }
    var newPasswordInput by remember { mutableStateOf("") }
    var generatedRecoveryCode by remember { mutableStateOf("") }

    var editMember by remember { mutableStateOf<Member?>(null) }
    var assignSupervisorEmployee by remember { mutableStateOf<Member?>(null) }

    val linkedExcelFile by viewModel.linkedExcelFile.collectAsStateWithLifecycle()
    val excelLinkStatus by viewModel.excelLinkStatus.collectAsStateWithLifecycle()
    val excelImportResult by viewModel.excelImportResult.collectAsStateWithLifecycle()

    val tabLabels = listOf(
        stringResource(R.string.overview_dashboard),
        stringResource(R.string.team_directory),
        stringResource(R.string.cloud_backups),
        stringResource(R.string.settings),
        stringResource(R.string.inbox),
        "Reports"
    )
    val activeLabel = tabLabels.getOrElse(currentTab) { "" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = activeLabel,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    when (currentTab) {
        0 -> { // Admin Overview
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                // Quick Report Buttons
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Admin Reports:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.exportToPDF(context) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Default.Star, contentDescription = "Overtime", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("OT", fontSize = 11.sp)
                            }
                            Button(
                                onClick = { viewModel.exportToPDF(context) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "PDF", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Full PDF", fontSize = 11.sp)
                            }
                        }
                    }
                }

                // Overtime Chart
                OvertimeDashboardWidget(members, attendanceRecords)
                Spacer(modifier = Modifier.height(16.dp))

                // Key metrics row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        title = "Total Members",
                        value = "${members.size}",
                        icon = Icons.Default.Person,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Present Today",
                        value = "${attendanceRecords.count { it.date == viewModel.getCurrentDateString() && it.isPresent }}",
                        icon = Icons.Default.Check,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        title = "Total Overtime",
                        value = "${attendanceRecords.sumOf { it.overtimeHours }}h",
                        icon = Icons.Default.Settings,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Pending Approvals",
                        value = "${attendanceRecords.count { it.isPresent && it.status == "PENDING" && it.approvedBySupervisorId == null }}",
                        icon = Icons.Default.Warning,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Pending Approvals Panel
                Text(
                    text = "Pending Supervisor Approvals",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val pendingApprovals = attendanceRecords.filter { it.isPresent && it.status == "PENDING" && it.approvedBySupervisorId == null }
                if (pendingApprovals.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "All employee attendances have been approved.",
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    pendingApprovals.forEach { record ->
                        val empName = members.firstOrNull { it.id == record.memberId }?.name ?: "Unknown"
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = empName, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "Date: ${record.date} | Overtime: ${record.overtimeHours} hrs",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { viewModel.rejectAttendanceRecord(record.id, "Manually rejected by administrator.") },
                                        modifier = Modifier.height(36.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Text("Reject", fontSize = 11.sp)
                                    }
                                    Button(
                                        onClick = { viewModel.approveAttendanceRecord(record.id) },
                                        modifier = Modifier.height(36.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text("Approve", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        1 -> { // Team Directory Tab
            TeamMembersDashboard(
                members = members.filter { it.role != "ADMIN" && it.role != "DEVELOPER" && it.role != "SUPERSU" },
                onAddMemberClick = onAddMemberClick,
                onRemoveMemberClick = { viewModel.removeTeamMember(it) },
                onResetPasswordClick = { member ->
                    resetPasswordMember = member
                    newPasswordInput = ""
                    generatedRecoveryCode = (100000..999999).random().toString()
                },
                onEditMemberClick = { member ->
                    editMember = member
                },
                activeMemberRole = activeMember.role,
                supervisors = members.filter { it.role == "SUPERVISOR" },
                onAssignSupervisorClick = { employee ->
                    assignSupervisorEmployee = employee
                }
            )
        }
        99 -> { // Reports & Exports Tab (Removed from dock)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Header & Intro Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Reports Logo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Payroll Reports Generator",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Generate and export monthly records in standard PDF & Excel formats for audit and payroll processing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Metric Statistics Cards
                Text(
                    text = "Key Metrics",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Compute Stats
                val totalRecords = attendanceRecords.size
                val presentRecords = attendanceRecords.count { it.isPresent }
                val attendanceRate = if (totalRecords > 0) (presentRecords * 100) / totalRecords else 0
                val totalOvertime = attendanceRecords.sumOf { it.overtimeHours }
                val pendingApprovalsCount = attendanceRecords.count { it.isPresent && it.approvedBySupervisorId == null }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Total Days
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Total Logs", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("$totalRecords", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Attendance Rate
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Attendance %", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("$attendanceRate%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Total Overtime
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Overtime Sum", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${totalOvertime}h", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        }
                    }

                    // Pending Approvals
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = if (pendingApprovalsCount > 0) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (pendingApprovalsCount > 0) MaterialTheme.colorScheme.error.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Unapproved Logs", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$pendingApprovalsCount",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (pendingApprovalsCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // EXPORT BUTTONS
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.exportToExcel(context) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("export_excel_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Excel", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export Excel", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { viewModel.exportToPDF(context) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("export_pdf_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "PDF", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // INTERACTIVE REPORT VIEWER
                Text(
                    text = "Interactive Attendance Report",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                var reportsFilterMemberId by remember { mutableStateOf<String?>(null) }
                var reportsFilterApprovedOnly by remember { mutableStateOf(false) }

                // Filter Controls (M3 Chips Row)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CustomFilterChip(
                        selected = reportsFilterMemberId == null && !reportsFilterApprovedOnly,
                        onClick = {
                            reportsFilterMemberId = null
                            reportsFilterApprovedOnly = false
                        },
                        label = "All Records"
                    )
                    CustomFilterChip(
                        selected = reportsFilterApprovedOnly,
                        onClick = {
                            reportsFilterApprovedOnly = !reportsFilterApprovedOnly
                        },
                        label = "Approved Only"
                    )

                    members.forEach { m ->
                        CustomFilterChip(
                            selected = reportsFilterMemberId == m.id,
                            onClick = {
                                reportsFilterMemberId = if (reportsFilterMemberId == m.id) null else m.id
                            },
                            label = m.name
                        )
                    }
                }

                // Individual Export Action
                if (reportsFilterMemberId != null) {
                    Button(
                        onClick = { viewModel.exportToPDF(context, employeeId = reportsFilterMemberId) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Icon(Icons.Default.Share, "Export")
                        Spacer(modifier = Modifier.width(8.dp))
                        val name = members.find { it.id == reportsFilterMemberId }?.name ?: "Employee"
                        Text("Export Attendance for $name")
                    }
                }

                // Filtered records list
                val reportsFilteredRecords = attendanceRecords.filter { record ->
                    val matchesMember = reportsFilterMemberId == null || record.memberId == reportsFilterMemberId
                    val matchesApproved = !reportsFilterApprovedOnly || record.approvedBySupervisorId != null
                    matchesMember && matchesApproved
                }

                if (reportsFilteredRecords.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "No records match the current filters.",
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        reportsFilteredRecords.forEach { record ->
                            val employee = members.firstOrNull { it.id == record.memberId }
                            val empName = employee?.name ?: "Unknown Employee"
                            val empRole = employee?.role ?: "EMPLOYEE"
                            val isApproved = record.approvedBySupervisorId != null

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSystemInDarkTheme()) Color(0xFF1E1E1E).copy(alpha = 0.45f) else Color.White.copy(alpha = 0.6f)
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = empName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Role: $empRole | Date: ${record.date}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        // Status badge
                                        Badge(
                                            containerColor = if (record.isPresent) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.errorContainer
                                            }
                                        ) {
                                            Text(
                                                text = if (record.isPresent) "PRESENT" else "ABSENT",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                color = if (record.isPresent) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onErrorContainer
                                                }
                                            )
                                        }
                                    }

                                    if (record.isPresent) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                        Spacer(modifier = Modifier.height(6.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Punch In",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "In: ${record.punchInTime ?: "--:--"}",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }

                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Punch Out",
                                                        tint = MaterialTheme.colorScheme.secondary,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "Out: ${record.punchOutTime ?: "--:--"}",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }

                                                if (record.overtimeHours > 0.0) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            imageVector = Icons.Default.Star,
                                                            contentDescription = "Overtime",
                                                            tint = MaterialTheme.colorScheme.tertiary,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = "+${record.overtimeHours}h OT",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.tertiary
                                                        )
                                                    }
                                                }
                                            }

                                            // Approval stamp
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = if (isApproved) Icons.Default.CheckCircle else Icons.Default.Warning,
                                                    contentDescription = if (isApproved) "Approved" else "Pending Approval",
                                                    tint = if (isApproved) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = if (isApproved) "APPROVED ✓" else "PENDING",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isApproved) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // DATA PURGE & CLEANING SECTION
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Warning",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Database Cleanse & Purge",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Instantly wipe out all legacy dummy team members and associated records, and clean unknown or unapproved supervisor checkpoints.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                viewModel.purgeDummyDataAndUnknownApprovals { membersDeleted, recordsDeleted ->
                                    Toast.makeText(
                                        context,
                                        "Database Cleansed! Purged $membersDeleted dummy members & $recordsDeleted records.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("purge_data_button")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Purge DB", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Purge Dummy Data & Unknown Approvals", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // CONSOLIDATED WORK HOURS WITH FILTERS
                Text(
                    text = "Report Logs Viewer",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // State variables for filter
                var searchText by remember { mutableStateOf("") }
                var selectedStatusFilter by remember { mutableStateOf("ALL") } // ALL, PRESENT, ABSENT
                var selectedApprovalFilter by remember { mutableStateOf("ALL") } // ALL, APPROVED, PENDING
                var selectedEmployeeIdFilter by remember { mutableStateOf<String?>(null) } // null means All

                // Dynamic filter controls
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Filters", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

                        // Search Field
                        OutlinedTextField(
                            value = searchText,
                            onValueChange = { searchText = it },
                            placeholder = { Text("Search by employee name...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .testTag("report_search_name"),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon", modifier = Modifier.size(18.dp)) },
                            trailingIcon = if (searchText.isNotEmpty()) {
                                {
                                    IconButton(onClick = { searchText = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear Search", modifier = Modifier.size(18.dp))
                                    }
                                }
                            } else null
                        )

                        // Employee Filter Chips Row
                        Text("Filter by Employee", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CustomFilterChip(
                                selected = selectedEmployeeIdFilter == null,
                                onClick = { selectedEmployeeIdFilter = null },
                                label = "All Staff"
                            )
                            members.forEach { m ->
                                CustomFilterChip(
                                    selected = selectedEmployeeIdFilter == m.id,
                                    onClick = { selectedEmployeeIdFilter = m.id },
                                    label = m.name
                                )
                            }
                        }

                        // Presence Status Filter Chips Row
                        Text("Presence Status", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CustomFilterChip(
                                selected = selectedStatusFilter == "ALL",
                                onClick = { selectedStatusFilter = "ALL" },
                                label = "All Statuses"
                            )
                            CustomFilterChip(
                                selected = selectedStatusFilter == "PRESENT",
                                onClick = { selectedStatusFilter = "PRESENT" },
                                label = "Present"
                            )
                            CustomFilterChip(
                                selected = selectedStatusFilter == "ABSENT",
                                onClick = { selectedStatusFilter = "ABSENT" },
                                label = "Absent"
                            )
                        }

                        // Approval Status Filter Chips Row
                        Text("Approval Status", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CustomFilterChip(
                                selected = selectedApprovalFilter == "ALL",
                                onClick = { selectedApprovalFilter = "ALL" },
                                label = "All Approvals"
                            )
                            CustomFilterChip(
                                selected = selectedApprovalFilter == "APPROVED",
                                onClick = { selectedApprovalFilter = "APPROVED" },
                                label = "Approved"
                            )
                            CustomFilterChip(
                                selected = selectedApprovalFilter == "PENDING",
                                onClick = { selectedApprovalFilter = "PENDING" },
                                label = "Pending"
                            )
                        }
                    }
                }

                // Filter Logic
                val filteredRecords = attendanceRecords.filter { rec ->
                    val member = members.firstOrNull { it.id == rec.memberId }
                    val empName = member?.name ?: "Unknown"

                    val matchesSearch = searchText.isEmpty() || empName.contains(searchText, ignoreCase = true)
                    val matchesEmployee = selectedEmployeeIdFilter == null || rec.memberId == selectedEmployeeIdFilter

                    val matchesStatus = when (selectedStatusFilter) {
                        "PRESENT" -> rec.isPresent
                        "ABSENT" -> !rec.isPresent
                        else -> true
                    }

                    val matchesApproval = when (selectedApprovalFilter) {
                        "APPROVED" -> rec.isPresent && rec.approvedBySupervisorId != null
                        "PENDING" -> rec.isPresent && rec.approvedBySupervisorId == null
                        else -> true
                    }

                    matchesSearch && matchesEmployee && matchesStatus && matchesApproval
                }

                // List of filtered records
                if (filteredRecords.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "No matching attendance records found.",
                            modifier = Modifier
                                .padding(32.dp)
                                .fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    filteredRecords.forEach { record ->
                        val member = members.firstOrNull { it.id == record.memberId }
                        val empName = member?.name ?: "Unknown"
                        val empRole = member?.role ?: "UNKNOWN"

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .testTag("report_item_card_${record.id}"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when (empRole) {
                                                    "ADMIN" -> MaterialTheme.colorScheme.primaryContainer
                                                    "SUPERVISOR" -> MaterialTheme.colorScheme.secondaryContainer
                                                    else -> MaterialTheme.colorScheme.tertiaryContainer
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = empName.take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            color = when (empRole) {
                                                "ADMIN" -> MaterialTheme.colorScheme.onPrimaryContainer
                                                "SUPERVISOR" -> MaterialTheme.colorScheme.onSecondaryContainer
                                                else -> MaterialTheme.colorScheme.onTertiaryContainer
                                            },
                                            fontSize = 16.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = empName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "$empRole • ${member?.email ?: "No Email"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Badge(
                                        containerColor = if (record.isPresent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                                    ) {
                                        Text(
                                            text = if (record.isPresent) "PRESENT" else "ABSENT",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (record.isPresent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("DATE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(record.date, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    }

                                    Column {
                                        Text("SHIFT PUNCH", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            text = if (record.isPresent) "${record.punchInTime ?: "09:00"} -> ${record.punchOutTime ?: "-"}" else "N/A",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("OVERTIME", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            text = "${record.overtimeHours} hours",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (record.overtimeHours > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                val approvedByMember = members.firstOrNull { it.id == record.approvedBySupervisorId }
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (record.isPresent) {
                                            if (approvedByMember != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                                        } else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (!record.isPresent) Icons.Default.Info
                                            else if (approvedByMember != null) Icons.Default.CheckCircle
                                            else Icons.Default.Warning,
                                            contentDescription = "Approval Icon",
                                            tint = if (!record.isPresent) MaterialTheme.colorScheme.onSurfaceVariant
                                            else if (approvedByMember != null) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (!record.isPresent) "Absent records do not require approval."
                                            else if (approvedByMember != null) "Approved by: ${approvedByMember.name}"
                                            else "Pending Supervisor Approval",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = if (!record.isPresent) MaterialTheme.colorScheme.onSurfaceVariant
                                            else if (approvedByMember != null) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        2 -> { // Cloud Backup & Sync Log Tab
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Secure Cloud Database Sync",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Back up offline attendance checkmarks to secure SQL cloud backup. Resolves packet conflicts automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { viewModel.synchronizeCloud() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cloud_sync_button"),
                            enabled = !syncingState
                        ) {
                            if (syncingState) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Syncing Backup...")
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Sync")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Trigger Manual Cloud Backup")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val importLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent(),
                    onResult = { uri ->
                        if (uri != null) {
                            viewModel.importDatabase(context, uri)
                        }
                    }
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Local Database Backup & Restore",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Export the raw SQLite database for external use, or import an existing backup. App restart required after import.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { importLauncher.launch("*/*") },
                                modifier = Modifier.weight(1f).testTag("import_db_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Import DB", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Import DB", fontSize = 12.sp)
                            }
                            Button(
                                onClick = { viewModel.exportDatabase(context) },
                                modifier = Modifier.weight(1f).testTag("export_db_button")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Export DB", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Export DB", fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Cloud Sync Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Firestore Real-time Sync Active",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "All attendance records and member data are automatically synchronized with the cloud database in real-time.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
        3 -> { // Settings
            SettingsView(activeMember = activeMember, viewModel = viewModel)
        }
        4 -> { // Inbox
            val messages by viewModel.messages.collectAsStateWithLifecycle()
            InboxView(messages = messages, onMarkAsRead = { viewModel.markMessageAsRead(it) })
        }
        5 -> { // Reports
            ReportsDashboard(viewModel = viewModel, activeMember = activeMember)
        }
    }

    if (assignSupervisorEmployee != null) {
        val employee = assignSupervisorEmployee!!
        val supervisorList = members.filter { it.role == "SUPERVISOR" }
        AlertDialog(
            onDismissRequest = { assignSupervisorEmployee = null },
            title = { Text("Assign Supervisor to ${employee.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Select a supervisor to oversee ${employee.name}'s attendance and approvals. History will be kept for audit logs.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    // Option to unassign supervisor
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.assignSupervisorToEmployee(employee.id, null)
                                Toast.makeText(context, "Supervisor unassigned", Toast.LENGTH_SHORT).show()
                                assignSupervisorEmployee = null
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (employee.supervisorId == null) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "None (Unassigned)",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            if (employee.supervisorId == null) {
                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    // List of supervisors
                    supervisorList.forEach { supervisor ->
                        val isSelected = employee.supervisorId == supervisor.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.assignSupervisorToEmployee(employee.id, supervisor.id)
                                    Toast.makeText(context, "Assigned ${supervisor.name} as Supervisor", Toast.LENGTH_SHORT).show()
                                    assignSupervisorEmployee = null
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = supervisor.name,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { assignSupervisorEmployee = null }) {
                    Text("Close")
                }
            }
        )
    }

    if (resetPasswordMember != null) {
        val targetMember = resetPasswordMember!!
        AlertDialog(
            onDismissRequest = { resetPasswordMember = null },
            title = {
                Text(
                    text = "Security Reset for ${targetMember.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Override this employee's password directly, or share the secure recovery code below so they can perform a self-service reset on the login screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Text(
                        text = "Option 1: Override Password Directly",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = newPasswordInput,
                        onValueChange = { newPasswordInput = it },
                        label = { Text("New Password") },
                        placeholder = { Text("Enter a secure password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("admin_reset_password_input")
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Option 2: Share Self-Service Recovery Code",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Temporary Recovery Code",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = generatedRecoveryCode,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPasswordInput.isNotEmpty()) {
                            viewModel.setMemberPassword(targetMember.id, newPasswordInput)
                            Toast.makeText(context, "Password for ${targetMember.name} updated successfully!", Toast.LENGTH_SHORT).show()
                        }
                        // Also register recovery code
                        viewModel.setMemberRecoveryCode(targetMember.id, generatedRecoveryCode)
                        Toast.makeText(context, "Temporary recovery code registered!", Toast.LENGTH_SHORT).show()
                        resetPasswordMember = null
                    },
                    modifier = Modifier.testTag("admin_save_reset_button")
                ) {
                    Text("Apply & Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { resetPasswordMember = null },
                    modifier = Modifier.testTag("admin_cancel_reset_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    editMember?.let { targetMember ->
        var editName by remember { mutableStateOf(targetMember.name) }
        var editTitle by remember { mutableStateOf(targetMember.title) }
        var editRole by remember { mutableStateOf(targetMember.role) }
        var editEmail by remember { mutableStateOf(targetMember.email) }
        var editRequiresLocation by remember { mutableStateOf(targetMember.requiresLocation) }

        Dialog(onDismissRequest = { editMember = null }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Edit Team Member",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Full Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Job Title (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = editEmail,
                        onValueChange = { editEmail = it },
                        label = { Text("Email Address (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = editRequiresLocation,
                            onCheckedChange = { editRequiresLocation = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Require Location for Attendance",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Select Control Layer Role",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val availableRoles = if (activeMember.role == "DEVELOPER" || activeMember.role == "SUPERSU") {
                            listOf("EMPLOYEE", "SUPERVISOR", "ADMIN")
                        } else {
                            listOf("EMPLOYEE", "SUPERVISOR")
                        }
                        availableRoles.forEach { role ->
                            FilterChip(
                                selected = (editRole == role),
                                onClick = { editRole = role },
                                label = { Text(role, fontSize = 10.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { editMember = null }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (editName.isNotBlank()) {
                                    viewModel.editTeamMember(targetMember, editName, editTitle, editRole, editEmail, editRequiresLocation)
                                    editMember = null
                                }
                            }
                        ) {
                            Text("Save Changes")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeveloperView(
    viewModel: AttendanceViewModel,
    activeMember: Member,
    members: List<Member>,
    currentTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabLabels = if (activeMember.role == "DEVELOPER") {
        listOf(
            stringResource(R.string.overview),
            stringResource(R.string.admins),
            "Firestore Settings",
            "Activity Logs",
            stringResource(R.string.settings),
            "Reports"
        )
    } else {
        listOf(
            stringResource(R.string.overview),
            stringResource(R.string.admins),
            "Firestore Settings",
            stringResource(R.string.settings),
            "Reports"
        )
    }
    val activeLabel = tabLabels.getOrElse(currentTab) { "" }
    val linkedExcelFile by viewModel.linkedExcelFile.collectAsStateWithLifecycle()
    val excelLinkStatus by viewModel.excelLinkStatus.collectAsStateWithLifecycle()

    // Dialog state
    var showAddAdminDialog by remember { mutableStateOf(false) }
    var editAdminMember by remember { mutableStateOf<Member?>(null) }
    var resetPasswordAdminMember by remember { mutableStateOf<Member?>(null) }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = activeLabel,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        val actualTab = if (activeMember.role == "SUPERSU") {
            when (currentTab) {
                0 -> 0
                1 -> 1
                2 -> 2
                3 -> 4
                4 -> 5
                else -> currentTab
            }
        } else {
            currentTab
        }

        when (actualTab) {
            0 -> { // Overview
                MetricCard(
                    title = stringResource(R.string.admins),
                    value = "${members.count { it.role == "ADMIN" }}",
                    icon = Icons.Default.Person,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            1 -> { // Admin Management
                val admins = members.filter { it.role == "ADMIN" }
                
                // Add Admin Floating action button / Header button
                Button(
                    onClick = { showAddAdminDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add New Admin")
                }

                if (admins.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "No Admins registered. Click the button above to add one.",
                            modifier = Modifier.padding(24.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    admins.forEach { admin ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = admin.name,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                color = if (admin.isActive) Color(0xFF4CAF50).copy(alpha = 0.15f) else Color.Red.copy(alpha = 0.15f),
                                                contentColor = if (admin.isActive) Color(0xFF2E7D32) else Color.Red,
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = if (admin.isActive) "ACTIVE" else "INACTIVE",
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Text(text = admin.email, style = MaterialTheme.typography.bodySmall)
                                        if (admin.title.isNotBlank()) {
                                            Text(text = admin.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Status switcher toggle
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(
                                            checked = admin.isActive,
                                            onCheckedChange = { viewModel.toggleMemberActive(admin) }
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (admin.isActive) "Deactivate" else "Activate",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }

                                    // Action buttons
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = { editAdminMember = admin },
                                            modifier = Modifier.testTag("edit_admin_${admin.id}")
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit Admin Details")
                                        }
                                        IconButton(
                                            onClick = { resetPasswordAdminMember = admin },
                                            modifier = Modifier.testTag("reset_password_admin_${admin.id}")
                                        ) {
                                            Icon(Icons.Default.Lock, contentDescription = "Reset Password")
                                        }
                                        IconButton(
                                            onClick = { viewModel.removeTeamMember(admin) },
                                            modifier = Modifier.testTag("delete_admin_${admin.id}")
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Admin", tint = Color.Red)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            2 -> { // Firestore Settings
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Firestore Configuration", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "This application is now powered by Google Firebase Firestore. Database synchronization is handled automatically by the cloud infrastructure.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.synchronizeCloud() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Manually Trigger Sync Refresh")
                        }
                    }
                }
            }
            3 -> { // Activity Logs (Placeholder)
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "System audit logs are now managed via Firestore Audit Collection. Implementation for this view is pending.",
                        modifier = Modifier.padding(24.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            4 -> { // Settings
                SettingsView(activeMember = activeMember, viewModel = viewModel)
            }
            5 -> { // Reports
                ReportsDashboard(viewModel = viewModel, activeMember = activeMember)
            }
        }
    }

    // Add Admin Dialog
    if (showAddAdminDialog) {
        var nameInput by remember { mutableStateOf("") }
        var titleInput by remember { mutableStateOf("") }
        var emailInput by remember { mutableStateOf("") }
        var requiresLocation by remember { mutableStateOf(false) }
        var initialPasswordInput by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showAddAdminDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Create Admin User", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Full Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("Job Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Email Address") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = initialPasswordInput,
                        onValueChange = { initialPasswordInput = it },
                        label = { Text("Initial Password") },
                        placeholder = { Text("Default: 12345") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = requiresLocation, onCheckedChange = { requiresLocation = it })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Require location for operations", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showAddAdminDialog = false }) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            if (nameInput.isNotBlank()) {
                                viewModel.addTeamMember(
                                    name = nameInput,
                                    title = titleInput,
                                    role = "ADMIN",
                                    email = emailInput,
                                    requiresLocation = requiresLocation,
                                    password = if (initialPasswordInput.isNotBlank()) initialPasswordInput else "12345"
                                )
                                showAddAdminDialog = false
                            }
                        }) { Text("Create") }
                    }
                }
            }
        }
    }

    // Edit Admin Dialog
    editAdminMember?.let { admin ->
        var nameInput by remember { mutableStateOf(admin.name) }
        var titleInput by remember { mutableStateOf(admin.title) }
        var emailInput by remember { mutableStateOf(admin.email) }
        var requiresLocation by remember { mutableStateOf(admin.requiresLocation) }

        Dialog(onDismissRequest = { editAdminMember = null }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Edit Admin User", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Full Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("Job Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Email Address") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = requiresLocation, onCheckedChange = { requiresLocation = it })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Require location for operations", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { editAdminMember = null }) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            if (nameInput.isNotBlank()) {
                                viewModel.editTeamMember(
                                    member = admin,
                                    name = nameInput,
                                    title = titleInput,
                                    role = "ADMIN",
                                    email = emailInput,
                                    requiresLocation = requiresLocation
                                )
                                editAdminMember = null
                            }
                        }) { Text("Save") }
                    }
                }
            }
        }
    }

    // Reset Password Dialog
    resetPasswordAdminMember?.let { admin ->
        var newPassInput by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { resetPasswordAdminMember = null }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Reset Password", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Set a new password for Admin: ${admin.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newPassInput,
                        onValueChange = { newPassInput = it },
                        label = { Text("New Password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { resetPasswordAdminMember = null }) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            if (newPassInput.isNotBlank()) {
                                viewModel.setMemberPassword(admin.id, newPassInput)
                                resetPasswordAdminMember = null
                            }
                        }) { Text("Reset") }
                    }
                }
            }
        }
    }
}

// ---------------------- SUPERVISOR VIEW ----------------------
@Composable
fun SupervisorView(
    viewModel: AttendanceViewModel,
    activeMember: Member,
    members: List<Member>,
    attendanceRecords: List<Attendance>,
    activeSubTab: Int,
    onSubTabSelected: (Int) -> Unit
) {
    val tabLabels = listOf(
        stringResource(R.string.record_daily_roster),
        stringResource(R.string.settings),
        "Reports"
    )
    val activeLabel = tabLabels.getOrElse(activeSubTab) { "" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = activeLabel,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    when (activeSubTab) {
        0 -> {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = stringResource(R.string.daily_attendance_check),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.supervisor_instruction),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                val todayDate = viewModel.getCurrentDateString()
                val employees = members.filter { it.role == "EMPLOYEE" }
                employees.forEach { emp ->
                    val existingRecord = attendanceRecords.firstOrNull { it.memberId == emp.id && it.date == todayDate }
                    val isApproved = existingRecord?.approvedBySupervisorId != null

                    var isPresent by remember(existingRecord) { mutableStateOf(existingRecord?.isPresent ?: true) }
                    var overtimeInput by remember(existingRecord) { mutableStateOf(existingRecord?.overtimeHours ?: 0.0) }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = emp.name, fontWeight = FontWeight.Bold)
                                    Text(text = emp.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (isPresent) "Present" else "Absent", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Checkbox(
                                        checked = isPresent,
                                        onCheckedChange = { isPresent = it },
                                        enabled = !isApproved
                                    )
                                }
                            }

                            if (isPresent) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Overtime Hours:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedButton(
                                            onClick = { if (overtimeInput > 0.0) overtimeInput -= 0.5 },
                                            contentPadding = PaddingValues(0.dp),
                                            modifier = Modifier.size(36.dp),
                                            enabled = !isApproved
                                        ) {
                                            Text("-", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Text(
                                            text = "${overtimeInput}h",
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 12.dp)
                                        )

                                        OutlinedButton(
                                            onClick = { if (overtimeInput < 12.0) overtimeInput += 0.5 },
                                            contentPadding = PaddingValues(0.dp),
                                            modifier = Modifier.size(36.dp),
                                            enabled = !isApproved
                                        ) {
                                            Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (!isApproved) {
                                Button(
                                    onClick = {
                                        viewModel.supervisorSaveAttendance(emp.id, isPresent, overtimeInput)
                                    },
                                    modifier = Modifier.align(Alignment.End).testTag("confirm_record_btn_${emp.id}")
                                ) {
                                    Text("Confirm & Record", fontSize = 12.sp)
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.testTag("approved_badge_${emp.id}")
                                    ) {
                                        Text(
                                            text = "RECORDED & APPROVED ✓",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    Text(
                                        text = "Saved & Locked",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        1 -> {
            SettingsView(activeMember = activeMember, viewModel = viewModel)
        }
        2 -> {
            ReportsDashboard(viewModel = viewModel, activeMember = activeMember)
        }
    }
}

// ---------------------- EMPLOYEE VIEW ----------------------
@Composable
fun EmployeeView(
    viewModel: AttendanceViewModel,
    activeMember: Member,
    attendanceRecords: List<Attendance>,
    currentTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val personalRecords = attendanceRecords.filter { it.memberId == activeMember.id }
    val todayRecord = personalRecords.firstOrNull { it.date == viewModel.getCurrentDateString() }
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()

    LaunchedEffect(activeMember.id) {
        viewModel.loadNotifications(activeMember.id)
    }

    var selectedOvertimeHours by remember { mutableStateOf(0.0) }
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var pendingAction by remember { mutableStateOf<String?>(null) }
    var locationMessage by remember { mutableStateOf<String?>(null) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            fetchLocationAndClock(fusedLocationClient, pendingAction, viewModel, selectedOvertimeHours) { msg ->
                locationMessage = msg
            }
        } else {
            locationMessage = "Location permission denied"
            // Still clock in/out without location if denied? Or block it?
            // The user said "requires location", so ideally block or just pass "Denied". We'll pass "Denied".
            if (pendingAction == "IN") viewModel.employeeClockIn("Denied")
            else if (pendingAction == "OUT") viewModel.employeeClockOut(selectedOvertimeHours, "Denied")
        }
    }

    when (currentTab) {
        0 -> {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                locationMessage?.let { msg ->
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Daily Time Card",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = viewModel.getFullFormattedDate(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Real-time Punch clock display
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(CircleShape)
                                .background(
                                    if (todayRecord != null) {
                                        if (todayRecord.punchOutTime != null) MaterialTheme.colorScheme.secondaryContainer
                                        else MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = if (todayRecord != null) Icons.Default.Check else Icons.Default.Lock,
                                    contentDescription = "Clock Icon",
                                    tint = if (todayRecord != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (todayRecord == null) {
                                        "IDLE"
                                    } else if (todayRecord.punchOutTime != null) {
                                        "COMPLETED"
                                    } else {
                                        "ACTIVE"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (todayRecord != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Overtime Chart for current employee
                OvertimeDashboardWidget(listOf(activeMember), personalRecords)
                
                Spacer(modifier = Modifier.height(24.dp))

                // Overtime logger slider
                if (todayRecord != null && todayRecord.punchOutTime == null) {
                            Text(
                                text = "Accumulated Overtime: ${String.format("%.1f", selectedOvertimeHours)} Hours",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = selectedOvertimeHours.toFloat(),
                                onValueChange = { selectedOvertimeHours = Math.round(it * 2) / 2.0 },
                                valueRange = 0f..6f,
                                steps = 11,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Punch Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    val activity = context as? androidx.fragment.app.FragmentActivity
                                    if (activity != null && BiometricHelper.isBiometricAvailable(context)) {
                                        BiometricHelper.showBiometricPrompt(
                                            activity = activity,
                                            onSuccess = {
                                                if (activeMember.requiresLocation) {
                                                    pendingAction = "IN"
                                                    val hasFine = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                                    val hasCoarse = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                                    if (hasFine || hasCoarse) {
                                                        fetchLocationAndClock(fusedLocationClient, pendingAction, viewModel, selectedOvertimeHours) { msg ->
                                                            locationMessage = msg
                                                        }
                                                    } else {
                                                        locationPermissionLauncher.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
                                                    }
                                                } else {
                                                    viewModel.employeeClockIn()
                                                }
                                            },
                                            onError = { err ->
                                                android.widget.Toast.makeText(context, "Authentication failed: $err", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    } else {
                                        if (activeMember.requiresLocation) {
                                            pendingAction = "IN"
                                            val hasFine = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                            val hasCoarse = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                            if (hasFine || hasCoarse) {
                                                fetchLocationAndClock(fusedLocationClient, pendingAction, viewModel, selectedOvertimeHours) { msg ->
                                                    locationMessage = msg
                                                }
                                            } else {
                                                locationPermissionLauncher.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
                                            }
                                        } else {
                                            viewModel.employeeClockIn()
                                        }
                                    }
                                },
                                enabled = todayRecord == null,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("clock_in_button")
                            ) {
                                Text("Clock In")
                            }

                            Button(
                                onClick = {
                                    val activity = context as? androidx.fragment.app.FragmentActivity
                                    if (activity != null && BiometricHelper.isBiometricAvailable(context)) {
                                        BiometricHelper.showBiometricPrompt(
                                            activity = activity,
                                            onSuccess = {
                                                if (activeMember.requiresLocation) {
                                                    pendingAction = "OUT"
                                                    val hasFine = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                                    val hasCoarse = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                                    if (hasFine || hasCoarse) {
                                                        fetchLocationAndClock(fusedLocationClient, pendingAction, viewModel, selectedOvertimeHours) { msg ->
                                                            locationMessage = msg
                                                        }
                                                    } else {
                                                        locationPermissionLauncher.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
                                                    }
                                                } else {
                                                    viewModel.employeeClockOut(selectedOvertimeHours)
                                                }
                                            },
                                            onError = { err ->
                                                android.widget.Toast.makeText(context, "Authentication failed: $err", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    } else {
                                        if (activeMember.requiresLocation) {
                                            pendingAction = "OUT"
                                            val hasFine = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                            val hasCoarse = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                            if (hasFine || hasCoarse) {
                                                fetchLocationAndClock(fusedLocationClient, pendingAction, viewModel, selectedOvertimeHours) { msg ->
                                                    locationMessage = msg
                                                }
                                            } else {
                                                locationPermissionLauncher.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
                                            }
                                        } else {
                                            viewModel.employeeClockOut(selectedOvertimeHours)
                                        }
                                    }
                                },
                                enabled = todayRecord != null && todayRecord.punchOutTime == null,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("clock_out_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Text("Clock Out")
                            }
                        }

                        // Show punch-in and out stamps
                        if (todayRecord != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("PUNCH IN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                    Text(todayRecord.punchInTime ?: "--:--", fontWeight = FontWeight.Bold)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("PUNCH OUT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                    Text(todayRecord.punchOutTime ?: "--:--", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                Spacer(modifier = Modifier.height(24.dp))

                // Notifications Section
                if (notifications.isNotEmpty()) {
                    Text(
                        text = "Recent Activity Alerts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    notifications.take(5).forEach { notification ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (notification.isRead) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = notification.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    if (!notification.isRead) {
                                        TextButton(onClick = { viewModel.markNotificationAsRead(notification.id) }) {
                                            Text("Dismiss", fontSize = 10.sp)
                                        }
                                    }
                                }
                                Text(text = notification.message, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Personal History table
                Text(
                    text = "Your Personal Attendance Log",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                personalRecords.forEach { record ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = "Date: ${record.date}", fontWeight = FontWeight.Bold)
                                Text(
                                    text = "In: ${record.punchInTime ?: "-"} | Out: ${record.punchOutTime ?: "-"} | Overtime: ${record.overtimeHours} hrs",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }

                            Badge(
                                containerColor = when(record.status) {
                                    "APPROVED" -> MaterialTheme.colorScheme.primaryContainer
                                    "REJECTED" -> MaterialTheme.colorScheme.errorContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            ) {
                                Text(
                                    text = record.status,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        1 -> { // Contact Admin
            ContactAdminView(onSendMessage = { viewModel.sendMessageToAdmin(it) })
        }
        2 -> { // Settings
            SettingsView(activeMember = activeMember, viewModel = viewModel)
        }
    }
}

// Simple Card wrapper for Metrics
@Composable
fun MetricCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

@Composable
fun NormalUserView(
    activeMember: Member,
    attendanceRecords: List<Attendance>,
    members: List<Member>
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Welcome, ${activeMember.name}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Role: Normal Viewer (Read-only Access)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "You have read-only access to view team attendance. If you need clock-in privileges, contact an administrator.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Live Team Attendance Log",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (attendanceRecords.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No attendance logs found.")
            }
        } else {
            attendanceRecords.forEach { record ->
                val member = members.firstOrNull { it.id == record.memberId }
                if (member != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = member.name, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "Date: ${record.date} | In: ${record.punchInTime ?: "-"} | Out: ${record.punchOutTime ?: "-"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Badge(
                                containerColor = if (record.isPresent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = if (record.isPresent) "PRESENT" else "ABSENT",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LoginScreen(
    viewModel: AttendanceViewModel,
    modifier: Modifier = Modifier
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isSignUpMode by remember { mutableStateOf(false) }
    val loginError by viewModel.loginError.collectAsStateWithLifecycle()
    val isBiometricPreferred by viewModel.isBiometricPreferred.collectAsStateWithLifecycle()
    val lastUser = remember { viewModel.getLastCachedUser() }

    var showRecoveryDialog by remember { mutableStateOf(false) }
    var recoveryEmail by remember { mutableStateOf("") }
    var recoveryCodeInput by remember { mutableStateOf("") }
    var recoveryNewPassword by remember { mutableStateOf("") }
    var recoveryError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showMicrosoftLoginDialog by remember { mutableStateOf(false) }
    var microsoftEmailInput by remember { mutableStateOf("") }
    var microsoftLoginStep by remember { mutableStateOf(0) } // 0: Input email, 1: Loading
    var microsoftLoginError by remember { mutableStateOf<String?>(null) }

    var showDevPasscodeDialog by remember { mutableStateOf(false) }

    val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
    val isDark = when (appTheme) {
        "Dark" -> true
        "Light" -> false
        else -> isSystemInDarkTheme()
    }
    val appleBackgroundBrush = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(Color(0xFF0F0F1A), Color(0xFF1E1F29), Color(0xFF121214))
        } else {
            listOf(Color(0xFFE2E9F3), Color(0xFFF3EDF5), Color(0xFFEAF5ED))
        }
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(appleBackgroundBrush)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App Branding or Logo
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)), CircleShape)
                    .pointerInput(Unit) {
                        // Hidden Access Trigger: Detect a double-tap within DEV_DOUBLE_TAP_MS (500ms)
                        // Single-click remains completely unchanged (unhandled, behaves normally)
                        detectTapGestures(
                            onDoubleTap = {
                                try {
                                    val remainingLockout = com.example.ui.DeveloperAuthService.getRemainingLockoutTime(context)
                                    if (remainingLockout > 0) {
                                        val remainingSecs = (remainingLockout + 999) / 1000
                                        Toast.makeText(context, context.getString(R.string.lockout_message, remainingSecs.toInt()), Toast.LENGTH_LONG).show()
                                    } else {
                                        showDevPasscodeDialog = true
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Login Logo",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Text(
                text = if (isSignUpMode) "Create Account" else stringResource(R.string.welcome_back),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = if (isSignUpMode) "Register your workspace credentials to get started" else stringResource(R.string.sign_in_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Glass container for the Form
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                containerAlpha = 0.35f,
                borderAlpha = 0.3f
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Username/Email Input
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.username_or_email)) },
                        placeholder = { Text("Enter your email") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Person, contentDescription = "User Icon")
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                            focusedContainerColor = Color.White.copy(alpha = 0.12f)
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("username_input")
                    )

                    // Password Input
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(com.example.R.string.password)) },
                        visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = "Lock Icon")
                        },
                        trailingIcon = {
                            TextButton(onClick = { passwordVisible = !passwordVisible }) {
                                Text(
                                    text = if (passwordVisible) "HIDE" else "SHOW",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                            focusedContainerColor = Color.White.copy(alpha = 0.12f)
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                showRecoveryDialog = true
                                recoveryError = null
                            },
                            modifier = Modifier.testTag("forgot_password_btn")
                        ) {
                            Text(
                                "Forgot Password?",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (loginError != null) {
                        Text(
                            text = loginError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                if (isSignUpMode) {
                                    viewModel.signUp(username, password)
                                } else {
                                    viewModel.login(username, password)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("login_button")
                    ) {
                        Text(if (isSignUpMode) "Sign Up" else "Log In", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = {
                            isSignUpMode = !isSignUpMode
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isSignUpMode) "Already have an account? Log In" else "Don't have an account? Sign Up",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (lastUser != null) {
                        val isDirectLoginAvailable = (lastUser.role == "ADMIN" || lastUser.role == "DEVELOPER") && com.example.ui.DeveloperAuthService.isDeveloperAuthorized(context)
                        
                        OutlinedButton(
                            onClick = {
                                if (isDirectLoginAvailable) {
                                    coroutineScope.launch {
                                        viewModel.login(lastUser.email, "")
                                    }
                                } else {
                                    val activity = context as? androidx.fragment.app.FragmentActivity
                                    if (activity != null && BiometricHelper.isBiometricAvailable(context) && isBiometricPreferred) {
                                        BiometricHelper.showBiometricPrompt(
                                            activity = activity,
                                            title = "Biometric Login",
                                            subtitle = "Authenticate to sign in as ${lastUser.name}",
                                            onSuccess = {
                                                coroutineScope.launch {
                                                    viewModel.loginWithBiometric(lastUser.id)
                                                    Toast.makeText(context, "Welcome back, ${lastUser.name}!", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            onError = { err ->
                                                Toast.makeText(context, "Biometric failed: $err", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    } else {
                                        username = lastUser.email
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("login_as_button"),
                            border = BorderStroke(1.dp, if (isDirectLoginAvailable) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isDirectLoginAvailable) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            )
                        ) {
                            Icon(
                                imageVector = if (isDirectLoginAvailable) Icons.Default.Shield else Icons.Default.Fingerprint,
                                contentDescription = "Login Icon",
                                tint = if (isDirectLoginAvailable) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                if (isDirectLoginAvailable) "Direct Login as ${lastUser.name}" else "Login as ${lastUser.name} (Biometric)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDirectLoginAvailable) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)
                        )
                        Text(
                            text = "OR ENTERPRISE SIGN-ON",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)
                        )
                    }

                    // Microsoft 365 Login Button
                    OutlinedButton(
                        onClick = {
                            showMicrosoftLoginDialog = true
                            microsoftLoginStep = 0
                            microsoftEmailInput = ""
                            microsoftLoginError = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("microsoft_login_button"),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isDark) Color(0xFF1E1E1E).copy(alpha = 0.45f) else Color.White.copy(alpha = 0.6f)
                        )
                    ) {
                        MicrosoftLogo()
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Sign in with Microsoft 365",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (showMicrosoftLoginDialog) {
        AlertDialog(
            onDismissRequest = {
                if (microsoftLoginStep == 0) {
                    showMicrosoftLoginDialog = false
                    microsoftEmailInput = ""
                    microsoftLoginError = null
                }
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MicrosoftLogo(modifier = Modifier.size(20.dp))
                    Text(
                        text = "Microsoft",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSystemInDarkTheme()) Color.White else Color(0xFF2F2F2F)
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (microsoftLoginStep == 0) {
                        Text(
                            text = "Sign in using your business email to access your corporate workspace.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = microsoftEmailInput,
                            onValueChange = { microsoftEmailInput = it },
                            label = { Text("Email, phone, or Skype") },
                            placeholder = { Text("someone@work.com") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("m365_email_input")
                        )
                        if (microsoftLoginError != null) {
                            Text(
                                text = microsoftLoginError ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (microsoftLoginStep == 1) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(40.dp),
                                color = Color(0xFF00A1F1)
                            )
                            Text(
                                text = "Connecting to tenant portal...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Checking organizational security policies",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (microsoftLoginStep == 0) {
                    Button(
                        onClick = {
                            if (microsoftEmailInput.isEmpty() || !microsoftEmailInput.contains("@")) {
                                microsoftLoginError = "Enter a valid business email address."
                            } else {
                                coroutineScope.launch {
                                    microsoftLoginStep = 1
                                    delay(1500)
                                    val success = viewModel.loginWithMicrosoft365(microsoftEmailInput)
                                    if (success) {
                                        showMicrosoftLoginDialog = false
                                        Toast.makeText(context, "Successfully authenticated with Microsoft 365!", Toast.LENGTH_LONG).show()
                                    } else {
                                        microsoftLoginStep = 0
                                        microsoftLoginError = "Corporate identity matching '$microsoftEmailInput' not registered in this workspace."
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A1F1)),
                        modifier = Modifier.testTag("m365_next_button")
                    ) {
                        Text("Next", color = Color.White)
                    }
                }
            },
            dismissButton = {
                if (microsoftLoginStep == 0) {
                    TextButton(
                        onClick = {
                            showMicrosoftLoginDialog = false
                            microsoftEmailInput = ""
                            microsoftLoginError = null
                        },
                        modifier = Modifier.testTag("m365_cancel_button")
                    ) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    if (showRecoveryDialog) {
        AlertDialog(
            onDismissRequest = { showRecoveryDialog = false },
            title = {
                Text(
                    text = "Self-Service Password Recovery",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Enter your registered email address and the temporary recovery code generated by your Administrator to securely set your new login password.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = recoveryEmail,
                        onValueChange = { recoveryEmail = it },
                        label = { Text("Email Address") },
                        placeholder = { Text("name@work.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("recovery_email_input")
                    )

                    OutlinedTextField(
                        value = recoveryCodeInput,
                        onValueChange = { recoveryCodeInput = it },
                        label = { Text("Temporary Recovery Code") },
                        placeholder = { Text("6-digit code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("recovery_code_input")
                    )

                    OutlinedTextField(
                        value = recoveryNewPassword,
                        onValueChange = { recoveryNewPassword = it },
                        label = { Text("New Password") },
                        placeholder = { Text("Enter a secure new password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("recovery_new_password_input")
                    )

                    if (recoveryError != null) {
                        Text(
                            text = recoveryError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (recoveryEmail.isEmpty() || recoveryCodeInput.isEmpty() || recoveryNewPassword.isEmpty()) {
                            recoveryError = "Please fill in all fields."
                        } else {
                            val success = viewModel.recoverPassword(recoveryEmail, recoveryCodeInput, recoveryNewPassword)
                            if (success) {
                                Toast.makeText(context, "Password reset successfully! You can now log in.", Toast.LENGTH_LONG).show()
                                showRecoveryDialog = false
                                recoveryEmail = ""
                                recoveryCodeInput = ""
                                recoveryNewPassword = ""
                                recoveryError = null
                            } else {
                                recoveryError = "Invalid recovery code or email match. Please verify with Admin."
                            }
                        }
                    },
                    modifier = Modifier.testTag("submit_recovery_button")
                ) {
                    Text("Reset Password")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRecoveryDialog = false },
                    modifier = Modifier.testTag("cancel_recovery_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    var devPasscodeInput by remember { mutableStateOf("") }
    var devPasscodeError by remember { mutableStateOf<String?>(null) }

    if (showDevPasscodeDialog) {
        AlertDialog(
            onDismissRequest = { 
                showDevPasscodeDialog = false
                devPasscodeInput = ""
                devPasscodeError = null
            },
            title = {
                Text(
                    text = stringResource(R.string.developer_passcode_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.developer_passcode_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = devPasscodeInput,
                        onValueChange = { 
                            devPasscodeInput = it
                            devPasscodeError = null
                        },
                        label = { Text(stringResource(R.string.passcode)) },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("dev_passcode_input")
                    )
                    if (devPasscodeError != null) {
                        Text(
                            text = devPasscodeError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val isVerified = com.example.ui.DeveloperAuthService.verifyPasscode(context, devPasscodeInput) { status, msg ->
                                // viewModel.logAdminAction("DEV_ACCESS_$status", "Developer", msg)
                            }
                            if (isVerified) {
                                com.example.ui.DeveloperAuthService.setDeveloperAuthorized(context, true)
                                showDevPasscodeDialog = false
                                devPasscodeInput = ""
                                devPasscodeError = null
                                
                                // Auto-login as developer if authorized
                                coroutineScope.launch {
                                    val success = viewModel.login("eng.mahmoudahmed1991@gmail.com", "")
                                    if (!success) {
                                        Toast.makeText(context, "Direct Developer access enabled locally.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                devPasscodeError = context.getString(R.string.incorrect_passcode)
                            }
                        } catch (e: Exception) {
                            devPasscodeError = e.message ?: "Authentication error"
                        }
                    },
                    modifier = Modifier.testTag("dev_passcode_verify_button")
                ) {
                    Text("Verify")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDevPasscodeDialog = false
                        devPasscodeInput = ""
                        devPasscodeError = null
                    },
                    modifier = Modifier.testTag("dev_passcode_cancel_button")
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun CustomFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .testTag("filter_chip_${label.lowercase().replace(" ", "_")}"),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun MicrosoftLogo(modifier: Modifier = Modifier) {
    Column(modifier = modifier.size(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFFF25022)))
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFF7FBA00)))
        }
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFF00A1F1)))
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFFFFB900)))
        }
    }
}

@Composable
fun GlassDock(
    isDark: Boolean,
    tabs: List<String>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    icons: List<androidx.compose.ui.graphics.vector.ImageVector>,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isDark) {
        Color(0xFF1E1E1E).copy(alpha = 0.75f)
    } else {
        Color.White.copy(alpha = 0.8f)
    }
    val borderColor = if (isDark) {
        Color.White.copy(alpha = 0.15f)
    } else {
        Color.White.copy(alpha = 0.35f)
    }

    Surface(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .padding(bottom = 12.dp)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .shadow(16.dp, shape = RoundedCornerShape(24.dp)),
        color = bgColor,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, label ->
                val selected = selectedTab == index
                val icon = icons.getOrElse(index) { Icons.Default.Menu }
                val tabBgColor = if (selected) {
                    if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f)
                } else {
                    Color.Transparent
                }
                
                val defaultColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                val activeColors = listOf(
                    MaterialTheme.colorScheme.primary,
                    Color(0xFF4CAF50), // Green for Team/Roster
                    Color(0xFF2196F3), // Blue
                    Color(0xFFFF9800), // Orange
                    Color(0xFF9C27B0)  // Purple
                )
                val baseColor = activeColors.getOrElse(index % activeColors.size) { MaterialTheme.colorScheme.primary }
                val contentColor = if (selected) baseColor else baseColor.copy(alpha = 0.6f)

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onTabSelected(index) }
                        .background(tabBgColor)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("dock_tab_$index"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = contentColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = label,
                            fontSize = 9.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = contentColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp),
    borderAlpha: Float = 0.25f,
    containerAlpha: Float = 0.45f,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) {
        Color(0xFF1E1E1E).copy(alpha = containerAlpha)
    } else {
        Color.White.copy(alpha = containerAlpha)
    }
    val borderColor = if (isDark) {
        Color.White.copy(alpha = borderAlpha * 0.4f)
    } else {
        Color.White.copy(alpha = borderAlpha)
    }

    val cardModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }

    Card(
        modifier = cardModifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

