package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.example.data.repository.ChatRepository
import com.example.data.repository.ContactRepository
import com.example.data.repository.MessageRepository
import com.example.data.repository.StorageRepository
import com.example.data.repository.UserRepository
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatDetailScreen
import com.example.ui.screens.ChatInfoScreen
import com.example.ui.screens.ContactsScreen
import com.example.ui.screens.CreateChannelScreen
import com.example.ui.screens.CreateGroupScreen
import com.example.ui.screens.EditProfileScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.UserProfileScreen
import com.example.ui.theme.BarqTheme
import com.example.ui.theme.TelegramBlue
import com.example.util.NotificationHelper
import com.example.util.catchToFallback
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class InAppNotificationBannerData(
    val chatId: String,
    val title: String,
    val body: String
)

class MainActivity : ComponentActivity() {

    private var userRepository: UserRepository? = null
    private var pendingChatId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper.createChannels(this)
        pendingChatId = intent?.getStringExtra(NotificationHelper.EXTRA_CHAT_ID)

        val app = if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
            val options = com.google.firebase.FirebaseOptions.fromResource(this)
            if (options != null) {
                com.google.firebase.FirebaseApp.initializeApp(this, options)
            } else {
                com.google.firebase.FirebaseApp.initializeApp(this)
            }
        } else {
            com.google.firebase.FirebaseApp.getInstance()
        }

        val databaseId = getString(R.string.firestore_database_id)
        val db = if (app != null) {
            FirebaseFirestore.getInstance(app, databaseId)
        } else {
            FirebaseFirestore.getInstance(databaseId)
        }
        userRepository = UserRepository(db)

        setContent {
            val systemDark = isSystemInDarkTheme()
            var isDarkMode by remember { mutableStateOf(systemDark) }
            var accentColor by remember { mutableStateOf(TelegramBlue) }

            BarqTheme(
                darkTheme = isDarkMode,
                accentColor = accentColor
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent(
                        db = db,
                        isDarkMode = isDarkMode,
                        onToggleDarkMode = { isDarkMode = it },
                        accentColor = accentColor,
                        onChangeAccent = { accentColor = it },
                        pendingChatId = pendingChatId,
                        onConsumePendingChatId = { pendingChatId = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val targetChatId = intent.getStringExtra(NotificationHelper.EXTRA_CHAT_ID)
        if (!targetChatId.isNullOrBlank()) {
            pendingChatId = targetChatId
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            if (Firebase.auth.currentUser != null) {
                val scope = CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
                scope.launch {
                    userRepository?.setOnlinePresence(true)
                }
            }
        } catch (_: Exception) {
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            if (Firebase.auth.currentUser != null) {
                val scope = CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
                scope.launch {
                    userRepository?.setOnlinePresence(false)
                }
            }
        } catch (_: Exception) {
        }
    }
}

sealed class Screen {
    data object Home : Screen()
    data class ChatDetail(val chatId: String) : Screen()
    data class ChatInfo(val chatId: String) : Screen()
    data class UserProfile(val userId: String) : Screen()
    data object Contacts : Screen()
    data object CreateGroup : Screen()
    data object CreateChannel : Screen()
    data object Settings : Screen()
    data object EditProfile : Screen()
}

@Composable
fun AppContent(
    db: FirebaseFirestore,
    isDarkMode: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    accentColor: Color,
    onChangeAccent: (Color) -> Unit,
    pendingChatId: String? = null,
    onConsumePendingChatId: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    if (currentUser == null) {
        AuthScreen(
            onAuthSuccess = {
                currentUser = Firebase.auth.currentUser
            }
        )
    } else {
        val uid = currentUser!!.uid
        key(uid) {
            MainFeatureFlow(
                currentUserId = uid,
                db = db,
                isDarkMode = isDarkMode,
                onToggleDarkMode = onToggleDarkMode,
                accentColor = accentColor,
                onChangeAccent = onChangeAccent,
                pendingChatId = pendingChatId,
                onConsumePendingChatId = onConsumePendingChatId,
                onSignOut = {
                    coroutineScope.launch {
                        withContext(NonCancellable) {
                            try {
                                credentialManager.clearCredentialState(ClearCredentialStateRequest())
                            } catch (e: Exception) {
                                Log.w("Auth", "Failed to clear credentials: ${e.message}")
                            } finally {
                                Firebase.auth.signOut()
                                currentUser = null
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun MainFeatureFlow(
    currentUserId: String,
    db: FirebaseFirestore,
    isDarkMode: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    accentColor: Color,
    onChangeAccent: (Color) -> Unit,
    pendingChatId: String? = null,
    onConsumePendingChatId: () -> Unit = {},
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val userRepository = remember(db) { UserRepository(db) }
    val chatRepository = remember(db) { ChatRepository(db) }
    val messageRepository = remember(db) { MessageRepository(db) }
    val contactRepository = remember(db) { ContactRepository(db) }
    val storageRepository = remember { StorageRepository() }

    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    var previousScreenBeforeProfile by remember { mutableStateOf<Screen>(Screen.Home) }
    var inAppBanner by remember { mutableStateOf<InAppNotificationBannerData?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            runCatching {
                FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                    if (!token.isNullOrBlank()) {
                        coroutineScope.launch { userRepository.saveFcmToken(token) }
                    }
                }
            }
        }
    }

    // Initialize profile, channels, notification permission & FCM token
    LaunchedEffect(currentUserId) {
        NotificationHelper.createChannels(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !NotificationHelper.hasNotificationPermission(context)) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        try {
            val displayName = Firebase.auth.currentUser?.displayName ?: "User"
            val photoUrl = Firebase.auth.currentUser?.photoUrl?.toString() ?: ""
            userRepository.getOrCreateProfile(displayName, photoUrl)
            userRepository.setOnlinePresence(true)
            runCatching {
                FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                    if (!token.isNullOrBlank()) {
                        coroutineScope.launch { userRepository.saveFcmToken(token) }
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("MainFeatureFlow", "Failed to initialize user profile", e)
        }
    }

    // Handle deep link from tapped notification
    LaunchedEffect(pendingChatId) {
        if (!pendingChatId.isNullOrBlank()) {
            currentScreen = Screen.ChatDetail(pendingChatId)
            NotificationHelper.cancelChatNotification(context, pendingChatId)
            onConsumePendingChatId()
        }
    }

    // Cancel active notification when opening a chat
    LaunchedEffect(currentScreen) {
        val active = currentScreen
        if (active is Screen.ChatDetail) {
            NotificationHelper.cancelChatNotification(context, active.chatId)
            if (inAppBanner?.chatId == active.chatId) {
                inAppBanner = null
            }
        }
    }

    // Real-time incoming message notification observer across user's chats
    val observedChats by remember(currentUserId) {
        chatRepository.observeUserChats(currentUserId).catchToFallback(emptyList())
    }.collectAsState(initial = emptyList())
    val chatSettingsMap by remember(currentUserId) {
        chatRepository.observeChatSettings(currentUserId).catchToFallback(emptyMap())
    }.collectAsState(initial = emptyMap())
    val savedContacts by remember(currentUserId) {
        contactRepository.observeContacts(currentUserId).catchToFallback(emptyList())
    }.collectAsState(initial = emptyList())
    val contactByUid = remember(savedContacts) { savedContacts.associateBy { it.contactUid } }

    var hasBaselineChats by remember { mutableStateOf(false) }
    val seenLastTimestamps = remember { mutableStateMapOf<String, Long>() }

    LaunchedEffect(observedChats) {
        if (!hasBaselineChats) {
            observedChats.forEach { chat ->
                val ts = chat.lastMessageTime?.toDate()?.time ?: chat.updatedAt?.toDate()?.time ?: 0L
                seenLastTimestamps[chat.chatId] = ts
            }
            if (observedChats.isNotEmpty()) {
                hasBaselineChats = true
            }
            return@LaunchedEffect
        }

        for (chat in observedChats) {
            val latestTs = chat.lastMessageTime?.toDate()?.time ?: chat.updatedAt?.toDate()?.time ?: 0L
            val prevTs = seenLastTimestamps[chat.chatId] ?: 0L
            if (latestTs > prevTs) {
                seenLastTimestamps[chat.chatId] = latestTs
                val isFromOther = chat.lastMessageSenderId.isNotBlank() && chat.lastMessageSenderId != currentUserId
                val isMuted = chatSettingsMap[chat.chatId]?.isMuted == true
                val isCurrentlyViewingThisChat = (currentScreen as? Screen.ChatDetail)?.chatId == chat.chatId
                val prefs = NotificationHelper.getPreferences(context)

                if (isFromOther && !isMuted && !isCurrentlyViewingThisChat && NotificationHelper.shouldNotifyForType(prefs, chat.type)) {
                    val peerUid = if (chat.type == "direct") {
                        chat.participantUids.firstOrNull { it != currentUserId }
                    } else null
                    val displayTitle = peerUid?.let { contactByUid[it]?.effectiveDisplayName() }
                        ?.takeIf { it.isNotBlank() }
                        ?: chat.name.ifBlank { "رسالة جديدة" }
                    val bodyText = if (prefs.showMessagePreview) {
                        chat.lastMessage.ifBlank { "رسالة جديدة" }
                    } else {
                        "لديك رسالة جديدة"
                    }

                    NotificationHelper.showIncomingMessageNotification(
                        context = context,
                        chatId = chat.chatId,
                        chatType = chat.type,
                        title = displayTitle,
                        body = bodyText
                    )
                    inAppBanner = InAppNotificationBannerData(
                        chatId = chat.chatId,
                        title = displayTitle,
                        body = bodyText
                    )
                }
            }
        }
    }

    LaunchedEffect(inAppBanner) {
        if (inAppBanner != null) {
            delay(4000L)
            inAppBanner = null
        }
    }

    if (currentScreen !is Screen.Home) {
        BackHandler {
            currentScreen = when (val active = currentScreen) {
                is Screen.ChatInfo -> Screen.ChatDetail(active.chatId)
                is Screen.UserProfile -> previousScreenBeforeProfile
                is Screen.EditProfile -> Screen.Settings
                else -> Screen.Home
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "ScreenTransition"
        ) { screen ->
            when (screen) {
                is Screen.Home -> {
                    HomeScreen(
                        currentUserId = currentUserId,
                        chatRepository = chatRepository,
                        contactRepository = contactRepository,
                        onNavigateToChat = { chatId ->
                            currentScreen = Screen.ChatDetail(chatId)
                        },
                        onNavigateToNewDirectChat = {
                            currentScreen = Screen.Contacts
                        },
                        onNavigateToCreateGroup = {
                            currentScreen = Screen.CreateGroup
                        },
                        onNavigateToCreateChannel = {
                            currentScreen = Screen.CreateChannel
                        },
                        onNavigateToContacts = {
                            currentScreen = Screen.Contacts
                        },
                        onNavigateToSettings = {
                            currentScreen = Screen.Settings
                        }
                    )
                }
                is Screen.ChatDetail -> {
                    ChatDetailScreen(
                        chatId = screen.chatId,
                        currentUserId = currentUserId,
                        chatRepository = chatRepository,
                        messageRepository = messageRepository,
                        userRepository = userRepository,
                        contactRepository = contactRepository,
                        storageRepository = storageRepository,
                        onBack = { currentScreen = Screen.Home },
                        onNavigateToChatInfo = { id ->
                            currentScreen = Screen.ChatInfo(id)
                        },
                        onNavigateToUserProfile = { uid ->
                            previousScreenBeforeProfile = screen
                            currentScreen = Screen.UserProfile(uid)
                        }
                    )
                }
                is Screen.ChatInfo -> {
                    ChatInfoScreen(
                        chatId = screen.chatId,
                        currentUserId = currentUserId,
                        chatRepository = chatRepository,
                        userRepository = userRepository,
                        contactRepository = contactRepository,
                        storageRepository = storageRepository,
                        onBack = { currentScreen = Screen.ChatDetail(screen.chatId) },
                        onNavigateToUserProfile = { uid ->
                            previousScreenBeforeProfile = screen
                            currentScreen = Screen.UserProfile(uid)
                        }
                    )
                }
                is Screen.UserProfile -> {
                    UserProfileScreen(
                        targetUserId = screen.userId,
                        currentUserId = currentUserId,
                        userRepository = userRepository,
                        contactRepository = contactRepository,
                        chatRepository = chatRepository,
                        onBack = { currentScreen = previousScreenBeforeProfile },
                        onNavigateToChat = { chatId ->
                            currentScreen = Screen.ChatDetail(chatId)
                        }
                    )
                }
                is Screen.Contacts -> {
                    ContactsScreen(
                        currentUserId = currentUserId,
                        userRepository = userRepository,
                        contactRepository = contactRepository,
                        chatRepository = chatRepository,
                        onBack = { currentScreen = Screen.Home },
                        onNavigateToChat = { chatId ->
                            currentScreen = Screen.ChatDetail(chatId)
                        },
                        onNavigateToUserProfile = { uid ->
                            previousScreenBeforeProfile = Screen.Contacts
                            currentScreen = Screen.UserProfile(uid)
                        }
                    )
                }
                is Screen.CreateGroup -> {
                    CreateGroupScreen(
                        currentUserId = currentUserId,
                        chatRepository = chatRepository,
                        contactRepository = contactRepository,
                        onBack = { currentScreen = Screen.Home },
                        onGroupCreated = { newGroupId ->
                            currentScreen = Screen.ChatDetail(newGroupId)
                        }
                    )
                }
                is Screen.CreateChannel -> {
                    CreateChannelScreen(
                        currentUserId = currentUserId,
                        chatRepository = chatRepository,
                        onBack = { currentScreen = Screen.Home },
                        onChannelCreated = { newChannelId ->
                            currentScreen = Screen.ChatDetail(newChannelId)
                        }
                    )
                }
                is Screen.Settings -> {
                    SettingsScreen(
                        currentUserId = currentUserId,
                        userRepository = userRepository,
                        contactRepository = contactRepository,
                        isDarkMode = isDarkMode,
                        onToggleDarkMode = onToggleDarkMode,
                        currentAccent = accentColor,
                        onChangeAccent = onChangeAccent,
                        onBack = { currentScreen = Screen.Home },
                        onNavigateToEditProfile = { currentScreen = Screen.EditProfile },
                        onSignOut = onSignOut
                    )
                }
                is Screen.EditProfile -> {
                    EditProfileScreen(
                        currentUserId = currentUserId,
                        userRepository = userRepository,
                        storageRepository = storageRepository,
                        onBack = { currentScreen = Screen.Settings }
                    )
                }
            }
        }

        // In-App Top Notification Banner
        AnimatedVisibility(
            visible = inAppBanner != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            inAppBanner?.let { banner ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            inAppBanner = null
                            currentScreen = Screen.ChatDetail(banner.chatId)
                        }
                        .testTag("in_app_notification_banner")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "إشعار رسالة",
                            tint = TelegramBlue,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = banner.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = banner.body,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { inAppBanner = null }) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
