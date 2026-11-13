package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.AdminPermissions
import com.example.data.model.User
import com.example.data.repository.ChatRepository
import com.example.data.repository.ContactRepository
import com.example.data.repository.StorageRepository
import com.example.data.repository.UserRepository
import com.example.ui.components.AvatarView
import com.example.ui.theme.TelegramBlue
import com.example.util.catchToFallback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInfoScreen(
    chatId: String,
    currentUserId: String,
    chatRepository: ChatRepository,
    userRepository: UserRepository,
    contactRepository: ContactRepository,
    storageRepository: StorageRepository,
    onBack: () -> Unit,
    onNavigateToUserProfile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val chat by remember(chatId) {
        chatRepository.observeChat(chatId).catchToFallback(null)
    }.collectAsState(initial = null)
    val contacts by remember(currentUserId) {
        contactRepository.observeContacts(currentUserId).catchToFallback(emptyList())
    }.collectAsState(initial = emptyList())
    val contactByUid = remember(contacts) { contacts.associateBy { it.contactUid } }

    var membersByUid by remember { mutableStateOf<Map<String, User>>(emptyMap()) }
    var candidateUsers by remember { mutableStateOf<List<User>>(emptyList()) }

    var showEditSettingsSheet by remember { mutableStateOf(false) }
    var showAddMemberSheet by remember { mutableStateOf(false) }
    var editingAdminUser by remember { mutableStateOf<User?>(null) }

    LaunchedEffect(chat?.participantUids) {
        val pids = chat?.participantUids ?: emptyList()
        if (pids.isNotEmpty()) {
            try {
                val users = userRepository.getUsersByIds(pids)
                membersByUid = users.associateBy { it.userId }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("chat_info_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                title = { Text("معلومات المحادثة", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        modifier = modifier
    ) { padding ->
        val c = chat
        if (c == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = TelegramBlue)
            }
        } else {
            val isGroupOrChannel = c.type == "group" || c.type == "channel"
            val canEditInfo = isGroupOrChannel && c.hasAdminPermission(currentUserId, AdminPermissions.CAN_EDIT_INFO)
            val canManageMembers = isGroupOrChannel && c.hasAdminPermission(currentUserId, AdminPermissions.CAN_MANAGE_MEMBERS)
            val otherUid = if (c.type == "direct" && !c.isSavedMessages) {
                c.participantUids.firstOrNull { it != currentUserId }
            } else null
            val otherUser = otherUid?.let { membersByUid[it] }
            val headerName = when {
                c.isSavedMessages -> "المحفوظات"
                otherUid != null -> {
                    val contact = contactByUid[otherUid]
                    contact?.effectiveDisplayName()?.takeIf { it.isNotBlank() }
                        ?: otherUser?.displayName?.ifBlank { "@${otherUser.username}" }
                        ?: c.name
                }
                else -> c.name
            }
            val headerAvatar = if (otherUid != null) (otherUser?.avatarUrl ?: c.avatarUrl) else c.avatarUrl

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (c.coverUrl.isNotBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(c.coverUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "صورة الغلاف",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp)
                            ) {
                                AvatarView(
                                    name = headerName.ifBlank { "Barq" },
                                    avatarUrl = headerAvatar,
                                    size = 86.dp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = headerName,
                                    fontSize = 21.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (c.channelUsername.isNotBlank()) {
                                    Text(
                                        text = "@${c.channelUsername}",
                                        fontSize = 14.sp,
                                        color = TelegramBlue,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                Text(
                                    text = when {
                                        c.isSavedMessages -> "سحابتك الخاصة للمحفوظات والملفات"
                                        c.type == "group" -> "مجموعة • ${c.participantUids.size} أعضاء" + if (c.isPublic) " • عامة" else " • خاصة"
                                        c.type == "channel" -> "قناة • ${c.participantUids.size} مشتركين" + if (c.isPublic) " • عامة" else " • خاصة"
                                        else -> "محادثة خاصة"
                                    },
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )

                                if (canEditInfo) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Button(
                                        onClick = { showEditSettingsSheet = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("edit_chat_settings_button")
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (c.type == "channel") "تعديل إعدادات القناة" else "تعديل إعدادات المجموعة",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (otherUid != null) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Button(
                                        onClick = { onNavigateToUserProfile(otherUid) },
                                        colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("open_peer_profile_button")
                                    ) {
                                        Icon(Icons.Default.Person, contentDescription = null, tint = Color.White)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "عرض الملف الشخصي وإعدادات الاتصال",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }

                // About Card
                if (c.about.isNotBlank()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("الوصف", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TelegramBlue)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(c.about, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }

                // Group/Channel Rules Summary
                if (isGroupOrChannel) {
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("إعدادات الخصوصية والنشر", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TelegramBlue)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (c.isPublic) "النوع: عامة (يمكن العثور عليها والانضمام عبر الرابط)" else "النوع: خاصة (بدعوة فقط)",
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (c.onlyAdminsCanPost || c.type == "channel") "صلاحية النشر: المالك والمشرفون فقط" else "صلاحية النشر: جميع الأعضاء",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                                if (c.joinRequiresApproval) {
                                    Text(
                                        text = "موافقة المشرفين مطلوبة عند الانضمام",
                                        fontSize = 13.sp,
                                        color = TelegramBlue,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Invite Link card
                if (c.inviteLink.isNotBlank()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Invite Link", c.inviteLink))
                                    Toast.makeText(context, "تم نسخ رابط الدعوة", Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null, tint = TelegramBlue)
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("رابط الدعوة", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TelegramBlue)
                                    Text(c.inviteLink, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Icon(Icons.Default.ContentCopy, contentDescription = "نسخ الرابط", tint = TelegramBlue)
                            }
                        }
                    }
                }

                // Members List & Add Member (for groups & channels)
                if (isGroupOrChannel) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "الأعضاء والمشرفون (${c.participantUids.size})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (canManageMembers) {
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            val allUsers = userRepository.listUsers(60)
                                            candidateUsers = allUsers.filter { !c.participantUids.contains(it.userId) }
                                            showAddMemberSheet = true
                                        }
                                    },
                                    modifier = Modifier.testTag("add_member_button")
                                ) {
                                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("إضافة عضو", color = TelegramBlue, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    items(c.participantUids, key = { it }) { uid ->
                        val isOwner = c.ownerId == uid
                        val isAdmin = c.adminUids.contains(uid)
                        val memberUser = membersByUid[uid]
                        val contactAlias = contactByUid[uid]?.effectiveDisplayName()
                        val displayName = when {
                            uid == currentUserId -> "أنت (${memberUser?.displayName?.ifBlank { "حسابك" } ?: "حسابك"})"
                            !contactAlias.isNullOrBlank() -> contactAlias
                            memberUser != null -> memberUser.displayName.ifBlank { "@${memberUser.username}" }
                            else -> "عضو (${uid.take(6)})"
                        }
                        var showMemberMenu by remember { mutableStateOf(false) }

                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (uid != currentUserId) {
                                        onNavigateToUserProfile(uid)
                                    }
                                }
                        ) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    AvatarView(
                                        name = displayName,
                                        avatarUrl = memberUser?.avatarUrl ?: "",
                                        size = 44.dp
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = displayName,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        val sub = buildString {
                                            append(if (isOwner) "المالك" else if (isAdmin) "مشرف" else "عضو")
                                            if (memberUser != null && memberUser.username.isNotBlank()) {
                                                append(" • @${memberUser.username}")
                                            }
                                        }
                                        Text(
                                            text = sub,
                                            fontSize = 12.sp,
                                            color = if (isAdmin || isOwner) TelegramBlue else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (isAdmin || isOwner) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(TelegramBlue.copy(alpha = 0.15f))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = if (isOwner) "مالك" else "مشرف",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TelegramBlue
                                            )
                                        }
                                    }

                                    if (canManageMembers && !isOwner && uid != currentUserId) {
                                        Box {
                                            IconButton(onClick = { showMemberMenu = true }) {
                                                Icon(Icons.Default.MoreVert, contentDescription = "إدارة العضو")
                                            }
                                            DropdownMenu(
                                                expanded = showMemberMenu,
                                                onDismissRequest = { showMemberMenu = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("عرض الملف الشخصي") },
                                                    onClick = {
                                                        showMemberMenu = false
                                                        onNavigateToUserProfile(uid)
                                                    },
                                                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                                                )
                                                if (c.ownerId == currentUserId || canManageMembers) {
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(if (isAdmin) "تعديل صلاحيات المشرف" else "ترقية إلى مشرف")
                                                        },
                                                        onClick = {
                                                            showMemberMenu = false
                                                            editingAdminUser = memberUser ?: User(
                                                                userId = uid,
                                                                username = uid.take(6),
                                                                displayName = displayName
                                                            )
                                                        },
                                                        leadingIcon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null) }
                                                    )
                                                }
                                                DropdownMenuItem(
                                                    text = { Text("إزالة من المحادثة", color = Color(0xFFE53935)) },
                                                    onClick = {
                                                        showMemberMenu = false
                                                        coroutineScope.launch {
                                                            try {
                                                                chatRepository.removeMemberFromChat(c.chatId, uid)
                                                                Toast.makeText(context, "تمت إزالة العضو", Toast.LENGTH_SHORT).show()
                                                            } catch (e: CancellationException) {
                                                                throw e
                                                            } catch (_: Exception) {
                                                                Toast.makeText(context, "تعذر إزالة العضو", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    },
                                                    leadingIcon = {
                                                        Icon(Icons.Default.PersonRemove, contentDescription = null, tint = Color(0xFFE53935))
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            }
                        }
                    }
                }
            }

            // Edit Group / Channel Settings Sheet
            if (showEditSettingsSheet) {
                EditGroupOrChannelSheet(
                    chat = c,
                    storageRepository = storageRepository,
                    onDismiss = { showEditSettingsSheet = false },
                    onSave = { name, about, avatarUrl, coverUrl, isPublic, username, onlyAdminsPost, joinApproval ->
                        coroutineScope.launch {
                            try {
                                chatRepository.updateGroupOrChannelSettings(
                                    chatId = c.chatId,
                                    name = name,
                                    about = about,
                                    avatarUrl = avatarUrl,
                                    coverUrl = coverUrl,
                                    isPublic = isPublic,
                                    channelUsername = username,
                                    onlyAdminsCanPost = onlyAdminsPost,
                                    joinRequiresApproval = joinApproval
                                )
                                Toast.makeText(context, "تم حفظ التعديلات وتحديثها للجميع", Toast.LENGTH_SHORT).show()
                                showEditSettingsSheet = false
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Toast.makeText(context, "تعذر حفظ الإعدادات: ${e.localizedMessage ?: ""}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }

            // Add Member Sheet
            if (showAddMemberSheet) {
                ModalBottomSheet(onDismissRequest = { showAddMemberSheet = false }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "إضافة عضو جديد",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        if (candidateUsers.isEmpty()) {
                            Text(
                                text = "لا يوجد مستخدمون آخرون متاحون للإضافة حالياً.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        } else {
                            LazyColumn(modifier = Modifier.height(320.dp)) {
                                items(candidateUsers, key = { it.userId }) { candidate ->
                                    val label = contactByUid[candidate.userId]?.effectiveDisplayName()
                                        ?.takeIf { it.isNotBlank() }
                                        ?: candidate.displayName.ifBlank { "@${candidate.username}" }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                coroutineScope.launch {
                                                    try {
                                                        chatRepository.addMemberToChat(c.chatId, candidate.userId)
                                                        Toast.makeText(context, "تمت إضافة $label", Toast.LENGTH_SHORT).show()
                                                        showAddMemberSheet = false
                                                    } catch (e: CancellationException) {
                                                        throw e
                                                    } catch (_: Exception) {
                                                        Toast.makeText(context, "تعذر إضافة العضو", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                            .padding(vertical = 10.dp)
                                    ) {
                                        AvatarView(name = label, avatarUrl = candidate.avatarUrl, size = 42.dp)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                            Text("@${candidate.username}", fontSize = 12.sp, color = TelegramBlue)
                                        }
                                        Icon(Icons.Default.PersonAdd, contentDescription = "إضافة", tint = TelegramBlue)
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            // Admin Permissions Dialog
            editingAdminUser?.let { targetUser ->
                val currentlyAdmin = c.adminUids.contains(targetUser.userId)
                val existingPerms = c.adminPermissions[targetUser.userId] ?: AdminPermissions.defaultPermissions()
                AdminPermissionsDialog(
                    userDisplayName = targetUser.displayName.ifBlank { "@${targetUser.username}" },
                    isCurrentlyAdmin = currentlyAdmin,
                    initialPermissions = existingPerms,
                    onDismiss = { editingAdminUser = null },
                    onConfirmAdmin = { perms ->
                        editingAdminUser = null
                        coroutineScope.launch {
                            try {
                                chatRepository.updateAdminAndPermissions(
                                    chatId = c.chatId,
                                    targetUserId = targetUser.userId,
                                    isAdmin = true,
                                    permissions = perms
                                )
                                Toast.makeText(context, "تم تحديث صلاحيات المشرف", Toast.LENGTH_SHORT).show()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                Toast.makeText(context, "تعذر تحديث الصلاحيات", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onRemoveAdmin = {
                        editingAdminUser = null
                        coroutineScope.launch {
                            try {
                                chatRepository.updateAdminAndPermissions(
                                    chatId = c.chatId,
                                    targetUserId = targetUser.userId,
                                    isAdmin = false,
                                    permissions = emptyMap()
                                )
                                Toast.makeText(context, "تمت إزالة صلاحيات الإشراف", Toast.LENGTH_SHORT).show()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                Toast.makeText(context, "تعذر إزالة الإشراف", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditGroupOrChannelSheet(
    chat: com.example.data.model.Chat,
    storageRepository: StorageRepository,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        about: String,
        avatarUrl: String,
        coverUrl: String,
        isPublic: Boolean,
        username: String,
        onlyAdminsCanPost: Boolean,
        joinRequiresApproval: Boolean
    ) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf(chat.name) }
    var about by remember { mutableStateOf(chat.about) }
    var avatarUrl by remember { mutableStateOf(chat.avatarUrl) }
    var coverUrl by remember { mutableStateOf(chat.coverUrl) }
    var isPublic by remember { mutableStateOf(chat.isPublic) }
    var username by remember { mutableStateOf(chat.channelUsername) }
    var onlyAdminsCanPost by remember { mutableStateOf(chat.onlyAdminsCanPost) }
    var joinRequiresApproval by remember { mutableStateOf(chat.joinRequiresApproval) }
    var isUploading by remember { mutableStateOf(false) }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploading = true
            coroutineScope.launch {
                try {
                    avatarUrl = storageRepository.uploadChatMedia(chat.chatId, "avatar", uri)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Toast.makeText(context, e.localizedMessage ?: "تعذر رفع الصورة", Toast.LENGTH_SHORT).show()
                } finally {
                    isUploading = false
                }
            }
        }
    }

    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploading = true
            coroutineScope.launch {
                try {
                    coverUrl = storageRepository.uploadChatMedia(chat.chatId, "cover", uri)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Toast.makeText(context, e.localizedMessage ?: "تعذر رفع الغلاف", Toast.LENGTH_SHORT).show()
                } finally {
                    isUploading = false
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = if (chat.type == "channel") "تعديل إعدادات القناة" else "تعديل إعدادات المجموعة",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .clickable {
                            avatarPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AvatarView(name = name.ifBlank { "Barq" }, avatarUrl = avatarUrl, size = 64.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "تغيير الصورة", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }

                OutlinedButton(
                    onClick = {
                        coverPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !isUploading,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = TelegramBlue)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (coverUrl.isNotBlank()) "تغيير صورة الغلاف" else "إضافة صورة غلاف")
                }
            }

            if (isUploading) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("جاري رفع الصورة...", fontSize = 12.sp, color = TelegramBlue)
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(if (chat.type == "channel") "اسم القناة" else "اسم المجموعة") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_chat_name_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = about,
                onValueChange = { about = it },
                label = { Text("الوصف") },
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_chat_about_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (chat.type == "channel") "قناة عامة" else "مجموعة عامة",
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "يمكن لأي مستخدم الوصول عبر المعرّف والرابط",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isPublic,
                    onCheckedChange = { isPublic = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = TelegramBlue)
                )
            }

            if (isPublic || chat.type == "channel") {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' } },
                    label = { Text("معرّف الرابط (@username)") },
                    prefix = { Text("@", color = TelegramBlue, fontWeight = FontWeight.Bold) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (chat.type == "group") {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("المشرفون فقط يمكنهم الإرسال", fontWeight = FontWeight.Medium)
                        Text(
                            text = "منع الأعضاء العاديين من إرسال رسائل جديدة",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = onlyAdminsCanPost,
                        onCheckedChange = { onlyAdminsCanPost = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = TelegramBlue)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("موافقة المشرفين للانضمام", fontWeight = FontWeight.Medium)
                    Text(
                        text = "تقييد الانضمام التلقائي عبر رابط الدعوة",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = joinRequiresApproval,
                    onCheckedChange = { joinRequiresApproval = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = TelegramBlue)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = {
                    onSave(
                        name,
                        about,
                        avatarUrl,
                        coverUrl,
                        isPublic,
                        username,
                        onlyAdminsCanPost,
                        joinRequiresApproval
                    )
                },
                enabled = !isUploading && name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_chat_settings_button")
            ) {
                Text("حفظ التعديلات", color = Color.White, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AdminPermissionsDialog(
    userDisplayName: String,
    isCurrentlyAdmin: Boolean,
    initialPermissions: Map<String, Boolean>,
    onDismiss: () -> Unit,
    onConfirmAdmin: (Map<String, Boolean>) -> Unit,
    onRemoveAdmin: () -> Unit
) {
    var canEditInfo by remember { mutableStateOf(initialPermissions[AdminPermissions.CAN_EDIT_INFO] ?: true) }
    var canPostMessages by remember { mutableStateOf(initialPermissions[AdminPermissions.CAN_POST_MESSAGES] ?: true) }
    var canDeleteMessages by remember { mutableStateOf(initialPermissions[AdminPermissions.CAN_DELETE_MESSAGES] ?: true) }
    var canManageMembers by remember { mutableStateOf(initialPermissions[AdminPermissions.CAN_MANAGE_MEMBERS] ?: true) }
    var canPinMessages by remember { mutableStateOf(initialPermissions[AdminPermissions.CAN_PIN_MESSAGES] ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("صلاحيات المشرف: $userDisplayName", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                PermissionCheckRow("تعديل المعلومات والإعدادات", canEditInfo) { canEditInfo = it }
                PermissionCheckRow("نشر الرسائل والمنشورات", canPostMessages) { canPostMessages = it }
                PermissionCheckRow("حذف رسائل الأعضاء", canDeleteMessages) { canDeleteMessages = it }
                PermissionCheckRow("إضافة وإزالة الأعضاء والمشرفين", canManageMembers) { canManageMembers = it }
                PermissionCheckRow("تثبيت الرسائل", canPinMessages) { canPinMessages = it }
                if (isCurrentlyAdmin) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = onRemoveAdmin,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("إزالة من قائمة المشرفين", color = Color(0xFFE53935), fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmAdmin(
                        mapOf(
                            AdminPermissions.CAN_EDIT_INFO to canEditInfo,
                            AdminPermissions.CAN_POST_MESSAGES to canPostMessages,
                            AdminPermissions.CAN_DELETE_MESSAGES to canDeleteMessages,
                            AdminPermissions.CAN_MANAGE_MEMBERS to canManageMembers,
                            AdminPermissions.CAN_PIN_MESSAGES to canPinMessages
                        )
                    )
                }
            ) {
                Text("حفظ الصلاحيات", color = TelegramBlue, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

@Composable
private fun PermissionCheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, fontSize = 13.sp)
    }
}
