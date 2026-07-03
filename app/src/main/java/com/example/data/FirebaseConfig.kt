package com.example.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import kotlinx.coroutines.tasks.await

object FirebaseConfig {
    private const val TAG = "FirebaseConfig"

    // Root collections as defined in the default database structure
    const val COLLECTION_USERS = "users"
    const val COLLECTION_TEAMS = "teams"
    const val COLLECTION_ATTENDANCE_RECORDS = "attendance_records"

    // Legacy collections for backward compatibility
    const val COLLECTION_LEGACY_MEMBERS = "members"
    const val COLLECTION_LEGACY_ATTENDANCE = "attendance"

    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    
    val firestore: FirebaseFirestore by lazy {
        val instance = FirebaseFirestore.getInstance()
        try {
            // Enable offline persistence (cache) and settings using modern API
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(com.google.firebase.firestore.PersistentCacheSettings.newBuilder().build())
                .build()
            instance.firestoreSettings = settings
            Log.d(TAG, "Firestore initialized with persistence enabled.")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting Firestore settings: ${e.message}")
        }
        instance
    }

    /**
     * Verifies the connection with the default Firestore database.
     * Performs a lightweight query to verify we can successfully point to our collections.
     * Returns true if successful, or falls back gracefully to local persistence status.
     */
    suspend fun verifyConnection(): Boolean {
        return try {
            // Fetch first document from members collection to verify query execution
            val snapshot = firestore.collection(COLLECTION_LEGACY_MEMBERS).limit(1).get().await()
            Log.d(TAG, "Firestore connection verified. Found ${snapshot.size()} documents in '$COLLECTION_LEGACY_MEMBERS'.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Firestore connection verification failed: ${e.message}")
            false
        }
    }
}
