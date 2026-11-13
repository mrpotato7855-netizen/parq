package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.Message
import com.example.data.model.TypingStatus
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

class MessageRepository(private val db: FirebaseFirestore) {

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

    fun observeMessages(chatId: String): Flow<List<Message>> = flow {
        requireUserId()
        val messagesCollection = db.collection("chats").document(chatId).collection("messages")

        emitAll(
            messagesCollection
                .orderBy("createdAt")
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Message::class.java)?.let {
                            if (it.messageId.isBlank()) it.copy(messageId = doc.id) else it
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
                        handleFirestoreError(exceptionToLog, OperationType.LIST, messagesCollection.path)
                    }
                    throw error
                }
        )
    }

    suspend fun sendMessage(
        chatId: String,
        type: String = "text",
        content: String,
        mediaUrl: String = "",
        fileName: String = "",
        fileSize: Long = 0L,
        durationSec: Int = 0,
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        isLiveLocation: Boolean = false,
        liveLocationActive: Boolean = false,
        liveLocationExpiresAt: Long = 0L,
        replyToMessageId: String = "",
        replyToText: String = "",
        replyToSenderName: String = "",
        forwardedFrom: String = "",
        senderName: String = "",
        senderAvatar: String = ""
    ): Message {
        val uid = requireUserId()
        val messageId = "msg_${UUID.randomUUID()}"
        val docRef = db.collection("chats").document(chatId).collection("messages").document(messageId)

        val safeContent = content.take(5000)
        val safeSenderName = senderName.take(60)
        val safeSenderAvatar = senderAvatar.take(2000)
        val safeReplyText = replyToText.take(300)
        val safeReplySenderName = replyToSenderName.take(60)

        val payload = mutableMapOf<String, Any>(
            "messageId" to messageId,
            "chatId" to chatId,
            "senderId" to uid,
            "senderName" to safeSenderName,
            "senderAvatar" to safeSenderAvatar,
            "type" to type,
            "content" to safeContent,
            "status" to "sent",
            "isEdited" to false,
            "viewsCount" to 1L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        if (mediaUrl.isNotBlank()) payload["mediaUrl"] = mediaUrl.take(3000)
        if (fileName.isNotBlank()) payload["fileName"] = fileName.take(150)
        if (fileSize > 0) payload["fileSize"] = fileSize
        if (durationSec > 0) payload["durationSec"] = durationSec
        if (latitude != 0.0 || longitude != 0.0 || type == "location" || type == "live_location") {
            payload["latitude"] = latitude
            payload["longitude"] = longitude
        }
        if (isLiveLocation || type == "live_location") {
            payload["isLiveLocation"] = true
            payload["liveLocationActive"] = liveLocationActive
            payload["liveLocationExpiresAt"] = liveLocationExpiresAt
        }
        if (replyToMessageId.isNotBlank()) {
            payload["replyToMessageId"] = replyToMessageId
            payload["replyToText"] = safeReplyText
            payload["replyToSenderName"] = safeReplySenderName
        }
        if (forwardedFrom.isNotBlank()) {
            payload["forwardedFrom"] = forwardedFrom.take(60)
        }

        try {
            docRef.set(payload).await()

            val lastSnippet = when (type) {
                "image" -> "📷 صورة"
                "video" -> "🎬 فيديو"
                "voice" -> "🎤 رسالة صوتية (${durationSec}ث)"
                "document" -> "📎 $fileName"
                "location" -> "📍 موقع جغرافي"
                "live_location" -> "📡 موقع مباشر"
                "sticker" -> "🎨 ملصق"
                else -> content.take(60)
            }
            db.collection("chats").document(chatId).update(
                mapOf(
                    "lastMessage" to lastSnippet,
                    "lastMessageSenderId" to uid,
                    "lastMessageType" to type,
                    "lastMessageTime" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            return Message(
                messageId = messageId,
                chatId = chatId,
                senderId = uid,
                senderName = safeSenderName,
                senderAvatar = safeSenderAvatar,
                type = type,
                content = safeContent,
                mediaUrl = mediaUrl,
                fileName = fileName,
                fileSize = fileSize,
                durationSec = durationSec,
                latitude = latitude,
                longitude = longitude,
                isLiveLocation = isLiveLocation || type == "live_location",
                liveLocationActive = liveLocationActive,
                liveLocationExpiresAt = liveLocationExpiresAt,
                replyToMessageId = replyToMessageId,
                replyToText = safeReplyText,
                replyToSenderName = safeReplySenderName,
                forwardedFrom = forwardedFrom,
                status = "sent"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateLiveLocation(
        chatId: String,
        messageId: String,
        latitude: Double,
        longitude: Double,
        active: Boolean,
        label: String? = null
    ) {
        requireUserId()
        val docRef = db.collection("chats").document(chatId).collection("messages").document(messageId)
        val updates = mutableMapOf<String, Any>(
            "latitude" to latitude,
            "longitude" to longitude,
            "liveLocationActive" to active,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!label.isNullOrBlank()) {
            updates["content"] = label.take(5000)
        }
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun forwardMessage(
        source: Message,
        targetChatId: String,
        senderName: String,
        senderAvatar: String,
        forwardedFromLabel: String
    ): Message {
        return sendMessage(
            chatId = targetChatId,
            type = if (source.type == "live_location") "location" else source.type,
            content = source.content,
            mediaUrl = source.mediaUrl,
            fileName = source.fileName,
            fileSize = source.fileSize,
            durationSec = source.durationSec,
            latitude = source.latitude,
            longitude = source.longitude,
            forwardedFrom = forwardedFromLabel.ifBlank { source.senderName.ifBlank { "مستخدم" } },
            senderName = senderName,
            senderAvatar = senderAvatar
        )
    }

    suspend fun editMessage(chatId: String, messageId: String, newContent: String) {
        val uid = requireUserId()
        val docRef = db.collection("chats").document(chatId).collection("messages").document(messageId)
        val payload = mapOf(
            "content" to newContent.take(5000),
            "isEdited" to true,
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

    suspend fun deleteMessage(chatId: String, messageId: String) {
        requireUserId()
        val docRef = db.collection("chats").document(chatId).collection("messages").document(messageId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun markMessageRead(chatId: String, messageId: String) {
        requireUserId()
        val docRef = db.collection("chats").document(chatId).collection("messages").document(messageId)
        try {
            docRef.update(
                mapOf(
                    "status" to "read",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            // Non-critical, ignore
        }
    }

    suspend fun incrementViews(chatId: String, messageId: String) {
        val docRef = db.collection("chats").document(chatId).collection("messages").document(messageId)
        try {
            docRef.update(
                mapOf(
                    "viewsCount" to FieldValue.increment(1),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            // Ignore view increment errors
        }
    }

    suspend fun setTyping(chatId: String, isTyping: Boolean, userName: String) {
        val uid = auth.currentUser?.uid ?: return
        val statusId = "${chatId}_${uid}"
        val docRef = db.collection("typingStatus").document(statusId)
        try {
            if (isTyping) {
                docRef.set(
                    mapOf(
                        "statusId" to statusId,
                        "chatId" to chatId,
                        "userId" to uid,
                        "userName" to userName,
                        "isTyping" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.delete().await()
            }
        } catch (e: Exception) {
            // Non-critical
        }
    }

    fun observeTypingStatus(chatId: String): Flow<List<TypingStatus>> = flow {
        val currentUid = auth.currentUser?.uid ?: ""
        val query = db.collection("typingStatus")
            .whereEqualTo("chatId", chatId)
            .whereEqualTo("isTyping", true)

        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(TypingStatus::class.java)?.let {
                            if (it.statusId.isBlank()) it.copy(statusId = doc.id) else it
                        }
                    }.filter { it.userId != currentUid }
                }
                .catch {
                    emit(emptyList())
                }
        )
    }
}
