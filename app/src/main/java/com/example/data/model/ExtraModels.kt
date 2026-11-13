package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class ChatSettings(
    val chatId: String = "",
    @get:PropertyName("isArchived") @field:PropertyName("isArchived")
    val isArchived: Boolean = false,
    @get:PropertyName("isMuted") @field:PropertyName("isMuted")
    val isMuted: Boolean = false,
    val pinned: Boolean = false,
    val unreadCount: Int = 0,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Contact(
    val contactUid: String = "",
    val contactUsername: String = "",
    val contactName: String = "",
    val customName: String = "",
    val realDisplayName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun effectiveDisplayName(): String {
        return customName.ifBlank { contactName }.ifBlank { realDisplayName }.ifBlank { "@$contactUsername" }
    }
}

@IgnoreExtraProperties
data class BlockedUser(
    val blockedUid: String = "",
    val blockedUsername: String = "",
    val blockedName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class TypingStatus(
    val statusId: String = "",
    val chatId: String = "",
    val userId: String = "",
    val userName: String = "",
    @get:PropertyName("isTyping") @field:PropertyName("isTyping")
    val isTyping: Boolean = false,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
