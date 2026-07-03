package com.example

import android.app.Application
import com.google.firebase.FirebaseApp

class AttendanceApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        instance = this
    }

    companion object {
        lateinit var instance: AttendanceApplication
            private set
    }
}
