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
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Chat::class.java)?.let {
                            if (it.chatId.isBlank()) it.copy(chatId = doc.id) else it
                        }
                    }.sortedByDescending {
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
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Chat::class.java)?.let {
                            if (it.chatId.isBlank()) it.copy(chatId = doc.id) else it
                        }
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

    suspend fun getChatById(chatId: String): Chat? {
        requireUserId()
        val docRef = db.collection("chats").document(chatId)
        val snapshot = try {
            docRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
        return if (snapshot.exists()) {
            snapshot.toObject(Chat::class.java)?.let {
                if (it.chatId.isBlank()) it.copy(chatId = snapshot.id) else it
            }
        } else null
    }

    suspend fun createOrGetDirectChat(targetUser: User): Chat {
        val uid = requireUserId()
        // Generate deterministic chatId for direct chat between two users
        val sortedUids = listOf(uid, targetUser.userId).sorted()
        val directChatId = "direct_${sortedUids[0]}_${sortedUids[1]}"
        val docRef = db.collection("chats").document(directChatId)
        val snapshot = try {
            docRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }

        if (snapshot.exists()) {
            return snapshot.toObject(Chat::class.java)?.let {
                if (it.chatId.isBlank()) it.copy(chatId = directChatId) else it
            } ?: Chat(chatId = directChatId, type = "direct", ownerId = uid, participantUids = sortedUids)
        }

        val isSelfChat = targetUser.userId == uid
        val chatName = if (isSelfChat) {
            "الرسائل المحفوظة"
        } else {
            targetUser.displayName.ifBlank { "@${targetUser.username}" }.take(100)
        }
        val chatAvatar = targetUser.avatarUrl.take(2000)

        val payload = mapOf(
            "chatId" to directChatId,
            "type" to "direct",
            "name" to chatName,
            "avatarUrl" to chatAvatar,
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
                name = chatName,
                avatarUrl = chatAvatar,
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
        val allParticipants = (memberUids + uid).distinct().take(200)
        val safeName = name.trim().ifBlank { "مجموعة" }.take(100)
        val safeAbout = about.trim().take(500)
        val safeAvatar = avatarUrl.trim().take(2000)

        val payload = mapOf(
            "chatId" to groupId,
            "type" to "group",
            "name" to safeName,
            "about" to safeAbout,
            "avatarUrl" to safeAvatar,
            "ownerId" to uid,
            "participantUids" to allParticipants,
            "adminUids" to listOf(uid),
            "lastMessage" to "Group created",
            "lastMessageType" to "text",
            "isPublic" to false,
            "inviteLink" to "https://barq.chat/join/$groupId".take(300),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            docRef.set(payload).await()
            return Chat(
                chatId = groupId,
                type = "group",
                name = safeName,
                about = safeAbout,
                avatarUrl = safeAvatar,
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
        val safeName = name.trim().ifBlank { "قناة" }.take(100)
        val safeAbout = about.trim().take(500)
        val safeAvatar = avatarUrl.trim().take(2000)
        val safeUsername = username.trim().removePrefix("@").lowercase().take(40)
        val inviteLink = "https://barq.chat/c/${safeUsername.ifBlank { channelId }}".take(300)

        val payload = mapOf(
            "chatId" to channelId,
            "type" to "channel",
            "name" to safeName,
            "about" to safeAbout,
            "avatarUrl" to safeAvatar,
            "channelUsername" to safeUsername,
            "ownerId" to uid,
            "participantUids" to listOf(uid),
            "adminUids" to listOf(uid),
            "lastMessage" to "Channel created",
            "lastMessageType" to "text",
            "isPublic" to isPublic,
            "inviteLink" to inviteLink,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            docRef.set(payload).await()
            return Chat(
                chatId = channelId,
                type = "channel",
                name = safeName,
                about = safeAbout,
                avatarUrl = safeAvatar,
                channelUsername = safeUsername,
                ownerId = uid,
                participantUids = listOf(uid),
                adminUids = listOf(uid),
                isPublic = isPublic,
                inviteLink = inviteLink
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    fun observeChat(chatId: String): Flow<Chat?> = flow {
        requireUserId()
        val docRef = db.collection("chats").document(chatId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(Chat::class.java)?.let {
                            if (it.chatId.isBlank()) it.copy(chatId = snapshot.id) else it
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

    suspend fun getOrCreateSavedMessagesChat(): Chat {
        val uid = requireUserId()
        val savedChatId = "saved_$uid"
        val docRef = db.collection("chats").document(savedChatId)
        val snapshot = try {
            docRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }

        if (snapshot.exists()) {
            return snapshot.toObject(Chat::class.java)?.let {
                if (it.chatId.isBlank()) it.copy(chatId = savedChatId, isSavedMessages = true) else it
            } ?: Chat(
                chatId = savedChatId,
                type = "direct",
                name = "المحفوظات",
                ownerId = uid,
                participantUids = listOf(uid),
                isSavedMessages = true
            )
        }

        val payload = mapOf(
            "chatId" to savedChatId,
            "type" to "direct",
            "name" to "المحفوظات",
            "about" to "مساحتك السحابية الخاصة لحفظ الرسائل والملفات والروابط",
            "avatarUrl" to "",
            "ownerId" to uid,
            "participantUids" to listOf(uid),
            "adminUids" to listOf(uid),
            "lastMessage" to "",
            "lastMessageType" to "text",
            "isPublic" to false,
            "isSavedMessages" to true,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            return Chat(
                chatId = savedChatId,
                type = "direct",
                name = "المحفوظات",
                about = "مساحتك السحابية الخاصة لحفظ الرسائل والملفات والروابط",
                ownerId = uid,
                participantUids = listOf(uid),
                adminUids = listOf(uid),
                isPublic = false,
                isSavedMessages = true
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateGroupOrChannelSettings(
        chatId: String,
        name: String,
        about: String,
        avatarUrl: String,
        coverUrl: String,
        isPublic: Boolean,
        channelUsername: String,
        onlyAdminsCanPost: Boolean,
        joinRequiresApproval: Boolean
    ) {
        requireUserId()
        val docRef = db.collection("chats").document(chatId)
        val safeName = name.trim().ifBlank { "محادثة" }.take(100)
        val safeAbout = about.trim().take(500)
        val safeAvatar = avatarUrl.trim().take(2000)
        val safeCover = coverUrl.trim().take(2000)
        val safeUsername = channelUsername.trim().removePrefix("@").lowercase().take(40)
        val inviteLink = if (safeUsername.isNotBlank() && isPublic) {
            "https://barq.chat/c/$safeUsername".take(300)
        } else {
            "https://barq.chat/join/$chatId".take(300)
        }

        val updates = mapOf(
            "name" to safeName,
            "about" to safeAbout,
            "avatarUrl" to safeAvatar,
            "coverUrl" to safeCover,
            "isPublic" to isPublic,
            "channelUsername" to safeUsername,
            "onlyAdminsCanPost" to onlyAdminsCanPost,
            "joinRequiresApproval" to joinRequiresApproval,
            "inviteLink" to inviteLink,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun addMemberToChat(chatId: String, targetUserId: String) {
        requireUserId()
        val docRef = db.collection("chats").document(chatId)
        try {
            docRef.update(
                mapOf(
                    "participantUids" to FieldValue.arrayUnion(targetUserId),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun removeMemberFromChat(chatId: String, targetUserId: String) {
        requireUserId()
        val docRef = db.collection("chats").document(chatId)
        try {
            val snap = docRef.get().await()
            val chat = snap.toObject(Chat::class.java) ?: return
            if (targetUserId == chat.ownerId) {
                throw IllegalStateException("لا يمكن إزالة مالك المجموعة أو القناة")
            }
            val updatedParticipants = chat.participantUids.filter { it != targetUserId }
            val updatedAdmins = chat.adminUids.filter { it != targetUserId }
            val updatedPerms = chat.adminPermissions.toMutableMap().apply { remove(targetUserId) }
            docRef.update(
                mapOf(
                    "participantUids" to updatedParticipants,
                    "adminUids" to updatedAdmins,
                    "adminPermissions" to updatedPerms,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun updateAdminAndPermissions(
        chatId: String,
        targetUserId: String,
        isAdmin: Boolean,
        permissions: Map<String, Boolean>
    ) {
        requireUserId()
        val docRef = db.collection("chats").document(chatId)
        try {
            val snap = docRef.get().await()
            val chat = snap.toObject(Chat::class.java) ?: return
            if (targetUserId == chat.ownerId) return
            val updatedAdmins = if (isAdmin) {
                (chat.adminUids + targetUserId).distinct().take(50)
            } else {
                chat.adminUids.filter { it != targetUserId }
            }
            val updatedPerms = chat.adminPermissions.toMutableMap()
            if (isAdmin) {
                updatedPerms[targetUserId] = permissions
            } else {
                updatedPerms.remove(targetUserId)
            }
            docRef.update(
                mapOf(
                    "adminUids" to updatedAdmins,
                    "adminPermissions" to updatedPerms,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
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
                        val settings = doc.toObject(ChatSettings::class.java)?.let {
                            if (it.chatId.isBlank()) it.copy(chatId = doc.id) else it
                        } ?: ChatSettings(chatId = doc.id)
                        doc.id to settings
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
