package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class User(
    @DocumentId val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    val avatarUrl: String = "",
    val isOnline: Boolean = false,
    @ServerTimestamp val lastSeen: Timestamp? = null,
    val lastSeenPrivacy: String = "everyone", // 'everyone', 'contacts', 'nobody'
    val avatarPrivacy: String = "everyone",   // 'everyone', 'nobody'
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
