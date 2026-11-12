package com.example

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.example.data.repository.ChatRepository
import com.example.data.repository.ContactRepository
import com.example.data.repository.MessageRepository
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
import com.example.ui.theme.BarqTheme
import com.example.ui.theme.TelegramBlue
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var userRepository: UserRepository? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val databaseId = getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
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
                        onChangeAccent = { accentColor = it }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Firebase.auth.currentUser != null) {
            val scope = CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
            scope.launch {
                userRepository?.setOnlinePresence(true)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (Firebase.auth.currentUser != null) {
            val scope = CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
            scope.launch {
                userRepository?.setOnlinePresence(false)
            }
        }
    }
}

sealed class Screen {
    data object Home : Screen()
    data class ChatDetail(val chatId: String) : Screen()
    data class ChatInfo(val chatId: String) : Screen()
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
    onChangeAccent: (Color) -> Unit
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
    onSignOut: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val userRepository = remember(db) { UserRepository(db) }
    val chatRepository = remember(db) { ChatRepository(db) }
    val messageRepository = remember(db) { MessageRepository(db) }
    val contactRepository = remember(db) { ContactRepository(db) }

    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

    // Initialize or get profile on start
    LaunchedEffect(currentUserId) {
        val displayName = Firebase.auth.currentUser?.displayName ?: "User"
        val photoUrl = Firebase.auth.currentUser?.photoUrl?.toString() ?: ""
        userRepository.getOrCreateProfile(displayName, photoUrl)
        userRepository.setOnlinePresence(true)
    }

    // Handle system back navigation
    if (currentScreen !is Screen.Home) {
        BackHandler {
            currentScreen = when (currentScreen) {
                is Screen.ChatInfo -> Screen.ChatDetail((currentScreen as Screen.ChatInfo).chatId)
                is Screen.EditProfile -> Screen.Settings
                else -> Screen.Home
            }
        }
    }

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
                    onBack = { currentScreen = Screen.Home },
                    onNavigateToChatInfo = { id ->
                        currentScreen = Screen.ChatInfo(id)
                    }
                )
            }
            is Screen.ChatInfo -> {
                ChatInfoScreen(
                    chatId = screen.chatId,
                    currentUserId = currentUserId,
                    chatRepository = chatRepository,
                    userRepository = userRepository,
                    onBack = { currentScreen = Screen.ChatDetail(screen.chatId) }
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
                    onBack = { currentScreen = Screen.Settings }
                )
            }
        }
    }
}
