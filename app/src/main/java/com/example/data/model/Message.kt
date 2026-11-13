package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Message(
    val messageId: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderAvatar: String = "",
    val type: String = "text", // text, image, video, voice, document, location, live_location, sticker
    val content: String = "",
    val mediaUrl: String = "",
    val fileName: String = "",
    val fileSize: Long = 0L,
    val durationSec: Int = 0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    @get:PropertyName("isLiveLocation") @field:PropertyName("isLiveLocation")
    val isLiveLocation: Boolean = false,
    val liveLocationActive: Boolean = false,
    val liveLocationExpiresAt: Long = 0L,
    val replyToMessageId: String = "",
    val replyToText: String = "",
    val replyToSenderName: String = "",
    val forwardedFrom: String = "",
    val status: String = "sent", // sent, delivered, read
    @get:PropertyName("isEdited") @field:PropertyName("isEdited")
    val isEdited: Boolean = false,
    val viewsCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
