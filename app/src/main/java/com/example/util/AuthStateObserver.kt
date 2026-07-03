package com.example.util

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Diagnostic utility to monitor and log Firebase Authentication and Firestore state.
 * Check Logcat with the tag "AuthStateObserver" for details.
 */
class AuthStateObserver(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val TAG = "AuthStateObserver"
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        Log.i(TAG, "Initializing Diagnostic AuthStateObserver...")
        
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                Log.i(TAG, "--- AUTH SESSION DETECTED ---")
                Log.d(TAG, "Status: LOGGED_IN")
                Log.d(TAG, "UID: ${user.uid}")
                Log.d(TAG, "Email: ${user.email}")
                Log.d(TAG, "Is Anonymous: ${user.isAnonymous}")
                Log.d(TAG, "Providers: ${user.providerData.map { it.providerId }}")
                
                // Explicitly check for Custom Claims (Roles)
                scope.launch {
                    try {
                        val tokenResult = user.getIdToken(true).await()
                        val claims = tokenResult.claims
                        Log.i(TAG, "--- TOKEN CLAIMS ---")
                        Log.d(TAG, "All Claims: $claims")
                        
                        if (claims.containsKey("role")) {
                            Log.i(TAG, "SUCCESS: 'role' claim found -> ${claims["role"]}")
                        } else if (claims.containsKey("admin")) {
                            Log.i(TAG, "SUCCESS: 'admin' claim found -> ${claims["admin"]}")
                        } else {
                            Log.w(TAG, "WARNING: No role-based custom claims found in ID Token.")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "ERROR: Failed to fetch ID Token: ${e.message}")
                    }
                }
            } else {
                Log.i(TAG, "--- AUTH SESSION DETECTED ---")
                Log.w(TAG, "Status: LOGGED_OUT")
            }
        }

        // Firestore Connectivity Monitor
        // We attempt to listen to a non-existent or restricted path to check connectivity state
        firestore.collection("_diagnostics_").document("ping")
            .addSnapshotListener { snapshot, e ->
                Log.i(TAG, "--- FIRESTORE CONNECTIVITY ---")
                if (e != null) {
                    if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        Log.d(TAG, "Firestore Status: CONNECTED (Encountered expected Permission Denied on restricted path)")
                    } else {
                        Log.e(TAG, "Firestore Status: ERROR -> ${e.code}: ${e.message}")
                    }
                } else if (snapshot != null) {
                    val source = if (snapshot.metadata.isFromCache) "LOCAL_CACHE" else "REMOTE_SERVER"
                    Log.d(TAG, "Firestore Status: ACTIVE (Data source: $source)")
                }
            }
    }
}
