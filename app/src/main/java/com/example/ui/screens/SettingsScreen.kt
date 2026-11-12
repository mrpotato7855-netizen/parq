package com.example.ui.screens

import android.os.Build
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.User
import com.example.data.repository.ContactRepository
import com.example.data.repository.UserRepository
import com.example.ui.components.AvatarView
import com.example.ui.theme.AccentClassicBlue
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentOrange
import com.example.ui.theme.AccentViolet
import com.example.ui.theme.TelegramBlue
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
    val coroutineScope = rememberCoroutineScope()
    val userProfile by userRepository.observeUserProfile(currentUserId).collectAsState(initial = null)
    val blockedUsers by contactRepository.observeBlockedUsers(currentUserId).collectAsState(initial = emptyList())

    var showSignOutDialog by remember { mutableStateOf(false) }
    var showPrivacySheet by remember { mutableStateOf(false) }
    var showBlockedUsersSheet by remember { mutableStateOf(false) }
    var showSessionsSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                            Icon(Icons.Default.Edit, contentDescription = "Edit Profile", tint = TelegramBlue)
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

            // Section 2: Privacy & Security
            item {
                SectionHeader("الخصوصية والأمان")
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column {
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

            // Section 3: Sign Out
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
                            contentDescription = "Sign Out",
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
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Sign Out Confirmation Dialog
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

    // Privacy Settings Bottom Sheet
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
                                coroutineScope.launch {
                                    userRepository.updateUserProfile(
                                        displayName = userProfile?.displayName ?: "",
                                        username = userProfile?.username ?: "",
                                        bio = userProfile?.bio ?: "",
                                        avatarUrl = userProfile?.avatarUrl ?: "",
                                        lastSeenPrivacy = key,
                                        avatarPrivacy = userProfile?.avatarPrivacy ?: "everyone"
                                    )
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

    // Blocked Users Bottom Sheet
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
                                        contactRepository.unblockUser(bUser.blockedUid)
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

    // Active Sessions Bottom Sheet
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
                        Text("${Build.MANUFACTURER.capitalize()} ${Build.MODEL}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Barq Android App • متصل الآن (هذا الجهاز)", fontSize = 12.sp, color = TelegramBlue)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
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
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
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
