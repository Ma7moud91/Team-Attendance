package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.firestore.*
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import androidx.compose.runtime.LaunchedEffect

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OvertimeDashboardWidget(
    members: List<Member>,
    attendanceRecords: List<Attendance>
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    
    val overtimeData = remember(members, attendanceRecords) {
        members.filter { it.role == "EMPLOYEE" }.map { member ->
            val totalOT = attendanceRecords
                .filter { it.memberId == member.id }
                .sumOf { it.overtimeHours }
            member.name to totalOT.toFloat()
        }.filter { it.second > 0 }
    }

    LaunchedEffect(overtimeData) {
        if (overtimeData.isNotEmpty()) {
            modelProducer.runTransaction {
                columnSeries {
                    series(overtimeData.map { it.second })
                }
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Total Overtime Hours per Member",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (overtimeData.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text("No overtime data recorded for this month.")
                }
            } else {
                CartesianChartHost(
                    chart = rememberCartesianChart(
                        rememberColumnCartesianLayer(),
                        startAxis = rememberStartAxis(),
                        bottomAxis = rememberBottomAxis(
                            valueFormatter = { value, _, _ ->
                                overtimeData.getOrNull(value.toInt())?.first ?: ""
                            }
                        ),
                    ),
                    modelProducer = modelProducer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Legend
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    overtimeData.forEach { (name, ot) ->
                        AssistChip(
                            onClick = {},
                            label = { Text("$name: $ot hrs") }
                        )
                    }
                }
            }
        }
    }
}
