package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.ChatRepository
import com.example.data.repository.ContactRepository
import com.example.data.repository.UserRepository
import com.example.ui.components.AvatarView
import com.example.ui.theme.TelegramBlue
import com.example.util.TimeUtils
import com.example.util.catchToFallback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    targetUserId: String,
    currentUserId: String,
    userRepository: UserRepository,
    contactRepository: ContactRepository,
    chatRepository: ChatRepository,
    onBack: () -> Unit,
    onNavigateToChat: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val targetUser by remember(targetUserId) {
        userRepository.observeUserProfile(targetUserId).catchToFallback(null)
    }.collectAsState(initial = null)
    val contacts by remember(currentUserId) {
        contactRepository.observeContacts(currentUserId).catchToFallback(emptyList())
    }.collectAsState(initial = emptyList())
    val blockedUsers by remember(currentUserId) {
        contactRepository.observeBlockedUsers(currentUserId).catchToFallback(emptyList())
    }.collectAsState(initial = emptyList())

    val contactEntry = remember(contacts, targetUserId) {
        contacts.firstOrNull { it.contactUid == targetUserId }
    }
    val isContact = contactEntry != null
    val isBlocked = remember(blockedUsers, targetUserId) {
        blockedUsers.any { it.blockedUid == targetUserId }
    }

    var isBusy by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var customNameDraft by remember { mutableStateOf("") }
    var showReportDialog by remember { mutableStateOf(false) }
    var reportReason by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("profile_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                title = { Text("الملف الشخصي", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        modifier = modifier
    ) { padding ->
        val user = targetUser
        if (user == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = TelegramBlue)
            }
        } else {
            val realName = user.displayName.ifBlank { "@${user.username}" }
            val shownName = contactEntry?.effectiveDisplayName()?.ifBlank { realName } ?: realName
            val avatarToDisplay = if (user.avatarPrivacy == "nobody" && user.userId != currentUserId) "" else user.avatarUrl

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        AvatarView(
                            name = shownName,
                            avatarUrl = avatarToDisplay,
                            size = 92.dp,
                            isOnline = user.isOnline,
                            showOnlineDot = true
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = shownName,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (contactEntry != null && contactEntry.customName.isNotBlank() && contactEntry.customName != realName) {
                            Text(
                                text = "الاسم الحقيقي: $realName",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Text(
                            text = "@${user.username}",
                            fontSize = 15.sp,
                            color = TelegramBlue,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Text(
                            text = TimeUtils.formatLastSeen(
                                user.lastSeen,
                                user.isOnline,
                                user.lastSeenPrivacy
                            ),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    if (isBusy) return@Button
                                    isBusy = true
                                    coroutineScope.launch {
                                        try {
                                            val chat = chatRepository.createOrGetDirectChat(user)
                                            onNavigateToChat(chat.chatId)
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "تعذر فتح المحادثة", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isBusy = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("profile_message_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("مراسلة", fontWeight = FontWeight.Bold, color = Color.White)
                            }

                            if (user.userId != currentUserId) {
                                OutlinedButton(
                                    onClick = {
                                        if (isBusy) return@OutlinedButton
                                        isBusy = true
                                        coroutineScope.launch {
                                            try {
                                                if (isContact) {
                                                    contactRepository.removeContact(user.userId)
                                                    Toast.makeText(context, "تمت الإزالة من جهات الاتصال", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    contactRepository.addContact(
                                                        contactUid = user.userId,
                                                        contactUsername = user.username,
                                                        contactName = realName
                                                    )
                                                    Toast.makeText(context, "تمت الإضافة إلى جهات الاتصال", Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: CancellationException) {
                                                throw e
                                            } catch (_: Exception) {
                                                Toast.makeText(context, "تعذر تحديث جهات الاتصال", Toast.LENGTH_SHORT).show()
                                            } finally {
                                                isBusy = false
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("toggle_contact_button")
                                ) {
                                    Icon(
                                        imageVector = if (isContact) Icons.Default.PersonRemove else Icons.Default.PersonAdd,
                                        contentDescription = null,
                                        tint = if (isContact) Color(0xFFE53935) else TelegramBlue
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isContact) "حذف من جهات الاتصال" else "إضافة إلى جهات الاتصال",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isContact) Color(0xFFE53935) else TelegramBlue
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (user.bio.isNotBlank()) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("النبذة التعريفية", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TelegramBlue)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(user.bio, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                if (user.userId != currentUserId) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            TextButton(
                                onClick = {
                                    customNameDraft = contactEntry?.customName ?: ""
                                    showRenameDialog = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("edit_custom_name_button")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = TelegramBlue)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (contactEntry?.customName?.isNotBlank() == true) {
                                        "تغيير الاسم عندي فقط (${contactEntry.customName})"
                                    } else {
                                        "تغيير الاسم عندي فقط"
                                    },
                                    color = TelegramBlue,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        try {
                                            if (isBlocked) {
                                                contactRepository.unblockUser(user.userId)
                                                Toast.makeText(context, "تم إلغاء الحظر", Toast.LENGTH_SHORT).show()
                                            } else {
                                                contactRepository.blockUser(user.userId, user.username, realName)
                                                Toast.makeText(context, "تم حظر المستخدم", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "تعذر تنفيذ الإجراء", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Block, contentDescription = null, tint = Color(0xFFE53935))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (isBlocked) "إلغاء حظر المستخدم" else "حظر المستخدم",
                                    color = Color(0xFFE53935),
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            TextButton(
                                onClick = {
                                    reportReason = ""
                                    showReportDialog = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Flag, contentDescription = null, tint = Color(0xFFF57C00))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "إبلاغ عن الحساب",
                                    color = Color(0xFFF57C00),
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            if (showRenameDialog) {
                AlertDialog(
                    onDismissRequest = { showRenameDialog = false },
                    title = { Text("تغيير الاسم عندي فقط") },
                    text = {
                        Column {
                            Text(
                                text = "سيظهر هذا الاسم المخصص لك وحدك في جهات الاتصال وقائمة المحادثات دون تعديل ملف ${realName} الأصلي.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = customNameDraft,
                                onValueChange = { customNameDraft = it },
                                label = { Text("الاسم المخصص (اتركه فارغاً للاسترجاع)") },
                                placeholder = { Text("مثال: أبو علي") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("custom_name_input")
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showRenameDialog = false
                                coroutineScope.launch {
                                    try {
                                        contactRepository.updateContactCustomName(
                                            contactUid = user.userId,
                                            customName = customNameDraft,
                                            contactUsername = user.username,
                                            realDisplayName = realName
                                        )
                                        Toast.makeText(context, "تم حفظ الاسم المخصص لك", Toast.LENGTH_SHORT).show()
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "تعذر حفظ الاسم المخصص", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.testTag("save_custom_name_button")
                        ) {
                            Text("حفظ", color = TelegramBlue, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRenameDialog = false }) {
                            Text("إلغاء")
                        }
                    }
                )
            }

            if (showReportDialog) {
                AlertDialog(
                    onDismissRequest = { showReportDialog = false },
                    title = { Text("إبلاغ عن الحساب") },
                    text = {
                        OutlinedTextField(
                            value = reportReason,
                            onValueChange = { reportReason = it },
                            label = { Text("سبب الإبلاغ") },
                            placeholder = { Text("اذكر سبب البلاغ...") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showReportDialog = false
                                coroutineScope.launch {
                                    try {
                                        userRepository.reportUser(user.userId, reportReason)
                                        Toast.makeText(context, "تم إرسال البلاغ بنجاح", Toast.LENGTH_SHORT).show()
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "تعذر إرسال البلاغ", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) {
                            Text("إرسال", color = Color(0xFFE53935), fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showReportDialog = false }) {
                            Text("إلغاء")
                        }
                    }
                )
            }
        }
    }
}
