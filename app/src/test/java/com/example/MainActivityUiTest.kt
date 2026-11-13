package com.example

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.ChatRepository
import com.example.data.repository.ContactRepository
import com.example.data.repository.UserRepository
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ContactsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.BarqTheme
import com.example.ui.theme.TelegramBlue
import com.example.util.NotificationHelper
import com.example.util.NotificationPreferences
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainActivityUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var firestore: FirebaseFirestore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseId = context.getString(R.string.firestore_database_id)
        val app = if (FirebaseApp.getApps(context).isEmpty()) {
            val newApp = FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("com.aistudio.telechat.vknzql")
                    .setProjectId("demo-no-project")
                    .setApiKey("fake-api-key")
                    .build()
            )
            val fs = FirebaseFirestore.getInstance(newApp, databaseId)
            fs.firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
            newApp
        } else {
            FirebaseApp.getInstance()
        }
        firestore = FirebaseFirestore.getInstance(app, databaseId)
    }

    @Test
    fun appVersion_is261113() {
        assertEquals("26.11.13", BuildConfig.VERSION_NAME)
    }

    @Test
    fun mainActivity_launchesCleanly() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        assertNotNull(controller.get())
        controller.pause().stop().destroy()
    }

    @Test
    fun authScreen_rendersGoogleSignInButton() {
        composeRule.setContent {
            BarqTheme {
                AuthScreen(onAuthSuccess = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("google_sign_in_button")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun homeScreen_withUnauthedFirestoreError_rendersGracefullyWithoutCrash() {
        val chatRepo = ChatRepository(firestore)
        val contactRepo = ContactRepository(firestore)
        composeRule.setContent {
            BarqTheme {
                HomeScreen(
                    currentUserId = "test_uid_123",
                    chatRepository = chatRepo,
                    contactRepository = contactRepo,
                    onNavigateToChat = {},
                    onNavigateToNewDirectChat = {},
                    onNavigateToCreateGroup = {},
                    onNavigateToCreateChannel = {},
                    onNavigateToContacts = {},
                    onNavigateToSettings = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("new_chat_fab").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_rendersUpcomingCallsAndVersion261113() {
        val userRepo = UserRepository(firestore)
        val contactRepo = ContactRepository(firestore)
        composeRule.setContent {
            BarqTheme {
                SettingsScreen(
                    currentUserId = "test_uid_123",
                    userRepository = userRepo,
                    contactRepository = contactRepo,
                    isDarkMode = false,
                    onToggleDarkMode = {},
                    currentAccent = TelegramBlue,
                    onChangeAccent = {},
                    onBack = {},
                    onNavigateToEditProfile = {},
                    onSignOut = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("upcoming_features_card"))
        composeRule.onNodeWithTag("upcoming_features_card").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("app_version_text"))
        composeRule.onNodeWithTag("app_version_text").assertIsDisplayed()
    }

    @Test
    fun contactsScreen_rendersSearchAndSavedMessagesItem() {
        val userRepo = UserRepository(firestore)
        val contactRepo = ContactRepository(firestore)
        val chatRepo = ChatRepository(firestore)
        composeRule.setContent {
            BarqTheme {
                ContactsScreen(
                    currentUserId = "test_uid_123",
                    userRepository = userRepo,
                    contactRepository = contactRepo,
                    chatRepository = chatRepo,
                    onBack = {},
                    onNavigateToChat = {},
                    onNavigateToUserProfile = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("contacts_search_field").assertIsDisplayed()
        composeRule.onNodeWithTag("saved_messages_item").assertIsDisplayed()
        composeRule.onNodeWithText("المحفوظات").assertIsDisplayed()
    }

    @Test
    fun notificationHelper_preferencesPersistAndFilterByChatType() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val updated = NotificationPreferences(
            notificationsEnabled = true,
            directNotificationsEnabled = true,
            groupNotificationsEnabled = false,
            channelNotificationsEnabled = true,
            soundEnabled = false,
            vibrateEnabled = true,
            showMessagePreview = false
        )
        NotificationHelper.savePreferences(context, updated)

        val restored = NotificationHelper.getPreferences(context)
        assertEquals(updated, restored)
        assertTrue(NotificationHelper.shouldNotifyForType(restored, "direct"))
        assertFalse(NotificationHelper.shouldNotifyForType(restored, "group"))
        assertTrue(NotificationHelper.shouldNotifyForType(restored, "channel"))

        val masterOff = restored.copy(notificationsEnabled = false)
        assertFalse(NotificationHelper.shouldNotifyForType(masterOff, "direct"))
        assertFalse(NotificationHelper.shouldNotifyForType(masterOff, "channel"))
    }

    @Test
    fun notificationHelper_postsRespectsPreviewAndCancelsNotification() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancelAll()
        val shadowNm = Shadows.shadowOf(manager)

        NotificationHelper.savePreferences(
            app,
            NotificationPreferences(
                notificationsEnabled = true,
                directNotificationsEnabled = true,
                showMessagePreview = true
            )
        )
        NotificationHelper.showIncomingMessageNotification(
            context = app,
            chatId = "chat_abc",
            chatType = "direct",
            title = "أحمد",
            body = "مرحبا بك في برق"
        )

        assertEquals(1, shadowNm.allNotifications.size)
        val posted = shadowNm.allNotifications.first()
        assertEquals("أحمد", posted.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        assertEquals("مرحبا بك في برق", posted.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
        assertEquals(NotificationHelper.CHANNEL_DIRECT, posted.channelId)

        NotificationHelper.cancelChatNotification(app, "chat_abc")
        assertEquals(0, shadowNm.allNotifications.size)

        NotificationHelper.savePreferences(app, NotificationPreferences(notificationsEnabled = false))
        NotificationHelper.showIncomingMessageNotification(
            context = app,
            chatId = "chat_abc",
            chatType = "direct",
            title = "أحمد",
            body = "رسالة ثانية"
        )
        assertEquals(0, shadowNm.allNotifications.size)
    }

    @Test
    fun settingsScreen_opensNotificationSettingsSheet() {
        val userRepo = UserRepository(firestore)
        val contactRepo = ContactRepository(firestore)
        composeRule.setContent {
            BarqTheme {
                SettingsScreen(
                    currentUserId = "test_uid_123",
                    userRepository = userRepo,
                    contactRepository = contactRepo,
                    isDarkMode = false,
                    onToggleDarkMode = {},
                    currentAccent = TelegramBlue,
                    onChangeAccent = {},
                    onBack = {},
                    onNavigateToEditProfile = {},
                    onSignOut = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("notifications_settings_row"))
        composeRule.onNodeWithTag("notifications_settings_row").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("إعدادات الإشعارات والأصوات").assertIsDisplayed()
        composeRule.onNodeWithText("تفعيل الإشعارات").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("إشعارات المحادثات الخاصة").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("إشعارات المجموعات").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("إشعارات القنوات").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("test_notification_button")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
