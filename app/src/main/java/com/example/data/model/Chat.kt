package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Chat(
    val chatId: String = "",
    val type: String = "direct", // direct, group, channel
    val name: String = "",
    val about: String = "",
    val avatarUrl: String = "",
    val coverUrl: String = "",
    val channelUsername: String = "",
    val ownerId: String = "",
    val participantUids: List<String> = emptyList(),
    val adminUids: List<String> = emptyList(),
    val adminPermissions: Map<String, Map<String, Boolean>> = emptyMap(),
    val lastMessage: String = "",
    val lastMessageSenderId: String = "",
    @ServerTimestamp val lastMessageTime: Timestamp? = null,
    val lastMessageType: String = "text",
    val pinnedMessageId: String = "",
    @get:PropertyName("isPublic") @field:PropertyName("isPublic")
    val isPublic: Boolean = false,
    val onlyAdminsCanPost: Boolean = false,
    val joinRequiresApproval: Boolean = false,
    @get:PropertyName("isSavedMessages") @field:PropertyName("isSavedMessages")
    val isSavedMessages: Boolean = false,
    val inviteLink: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun hasAdminPermission(uid: String, permission: String): Boolean {
        if (uid == ownerId) return true
        if (!adminUids.contains(uid)) return false
        val perms = adminPermissions[uid] ?: return true // Backward compatibility for legacy admins
        return perms[permission] ?: false
    }
}

object AdminPermissions {
    const val CAN_EDIT_INFO = "canEditInfo"
    const val CAN_POST_MESSAGES = "canPostMessages"
    const val CAN_DELETE_MESSAGES = "canDeleteMessages"
    const val CAN_MANAGE_MEMBERS = "canManageMembers"
    const val CAN_PIN_MESSAGES = "canPinMessages"

    fun defaultPermissions(): Map<String, Boolean> = mapOf(
        CAN_EDIT_INFO to true,
        CAN_POST_MESSAGES to true,
        CAN_DELETE_MESSAGES to true,
        CAN_MANAGE_MEMBERS to true,
        CAN_PIN_MESSAGES to true
    )
}
