package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.firestore.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReportsDashboard(
    viewModel: AttendanceViewModel,
    activeMember: Member,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val members by viewModel.members.collectAsStateWithLifecycle()
    val attendanceRecords by viewModel.attendanceRecords.collectAsStateWithLifecycle()
    val allowSupervisorExport by viewModel.allowSupervisorExport.collectAsStateWithLifecycle()
    val exportResult by viewModel.exportResult.collectAsStateWithLifecycle()

    // Filter states
    var selectedEmployeeId by remember { mutableStateOf<String?>(null) }
    var selectedTeam by remember { mutableStateOf("All") }
    var selectedSupervisorId by remember { mutableStateOf<String?>(null) }
    var startDateInput by remember { mutableStateOf("") }
    var endDateInput by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf("All") }

    // Dialog toggles for filter selection
    var showEmployeeSelectDialog by remember { mutableStateOf(false) }
    var showSupervisorSelectDialog by remember { mutableStateOf(false) }

    // If active user is a supervisor, restrict filters to only their assigned employees
    val visibleEmployees = remember(activeMember, members) {
        if (activeMember.role == "SUPERVISOR") {
            members.filter { it.role == "EMPLOYEE" && it.supervisorId == activeMember.id }
        } else {
            members
        }
    }

    // Automatically enforce supervisor filter if active user is supervisor
    val effectiveSupervisorId = if (activeMember.role == "SUPERVISOR") {
        activeMember.id
    } else {
        selectedSupervisorId
    }

    // Determine if export actions are permitted
    val canExport = remember(activeMember, allowSupervisorExport) {
        activeMember.role == "DEVELOPER" ||
        activeMember.role == "SUPERSU" ||
        activeMember.role == "ADMIN" ||
        (activeMember.role == "SUPERVISOR" && allowSupervisorExport)
    }

    // Filtered records
    val filteredRecords = remember(
        attendanceRecords, members, selectedEmployeeId, selectedTeam,
        effectiveSupervisorId, startDateInput, endDateInput, selectedStatus
    ) {
        viewModel.getFilteredAttendance(
            records = attendanceRecords,
            memberList = members,
            employeeId = selectedEmployeeId,
            team = selectedTeam,
            supervisorId = effectiveSupervisorId,
            startDateStr = startDateInput,
            endDateStr = endDateInput,
            status = selectedStatus
        )
    }

    // Handle export notification toasts
    LaunchedEffect(exportResult) {
        exportResult?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.clearExportResult()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("reports_dashboard_container"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome / Title Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Assessment,
                        contentDescription = "Reports",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Attendance Reports Generator",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (activeMember.role == "SUPERVISOR") {
                        "View detailed records and metrics for employees assigned to you."
                    } else {
                        "Filter, preview, and compile payroll or attendance summaries to XLSX & PDF."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Summary Metric Widgets
        Text(
            text = "Metric Summaries",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        val totalLogs = filteredRecords.size
        val presentLogs = filteredRecords.count { it.isPresent }
        val attendancePercent = if (totalLogs > 0) (presentLogs * 100) / totalLogs else 0
        val totalOvertimeHours = filteredRecords.sumOf { it.overtimeHours }
        val unapprovedCount = filteredRecords.count { it.isPresent && it.approvedBySupervisorId == null }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = "Filtered Logs",
                value = "$totalLogs",
                icon = Icons.Default.List,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Attendance %",
                value = "$attendancePercent%",
                icon = Icons.Default.CheckCircle,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = "Total Overtime",
                value = "${totalOvertimeHours}h",
                icon = Icons.Default.Star,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Unapproved",
                value = "$unapprovedCount",
                icon = Icons.Default.Warning,
                modifier = Modifier.weight(1f)
            )
        }

        // Filter Configuration Panel
        Card(
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Reports Query Filters",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )

                // 1. Employee picker
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showEmployeeSelectDialog = true },
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Filter by Employee", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val name = if (selectedEmployeeId == null) "All Employees" else members.find { it.id == selectedEmployeeId }?.name ?: "All Employees"
                            Text(name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        }
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                }

                // 2. Team / Role Row (if not supervisor)
                if (activeMember.role != "SUPERVISOR") {
                    Text("Filter by Team / Job Role", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("All", "EMPLOYEE", "SUPERVISOR", "ADMIN").forEach { team ->
                            FilterChip(
                                selected = selectedTeam == team,
                                onClick = { selectedTeam = team },
                                label = { Text(team) }
                            )
                        }
                    }

                    // 3. Supervisor picker
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSupervisorSelectDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Filter by Supervisor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val name = if (selectedSupervisorId == null) "All Supervisors" else members.find { it.id == selectedSupervisorId }?.name ?: "All Supervisors"
                                Text(name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            }
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                    }
                }

                // 4. Date range input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = startDateInput,
                        onValueChange = { startDateInput = it },
                        label = { Text("Start Date") },
                        placeholder = { Text("YYYY-MM-DD") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        trailingIcon = { Icon(Icons.Default.DateRange, null) }
                    )
                    OutlinedTextField(
                        value = endDateInput,
                        onValueChange = { endDateInput = it },
                        label = { Text("End Date") },
                        placeholder = { Text("YYYY-MM-DD") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        trailingIcon = { Icon(Icons.Default.DateRange, null) }
                    )
                }

                // 5. Attendance Status filters
                Text("Attendance Status", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "Present", "Absent", "PENDING", "APPROVED", "REJECTED").forEach { status ->
                        FilterChip(
                            selected = selectedStatus == status,
                            onClick = { selectedStatus = status },
                            label = { Text(status) }
                        )
                    }
                }

                // Clear Filters Button
                if (selectedEmployeeId != null || selectedTeam != "All" || selectedSupervisorId != null || startDateInput.isNotBlank() || endDateInput.isNotBlank() || selectedStatus != "All") {
                    TextButton(
                        onClick = {
                            selectedEmployeeId = null
                            selectedTeam = "All"
                            selectedSupervisorId = null
                            startDateInput = ""
                            endDateInput = ""
                            selectedStatus = "All"
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Refresh, "Clear")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset Filters")
                    }
                }
            }
        }

        // Export Options
        if (canExport) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Compile and Export Options",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.exportToExcel(
                                    context = context,
                                    employeeId = selectedEmployeeId,
                                    team = selectedTeam,
                                    supervisorId = effectiveSupervisorId,
                                    startDateStr = startDateInput,
                                    endDateStr = endDateInput,
                                    status = selectedStatus
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("export_excel_action"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("To Excel (.xlsx)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                viewModel.exportToPDF(
                                    context = context,
                                    employeeId = selectedEmployeeId,
                                    team = selectedTeam,
                                    supervisorId = effectiveSupervisorId,
                                    startDateStr = startDateInput,
                                    endDateStr = endDateInput,
                                    status = selectedStatus
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("export_pdf_action"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("To PDF (.pdf)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else if (activeMember.role == "SUPERVISOR") {
            // Supervisor warning that they don't have permission to export
            Card(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Reports export functionality is locked. Please request authorization from your Administrator.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Live Preview List before export
        Text(
            text = "Reports Preview (${filteredRecords.size} Records Found)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        if (filteredRecords.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Text(
                    text = "No logs match the selected query criteria.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
            ) {
                filteredRecords.forEach { record ->
                    val emp = members.find { it.id == record.memberId }
                    val empName = emp?.name ?: "Unknown"
                    val supervisor = emp?.supervisorId?.let { sId -> members.find { it.id == sId }?.name } ?: "None"
                    val workedHrs = viewModel.calculateWorkedHours(record.punchInTime, record.punchOutTime)
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
                                        text = "ID: ${record.memberId} | Date: ${record.date}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { }
                                ) {
                                    Surface(
                                        color = if (isApproved) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (isApproved) "APPROVED" else "PENDING",
                                            color = if (isApproved) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Supervisor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(supervisor, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Worked Hours", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(String.format("%.1f hrs", workedHrs), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Overtime", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${record.overtimeHours} hrs", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog for selecting Employee
    if (showEmployeeSelectDialog) {
        AlertDialog(
            onDismissRequest = { showEmployeeSelectDialog = false },
            title = { Text("Filter by Employee") },
            text = {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedEmployeeId = null
                                showEmployeeSelectDialog = false
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedEmployeeId == null, onClick = {
                            selectedEmployeeId = null
                            showEmployeeSelectDialog = false
                        })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("All Employees", fontWeight = FontWeight.Bold)
                    }

                    visibleEmployees.forEach { emp ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedEmployeeId = emp.id
                                    showEmployeeSelectDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedEmployeeId == emp.id, onClick = {
                                selectedEmployeeId = emp.id
                                showEmployeeSelectDialog = false
                            })
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(emp.name, fontWeight = FontWeight.Bold)
                                Text("Role: ${emp.role} | ID: ${emp.id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEmployeeSelectDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Dialog for selecting Supervisor
    if (showSupervisorSelectDialog) {
        val supervisors = members.filter { it.role == "SUPERVISOR" }
        AlertDialog(
            onDismissRequest = { showSupervisorSelectDialog = false },
            title = { Text("Filter by Supervisor") },
            text = {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedSupervisorId = null
                                showSupervisorSelectDialog = false
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedSupervisorId == null, onClick = {
                            selectedSupervisorId = null
                            showSupervisorSelectDialog = false
                        })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("All Supervisors", fontWeight = FontWeight.Bold)
                    }

                    supervisors.forEach { sup ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedSupervisorId = sup.id
                                    showSupervisorSelectDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedSupervisorId == sup.id, onClick = {
                                selectedSupervisorId = sup.id
                                showSupervisorSelectDialog = false
                            })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(sup.name, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSupervisorSelectDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun MetricCard(
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
            Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}
