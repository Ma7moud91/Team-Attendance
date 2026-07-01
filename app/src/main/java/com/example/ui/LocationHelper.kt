package com.example.ui

import android.annotation.SuppressLint
import com.google.android.gms.location.FusedLocationProviderClient

@SuppressLint("MissingPermission")
fun fetchLocationAndClock(
    fusedLocationClient: FusedLocationProviderClient,
    action: String?,
    viewModel: AttendanceViewModel,
    overtime: Double,
    onResult: (String) -> Unit
) {
    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
        val locStr = if (location != null) {
            "${location.latitude}, ${location.longitude}"
        } else {
            "Unknown Location"
        }
        if (action == "IN") {
            viewModel.employeeClockIn(locStr)
            onResult("Clocked in at $locStr")
        } else if (action == "OUT") {
            viewModel.employeeClockOut(overtime, locStr)
            onResult("Clocked out at $locStr")
        }
    }.addOnFailureListener {
        val locStr = "Failed to get location"
        if (action == "IN") {
            viewModel.employeeClockIn(locStr)
        } else if (action == "OUT") {
            viewModel.employeeClockOut(overtime, locStr)
        }
        onResult(locStr)
    }
}
