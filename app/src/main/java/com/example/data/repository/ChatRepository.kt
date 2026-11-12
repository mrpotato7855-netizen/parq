package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.Chat
import com.example.data.model.ChatSettings
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
import java.util.UUID

class ChatRepository(private val db: FirebaseFirestore) {

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

    fun observeUserChats(userId: String): Flow<List<Chat>> = flow {
        val query = db.collection("chats")
            .whereArrayContains("participantUids", userId)

        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Chat::class.java).sortedByDescending {
                        it.lastMessageTime?.toDate()?.time ?: it.updatedAt?.toDate()?.time ?: 0L
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, "chats")
                    }
                    throw error
                }
        )
    }

    fun observePublicChannels(): Flow<List<Chat>> = flow {
        val query = db.collection("chats")
            .whereEqualTo("type", "channel")
            .whereEqualTo("isPublic", true)

        emitAll(
            query.snapshots()
                .map { snapshot -> snapshot.toObjects(Chat::class.java) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, "chats")
                    }
                    throw error
                }
        )
    }

    suspend fun getChatById(chatId: String): Chat? {
        requireUserId()
        val docRef = db.collection("chats").document(chatId)
        val snapshot = docRef.get().await()
        return if (snapshot.exists()) snapshot.toObject(Chat::class.java)?.copy(chatId = snapshot.id) else null
    }

    suspend fun createOrGetDirectChat(targetUser: User): Chat {
        val uid = requireUserId()
        // Generate deterministic chatId for direct chat between two users
        val sortedUids = listOf(uid, targetUser.userId).sorted()
        val directChatId = "direct_${sortedUids[0]}_${sortedUids[1]}"
        val docRef = db.collection("chats").document(directChatId)
        val snapshot = docRef.get().await()

        if (snapshot.exists()) {
            return snapshot.toObject(Chat::class.java)?.copy(chatId = directChatId)
                ?: Chat(chatId = directChatId, type = "direct", ownerId = uid, participantUids = sortedUids)
        }

        val payload = mapOf(
            "chatId" to directChatId,
            "type" to "direct",
            "name" to targetUser.displayName,
            "avatarUrl" to targetUser.avatarUrl,
            "ownerId" to uid,
            "participantUids" to sortedUids,
            "adminUids" to sortedUids,
            "lastMessage" to "",
            "lastMessageType" to "text",
            "isPublic" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            docRef.set(payload).await()
            return Chat(
                chatId = directChatId,
                type = "direct",
                name = targetUser.displayName,
                avatarUrl = targetUser.avatarUrl,
                ownerId = uid,
                participantUids = sortedUids
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun createGroup(name: String, about: String, avatarUrl: String, memberUids: List<String>): Chat {
        val uid = requireUserId()
        val groupId = "group_${UUID.randomUUID()}"
        val docRef = db.collection("chats").document(groupId)
        val allParticipants = (memberUids + uid).distinct()

        val payload = mapOf(
            "chatId" to groupId,
            "type" to "group",
            "name" to name,
            "about" to about,
            "avatarUrl" to avatarUrl,
            "ownerId" to uid,
            "participantUids" to allParticipants,
            "adminUids" to listOf(uid),
            "lastMessage" to "Group created",
            "lastMessageType" to "text",
            "isPublic" to false,
            "inviteLink" to "https://barq.chat/join/$groupId",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            docRef.set(payload).await()
            return Chat(
                chatId = groupId,
                type = "group",
                name = name,
                about = about,
                avatarUrl = avatarUrl,
                ownerId = uid,
                participantUids = allParticipants,
                adminUids = listOf(uid),
                inviteLink = "https://barq.chat/join/$groupId"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun createChannel(name: String, username: String, about: String, avatarUrl: String, isPublic: Boolean): Chat {
        val uid = requireUserId()
        val channelId = "channel_${UUID.randomUUID()}"
        val docRef = db.collection("chats").document(channelId)

        val payload = mapOf(
            "chatId" to channelId,
            "type" to "channel",
            "name" to name,
            "about" to about,
            "avatarUrl" to avatarUrl,
            "channelUsername" to username.trim().removePrefix("@").lowercase(),
            "ownerId" to uid,
            "participantUids" to listOf(uid),
            "adminUids" to listOf(uid),
            "lastMessage" to "Channel created",
            "lastMessageType" to "text",
            "isPublic" to isPublic,
            "inviteLink" to "https://barq.chat/c/${username.ifBlank { channelId }}",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            docRef.set(payload).await()
            return Chat(
                chatId = channelId,
                type = "channel",
                name = name,
                about = about,
                avatarUrl = avatarUrl,
                channelUsername = username,
                ownerId = uid,
                participantUids = listOf(uid),
                adminUids = listOf(uid),
                isPublic = isPublic,
                inviteLink = "https://barq.chat/c/${username.ifBlank { channelId }}"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun joinChannel(channelId: String) {
        val uid = requireUserId()
        val docRef = db.collection("chats").document(channelId)
        try {
            docRef.update(
                "participantUids", FieldValue.arrayUnion(uid),
                "updatedAt", FieldValue.serverTimestamp()
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun pinMessage(chatId: String, messageId: String) {
        requireUserId()
        val docRef = db.collection("chats").document(chatId)
        try {
            docRef.update(
                "pinnedMessageId", messageId,
                "updatedAt", FieldValue.serverTimestamp()
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun updateChatSettings(chatId: String, isArchived: Boolean? = null, isMuted: Boolean? = null, pinned: Boolean? = null) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("chatSettings").document(chatId)
        val updates = mutableMapOf<String, Any>(
            "chatId" to chatId,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (isArchived != null) updates["isArchived"] = isArchived
        if (isMuted != null) updates["isMuted"] = isMuted
        if (pinned != null) updates["pinned"] = pinned

        try {
            docRef.set(updates, com.google.firebase.firestore.SetOptions.merge()).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    fun observeChatSettings(userId: String): Flow<Map<String, ChatSettings>> = flow {
        val collection = db.collection("users").document(userId).collection("chatSettings")
        emitAll(
            collection.snapshots()
                .map { snapshot ->
                    snapshot.documents.associate { doc ->
                        doc.id to (doc.toObject(ChatSettings::class.java) ?: ChatSettings(chatId = doc.id))
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, collection.path)
                    }
                    throw error
                }
        )
    }
}
