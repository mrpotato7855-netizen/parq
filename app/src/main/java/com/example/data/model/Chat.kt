package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Chat(
    @DocumentId val chatId: String = "",
    val type: String = "direct", // direct, group, channel
    val name: String = "",
    val about: String = "",
    val avatarUrl: String = "",
    val channelUsername: String = "",
    val ownerId: String = "",
    val participantUids: List<String> = emptyList(),
    val adminUids: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastMessageSenderId: String = "",
    @ServerTimestamp val lastMessageTime: Timestamp? = null,
    val lastMessageType: String = "text",
    val pinnedMessageId: String = "",
    val isPublic: Boolean = false,
    val inviteLink: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
