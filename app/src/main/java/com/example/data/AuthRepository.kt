package com.example.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AuthRepository {
    private val auth: FirebaseAuth by lazy { FirebaseConfig.auth }
    private val firestore: FirebaseFirestore by lazy { FirebaseConfig.firestore }

    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    suspend fun signIn(email: String, pword: String): FirebaseUser {
        val result = auth.signInWithEmailAndPassword(email, pword).await()
        return result.user ?: throw Exception("User is null after sign in")
    }

    suspend fun signUp(email: String, pword: String): FirebaseUser {
        val result = auth.createUserWithEmailAndPassword(email, pword).await()
        return result.user ?: throw Exception("User is null after sign up")
    }

    suspend fun checkUserExistsInFirestore(uid: String): Boolean {
        return try {
            val subDoc = firestore.collection("data").document("root").collection(FirebaseConfig.COLLECTION_USERS).document(uid).get().await()
            if (subDoc.exists()) return true
            
            val document = firestore.collection(FirebaseConfig.COLLECTION_USERS).document(uid).get().await()
            document.exists()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun createUserInFirestore(uid: String, name: String, title: String, role: String, email: String) {
        val userMap = hashMapOf(
            "id" to uid,
            "name" to name,
            "title" to title,
            "role" to role,
            "email" to email,
            "isActive" to true,
            "createdAt" to System.currentTimeMillis()
        )
        // Write to Firestore /data/root/users collection
        try {
            firestore.collection("data").document("root").collection(FirebaseConfig.COLLECTION_USERS).document(uid).set(userMap).await()
        } catch (e: Exception) {
            // fail silently
        }
        
        // Write to root 'users' collection as backup
        try {
            firestore.collection(FirebaseConfig.COLLECTION_USERS).document(uid).set(userMap).await()
        } catch (e: Exception) {
            // fail silently
        }
    }

    fun signOut() {
        auth.signOut()
    }

    suspend fun refreshToken(forceRefresh: Boolean = true): String? {
        return auth.currentUser?.getIdToken(forceRefresh)?.await()?.token
    }
}
