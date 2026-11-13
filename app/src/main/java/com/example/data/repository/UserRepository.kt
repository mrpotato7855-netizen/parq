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
        val safeDisplayName = defaultDisplayName.trim().ifBlank { "User" }.take(60)
        val safeAvatar = defaultAvatar.trim().take(2000)
        val snapshot = try {
            docRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
        if (snapshot.exists()) {
            val parsed = snapshot.toObject(User::class.java)
            return if (parsed != null) {
                if (parsed.userId.isBlank()) parsed.copy(userId = uid) else parsed
            } else {
                User(userId = uid, displayName = safeDisplayName, username = "user_${uid.take(6).lowercase()}")
            }
        }

        // Generate initial username from uid
        val generatedUsername = "user_${uid.take(6).lowercase()}".padEnd(6, '0')
        val payload = mapOf(
            "userId" to uid,
            "username" to generatedUsername,
            "displayName" to safeDisplayName,
            "bio" to "Hey there! I am using Barq.",
            "avatarUrl" to safeAvatar,
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
                displayName = safeDisplayName,
                bio = "Hey there! I am using Barq.",
                avatarUrl = safeAvatar,
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
                    if (snapshot.exists()) {
                        snapshot.toObject(User::class.java)?.let {
                            if (it.userId.isBlank()) it.copy(userId = snapshot.id) else it
                        }
                    } else null
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
        val cleanUsername = username.trim().removePrefix("@").lowercase().take(35)
        val safeUsername = if (cleanUsername.length >= 3) cleanUsername else "user_${uid.take(6).lowercase()}"
        val payload = mapOf(
            "displayName" to displayName.trim().ifBlank { "User" }.take(60),
            "username" to safeUsername,
            "bio" to bio.trim().take(300),
            "avatarUrl" to avatarUrl.trim().take(2000),
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

    suspend fun listUsers(limitCount: Long = 50): List<User> {
        requireUserId()
        return try {
            val snapshot = db.collection("users")
                .limit(limitCount)
                .get()
                .await()
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(User::class.java)?.let {
                    if (it.userId.isBlank()) it.copy(userId = doc.id) else it
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "users")
            emptyList()
        }
    }

    suspend fun searchUsers(query: String): List<User> {
        requireUserId()
        val cleanQuery = query.trim().removePrefix("@").lowercase()
        if (cleanQuery.isBlank()) return emptyList()

        try {
            val prefixSnapshot = db.collection("users")
                .orderBy("username")
                .startAt(cleanQuery)
                .endAt(cleanQuery + "\uf8ff")
                .limit(20)
                .get()
                .await()

            val allSnapshot = db.collection("users")
                .limit(50)
                .get()
                .await()

            val byUsername = prefixSnapshot.documents.mapNotNull { doc ->
                doc.toObject(User::class.java)?.let {
                    if (it.userId.isBlank()) it.copy(userId = doc.id) else it
                }
            }
            val byNameOrHandle = allSnapshot.documents.mapNotNull { doc ->
                doc.toObject(User::class.java)?.let {
                    if (it.userId.isBlank()) it.copy(userId = doc.id) else it
                }
            }.filter { user ->
                user.username.contains(cleanQuery, ignoreCase = true) ||
                    user.displayName.contains(cleanQuery, ignoreCase = true)
            }

            return (byUsername + byNameOrHandle).distinctBy { it.userId }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "users")
            return emptyList()
        }
    }

    suspend fun getUserById(userId: String): User? {
        requireUserId()
        val docRef = db.collection("users").document(userId)
        return try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                snap.toObject(User::class.java)?.let {
                    if (it.userId.isBlank()) it.copy(userId = snap.id) else it
                }
            } else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            null
        }
    }

    suspend fun getUsersByIds(userIds: List<String>): List<User> {
        requireUserId()
        val cleanIds = userIds.filter { it.isNotBlank() }.distinct()
        if (cleanIds.isEmpty()) return emptyList()
        val result = mutableListOf<User>()
        for (chunk in cleanIds.chunked(10)) {
            try {
                val snap = db.collection("users")
                    .whereIn("userId", chunk)
                    .get()
                    .await()
                result.addAll(
                    snap.documents.mapNotNull { doc ->
                        doc.toObject(User::class.java)?.let {
                            if (it.userId.isBlank()) it.copy(userId = doc.id) else it
                        }
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                for (id in chunk) {
                    getUserById(id)?.let { result.add(it) }
                }
            }
        }
        return result.distinctBy { it.userId }
    }

    suspend fun saveFcmToken(token: String) {
        val uid = auth.currentUser?.uid ?: return
        if (token.isBlank()) return
        try {
            db.collection("users").document(uid)
                .collection("fcmTokens").document(token.take(100))
                .set(
                    mapOf(
                        "token" to token,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    suspend fun reportUser(targetUid: String, reason: String) {
        val uid = requireUserId()
        val docRef = db.collection("reports").document()
        val payload = mapOf(
            "reporterUid" to uid,
            "targetUid" to targetUid,
            "reason" to reason.trim().ifBlank { "سلوك مخالف" }.take(300),
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }
}
