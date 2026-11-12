package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Message(
    @DocumentId val messageId: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderAvatar: String = "",
    val type: String = "text", // text, image, voice, document, location, sticker
    val content: String = "",
    val mediaUrl: String = "",
    val fileName: String = "",
    val fileSize: Long = 0L,
    val durationSec: Int = 0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val replyToMessageId: String = "",
    val replyToText: String = "",
    val replyToSenderName: String = "",
    val forwardedFrom: String = "",
    val status: String = "sent", // sent, delivered, read
    val isEdited: Boolean = false,
    val viewsCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
