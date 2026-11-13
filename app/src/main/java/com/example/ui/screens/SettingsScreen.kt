package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.data.repository.ContactRepository
import com.example.data.repository.UserRepository
import com.example.ui.components.AvatarView
import com.example.ui.theme.AccentClassicBlue
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentOrange
import com.example.ui.theme.AccentViolet
import com.example.ui.theme.TelegramBlue
import com.example.util.NotificationHelper
import com.example.util.NotificationPreferences
import com.example.util.catchToFallback
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentUserId: String,
    userRepository: UserRepository,
    contactRepository: ContactRepository,
    isDarkMode: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    currentAccent: Color,
    onChangeAccent: (Color) -> Unit,
    onBack: () -> Unit,
    onNavigateToEditProfile: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val userProfile by remember(currentUserId) {
        userRepository.observeUserProfile(currentUserId).catchToFallback(null)
    }.collectAsState(initial = null)
    val blockedUsers by remember(currentUserId) {
        contactRepository.observeBlockedUsers(currentUserId).catchToFallback(emptyList())
    }.collectAsState(initial = emptyList())

    var notifPrefs by remember { mutableStateOf(NotificationHelper.getPreferences(context)) }
    var hasSystemNotifPermission by remember { mutableStateOf(NotificationHelper.hasNotificationPermission(context)) }

    var showSignOutDialog by remember { mutableStateOf(false) }
    var showPrivacySheet by remember { mutableStateOf(false) }
    var showBlockedUsersSheet by remember { mutableStateOf(false) }
    var showSessionsSheet by remember { mutableStateOf(false) }
    var showNotificationsSheet by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasSystemNotifPermission = granted
        if (granted) {
            Toast.makeText(context, "تم تفعيل إشعارات التطبيق", Toast.LENGTH_SHORT).show()
            runCatching {
                FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                    coroutineScope.launch { userRepository.saveFcmToken(token) }
                }
            }
        } else {
            Toast.makeText(context, "تم رفض إذن الإشعارات", Toast.LENGTH_SHORT).show()
        }
    }

    fun updateNotifPrefs(transform: (NotificationPreferences) -> NotificationPreferences) {
        val next = transform(notifPrefs)
        notifPrefs = next
        NotificationHelper.savePreferences(context, next)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                title = { Text("الإعدادات", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("settings_list")
        ) {
            // Profile Card Header
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onNavigateToEditProfile)
                            .padding(16.dp)
                    ) {
                        AvatarView(
                            name = userProfile?.displayName ?: "User",
                            avatarUrl = userProfile?.avatarUrl ?: "",
                            size = 64.dp,
                            isOnline = true,
                            showOnlineDot = true
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userProfile?.displayName?.ifBlank { "مستخدم برق" } ?: "مستخدم برق",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "@${userProfile?.username ?: "user"}",
                                fontSize = 14.sp,
                                color = TelegramBlue
                            )
                            if (userProfile?.bio?.isNotBlank() == true) {
                                Text(
                                    text = userProfile!!.bio,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }

                        IconButton(onClick = onNavigateToEditProfile) {
                            Icon(Icons.Default.Edit, contentDescription = "تعديل الملف الشخصي", tint = TelegramBlue)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // Section 1: Appearance & Themes
            item {
                SectionHeader("المظهر والألوان")
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DarkMode, contentDescription = null, tint = TelegramBlue)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("الوضع الداكن (Dark Mode)", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            }
                            Switch(
                                checked = isDarkMode,
                                onCheckedChange = onToggleDarkMode,
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = TelegramBlue),
                                modifier = Modifier.testTag("dark_mode_switch")
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

                        Text("لون السمة المفضل:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val colors = listOf(AccentClassicBlue, AccentCyan, AccentEmerald, AccentViolet, AccentOrange)
                            colors.forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (currentAccent == color) 3.dp else 0.dp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            shape = CircleShape
                                        )
                                        .clickable { onChangeAccent(color) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (currentAccent == color) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // Section 2: Privacy, Security & Notifications
            item {
                SectionHeader("الإشعارات والأصوات والخصوصية")
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column {
                        SettingsRow(
                            icon = Icons.Default.Notifications,
                            title = "الإشعارات والأصوات",
                            subtitle = when {
                                !hasSystemNotifPermission -> "يتطلب منح إذن الإشعارات من النظام"
                                !notifPrefs.notificationsEnabled -> "الإشعارات متوقفة"
                                else -> "مفعّلة للمحادثات والمجموعات والقنوات"
                            },
                            onClick = { showNotificationsSheet = true },
                            modifier = Modifier.testTag("notifications_settings_row")
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        SettingsRow(
                            icon = Icons.Default.Security,
                            title = "إعدادات الخصوصية",
                            subtitle = "آخر ظهور، صورة الحساب",
                            onClick = { showPrivacySheet = true }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        SettingsRow(
                            icon = Icons.Default.Block,
                            title = "المستخدمون المحظورون",
                            subtitle = "${blockedUsers.size} مستخدمين",
                            onClick = { showBlockedUsersSheet = true }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        SettingsRow(
                            icon = Icons.Default.Devices,
                            title = "الجلسات والأجهزة النشطة",
                            subtitle = "جهاز Android الحالي",
                            onClick = { showSessionsSheet = true }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // Section 3: Upcoming Features ("المزايا القادمة")
            item {
                SectionHeader("المزايا القادمة")
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("upcoming_features_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "الاتصالات والمكالمات (قريباً • غير متاح حالياً)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        UpcomingFeatureRow(
                            icon = Icons.Default.Call,
                            title = "المكالمات الصوتية",
                            badge = "قريباً"
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        UpcomingFeatureRow(
                            icon = Icons.Default.Videocam,
                            title = "مكالمات الفيديو",
                            badge = "قريباً"
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        UpcomingFeatureRow(
                            icon = Icons.Default.CameraAlt,
                            title = "استخدام الكاميرا أثناء المكالمات",
                            badge = "قريباً"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // Section 4: About App & Version 26.11.13
            item {
                SectionHeader("حول التطبيق")
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    SettingsRow(
                        icon = Icons.Default.Info,
                        title = "حول تطبيق بَرْق",
                        subtitle = "الإصدار ${BuildConfig.VERSION_NAME}",
                        onClick = { showAboutDialog = true }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Section 5: Sign Out & Footer Version
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSignOutDialog = true }
                            .padding(16.dp)
                            .testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "تسجيل الخروج",
                            tint = Color(0xFFE53935)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = "تسجيل الخروج",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE53935)
                        )
                    }
                }

                Text(
                    text = "بَرْق لنظام Android • الإصدار ${BuildConfig.VERSION_NAME}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                        .testTag("app_version_text")
                )
            }
        }
    }

    // Notifications Settings Sheet
    if (showNotificationsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showNotificationsSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text("إعدادات الإشعارات والأصوات", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasSystemNotifPermission) {
                    Surface(
                        color = Color(0xFFE53935).copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "إذن الإشعارات غير ممنوح في إعدادات النظام",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE53935),
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue)
                            ) {
                                Text("تفعيل إذن النظام", color = Color.White)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                NotifSwitchRow(
                    title = "تفعيل الإشعارات",
                    subtitle = "تشغيل أو إيقاف جميع تنبيهات الرسائل",
                    checked = notifPrefs.notificationsEnabled,
                    onCheckedChange = { enabled -> updateNotifPrefs { it.copy(notificationsEnabled = enabled) } }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))

                NotifSwitchRow(
                    title = "إشعارات المحادثات الخاصة",
                    subtitle = "تنبيه فوري للرسائل المباشرة",
                    checked = notifPrefs.directNotificationsEnabled,
                    enabled = notifPrefs.notificationsEnabled,
                    onCheckedChange = { enabled -> updateNotifPrefs { it.copy(directNotificationsEnabled = enabled) } }
                )
                NotifSwitchRow(
                    title = "إشعارات المجموعات",
                    subtitle = "تنبيه لرسائل المجموعات المشترك بها",
                    checked = notifPrefs.groupNotificationsEnabled,
                    enabled = notifPrefs.notificationsEnabled,
                    onCheckedChange = { enabled -> updateNotifPrefs { it.copy(groupNotificationsEnabled = enabled) } }
                )
                NotifSwitchRow(
                    title = "إشعارات القنوات",
                    subtitle = "تنبيه لمنشورات القنوات الجديدة",
                    checked = notifPrefs.channelNotificationsEnabled,
                    enabled = notifPrefs.notificationsEnabled,
                    onCheckedChange = { enabled -> updateNotifPrefs { it.copy(channelNotificationsEnabled = enabled) } }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))

                NotifSwitchRow(
                    title = "معاينة نص الرسالة",
                    subtitle = "إظهار محتوى الرسالة داخل الإشعار",
                    checked = notifPrefs.showMessagePreview,
                    enabled = notifPrefs.notificationsEnabled,
                    onCheckedChange = { enabled -> updateNotifPrefs { it.copy(showMessagePreview = enabled) } }
                )
                NotifSwitchRow(
                    title = "الصوت",
                    subtitle = "تشغيل نغمة التنبيه عند وصول رسالة",
                    checked = notifPrefs.soundEnabled,
                    enabled = notifPrefs.notificationsEnabled,
                    onCheckedChange = { enabled -> updateNotifPrefs { it.copy(soundEnabled = enabled) } }
                )
                NotifSwitchRow(
                    title = "الاهتزاز",
                    subtitle = "اهتزاز الجهاز عند استلام الإشعار",
                    checked = notifPrefs.vibrateEnabled,
                    enabled = notifPrefs.notificationsEnabled,
                    onCheckedChange = { enabled -> updateNotifPrefs { it.copy(vibrateEnabled = enabled) } }
                )

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            NotificationHelper.showIncomingMessageNotification(
                                context = context,
                                chatId = "",
                                chatType = "direct",
                                title = "بَرْق",
                                body = "الإشعارات تعمل بنجاح على جهازك"
                            )
                            Toast.makeText(context, "تم إرسال إشعار تحقق إلى شريط التنبيهات", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("test_notification_button")
                ) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = TelegramBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إرسال إشعار تحقق للنظام", color = TelegramBlue, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("حول تطبيق بَرْق (Barq)") },
            text = {
                Column {
                    Text("تطبيق مراسلة فوري حديث مبني على خدمات Firebase السحابية.", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "رقم الإصدار: ${BuildConfig.VERSION_NAME}",
                        fontWeight = FontWeight.Bold,
                        color = TelegramBlue
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("حسناً", color = TelegramBlue, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("تسجيل الخروج") },
            text = { Text("هل أنت متأكد من رغبتك في تسجيل الخروج من تطبيق برق؟") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutDialog = false
                        onSignOut()
                    }
                ) {
                    Text("خروج", color = Color(0xFFE53935), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (showPrivacySheet) {
        val currentPrivacy = userProfile?.lastSeenPrivacy ?: "everyone"
        ModalBottomSheet(onDismissRequest = { showPrivacySheet = false }) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("من يستطيع رؤية آخر ظهور؟", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                listOf(
                    Pair("everyone", "الجميع"),
                    Pair("contacts", "جهات الاتصال فقط"),
                    Pair("nobody", "لا أحد")
                ).forEach { (key, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val profile = userProfile ?: return@clickable
                                coroutineScope.launch {
                                    try {
                                        userRepository.updateUserProfile(
                                            displayName = profile.displayName,
                                            username = profile.username,
                                            bio = profile.bio,
                                            avatarUrl = profile.avatarUrl,
                                            lastSeenPrivacy = key,
                                            avatarPrivacy = profile.avatarPrivacy
                                        )
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "تعذر تحديث الخصوصية", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        RadioButton(
                            selected = currentPrivacy == key,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = TelegramBlue)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(label, fontSize = 15.sp)
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showBlockedUsersSheet) {
        ModalBottomSheet(onDismissRequest = { showBlockedUsersSheet = false }) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("المستخدمون المحظورون", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                if (blockedUsers.isEmpty()) {
                    Text("لا يوجد مستخدمون محظورون حالياً.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    blockedUsers.forEach { bUser ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            AvatarView(name = bUser.blockedName, size = 38.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(bUser.blockedName, fontWeight = FontWeight.SemiBold)
                                Text("@${bUser.blockedUsername}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        try {
                                            contactRepository.unblockUser(bUser.blockedUid)
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "تعذر إلغاء الحظر", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            ) {
                                Text("إلغاء الحظر", color = TelegramBlue)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showSessionsSheet) {
        ModalBottomSheet(onDismissRequest = { showSessionsSheet = false }) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("الأجهزة والجلسات النشطة", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(TelegramBlue.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Devices, contentDescription = null, tint = TelegramBlue)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text("${Build.MANUFACTURER} ${Build.MODEL}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Barq ${BuildConfig.VERSION_NAME} • متصل الآن (هذا الجهاز)", fontSize = 12.sp, color = TelegramBlue)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun NotifSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = TelegramBlue)
        )
    }
}

@Composable
private fun UpcomingFeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    badge: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            modifier = Modifier.weight(1f)
        )
        Surface(
            color = TelegramBlue.copy(alpha = 0.14f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = badge,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TelegramBlue,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = TelegramBlue,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
    )
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = Color.Gray.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
    }
}
