package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class ChatSettings(
    @DocumentId val chatId: String = "",
    val isArchived: Boolean = false,
    val isMuted: Boolean = false,
    val pinned: Boolean = false,
    val unreadCount: Int = 0,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Contact(
    @DocumentId val contactUid: String = "",
    val contactUsername: String = "",
    val contactName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class BlockedUser(
    @DocumentId val blockedUid: String = "",
    val blockedUsername: String = "",
    val blockedName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class TypingStatus(
    @DocumentId val statusId: String = "",
    val chatId: String = "",
    val userId: String = "",
    val userName: String = "",
    val isTyping: Boolean = false,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
