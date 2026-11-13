package com.example.util

import android.util.Log
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BarqMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = Firebase.auth.currentUser?.uid ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dbId = getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                db.collection("users").document(uid)
                    .collection("fcmTokens").document(token.take(100))
                    .set(
                        mapOf(
                            "token" to token,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
            } catch (e: Exception) {
                Log.w("BarqFCM", "Failed to save FCM token", e)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val chatId = message.data["chatId"].orEmpty()
        val chatType = message.data["chatType"] ?: "direct"
        val title = message.notification?.title ?: message.data["title"] ?: getString(R.string.app_name)
        val body = message.notification?.body ?: message.data["body"] ?: return

        NotificationHelper.showIncomingMessageNotification(
            context = applicationContext,
            chatId = chatId,
            chatType = chatType,
            title = title,
            body = body
        )
    }
}
