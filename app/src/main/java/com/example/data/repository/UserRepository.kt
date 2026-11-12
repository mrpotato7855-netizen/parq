package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.User
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class UserRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    suspend fun getOrCreateProfile(defaultDisplayName: String, defaultAvatar: String = ""): User {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val snapshot = docRef.get().await()
        if (snapshot.exists()) {
            return snapshot.toObject(User::class.java)?.copy(userId = uid)
                ?: User(userId = uid, displayName = defaultDisplayName, username = "user_${uid.take(6)}")
        }

        // Generate initial username from uid
        val generatedUsername = "user_${uid.take(6).lowercase()}"
        val payload = mapOf(
            "userId" to uid,
            "username" to generatedUsername,
            "displayName" to defaultDisplayName.ifBlank { "User" },
            "bio" to "Hey there! I am using Barq.",
            "avatarUrl" to defaultAvatar,
            "isOnline" to true,
            "lastSeen" to FieldValue.serverTimestamp(),
            "lastSeenPrivacy" to "everyone",
            "avatarPrivacy" to "everyone",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            return User(
                userId = uid,
                username = generatedUsername,
                displayName = defaultDisplayName.ifBlank { "User" },
                bio = "Hey there! I am using Barq.",
                avatarUrl = defaultAvatar,
                isOnline = true
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    fun observeUserProfile(userId: String): Flow<User?> = flow {
        val docRef = db.collection("users").document(userId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) snapshot.toObject(User::class.java)?.copy(userId = snapshot.id) else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser != null) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun updateUserProfile(
        displayName: String,
        username: String,
        bio: String,
        avatarUrl: String,
        lastSeenPrivacy: String,
        avatarPrivacy: String
    ) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val payload = mapOf(
            "displayName" to displayName,
            "username" to username.trim().removePrefix("@").lowercase(),
            "bio" to bio,
            "avatarUrl" to avatarUrl,
            "lastSeenPrivacy" to lastSeenPrivacy,
            "avatarPrivacy" to avatarPrivacy,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun setOnlinePresence(isOnline: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val docRef = db.collection("users").document(uid)
        try {
            docRef.update(
                mapOf(
                    "isOnline" to isOnline,
                    "lastSeen" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            // Non-critical, ignore or log
        }
    }

    suspend fun searchUsers(query: String): List<User> {
        requireUserId()
        val cleanQuery = query.trim().removePrefix("@").lowercase()
        if (cleanQuery.isBlank()) return emptyList()

        try {
            val snapshot = db.collection("users")
                .orderBy("username")
                .startAt(cleanQuery)
                .endAt(cleanQuery + "\uf8ff")
                .limit(20)
                .get()
                .await()

            return snapshot.documents.mapNotNull { it.toObject(User::class.java)?.copy(userId = it.id) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "users")
            return emptyList()
        }
    }
}
