package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShareLocation
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AdminPermissions
import com.example.data.model.Message
import com.example.data.model.User
import com.example.data.repository.ChatRepository
import com.example.data.repository.ContactRepository
import com.example.data.repository.MessageRepository
import com.example.data.repository.StorageRepository
import com.example.data.repository.UserRepository
import com.example.ui.components.AvatarView
import com.example.ui.components.ChatInputBar
import com.example.ui.components.LocationShareSheet
import com.example.ui.components.MessageBubble
import com.example.ui.components.PinMessageHeader
import com.example.ui.theme.TelegramBlue
import com.example.util.LocationHelper
import com.example.util.TimeUtils
import com.example.util.VoicePlayer
import com.example.util.VoiceRecorder
import com.example.util.catchToFallback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    chatId: String,
    currentUserId: String,
    chatRepository: ChatRepository,
    messageRepository: MessageRepository,
    userRepository: UserRepository,
    contactRepository: ContactRepository,
    storageRepository: StorageRepository,
    onBack: () -> Unit,
    onNavigateToChatInfo: (String) -> Unit,
    onNavigateToUserProfile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val chat by remember(chatId) {
        chatRepository.observeChat(chatId).catchToFallback(null)
    }.collectAsState(initial = null)
    val contacts by remember(currentUserId) {
        contactRepository.observeContacts(currentUserId).catchToFallback(emptyList())
    }.collectAsState(initial = emptyList())
    val contactByUid = remember(contacts) { contacts.associateBy { it.contactUid } }

    var otherUser by remember { mutableStateOf<User?>(null) }
    var currentUserProfile by remember { mutableStateOf<User?>(null) }

    val messages by remember(chatId) {
        messageRepository.observeMessages(chatId).catchToFallback(emptyList())
    }.collectAsState(initial = null)
    val typingUsers by remember(chatId) {
        messageRepository.observeTypingStatus(chatId).catchToFallback(emptyList())
    }.collectAsState(initial = emptyList())
    val allUserChats by remember(currentUserId) {
        chatRepository.observeUserChats(currentUserId).catchToFallback(emptyList())
    }.collectAsState(initial = emptyList())
    val chatSettingsMap by remember(currentUserId) {
        chatRepository.observeChatSettings(currentUserId).catchToFallback(emptyMap())
    }.collectAsState(initial = emptyMap())
    val isChatMuted = chatSettingsMap[chatId]?.isMuted == true

    val voiceRecorder = remember { VoiceRecorder(context) }
    val voicePlayer = remember { VoicePlayer() }
    val playingVoiceId by voicePlayer.currentPlayingId.collectAsState()
    val voiceProgress by voicePlayer.progress.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var replyingMessage by remember { mutableStateOf<Message?>(null) }
    var editingMessage by remember { mutableStateOf<Message?>(null) }
    var forwardingMessage by remember { mutableStateOf<Message?>(null) }
    var showLocationSheet by remember { mutableStateOf(false) }
    var isUploadingMedia by remember { mutableStateOf(false) }

    var activeLiveLocationMsgId by remember { mutableStateOf<String?>(null) }
    var liveLocationJob by remember { mutableStateOf<Job?>(null) }

    var searchInChatQuery by remember { mutableStateOf("") }
    var isSearchInChatActive by remember { mutableStateOf(false) }

    LaunchedEffect(currentUserId) {
        try {
            currentUserProfile = userRepository.getOrCreateProfile("")
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    val directPeerUid = remember(chat, currentUserId) {
        val c = chat ?: return@remember null
        if (c.type == "direct" && !c.isSavedMessages) {
            c.participantUids.firstOrNull { it != currentUserId }
        } else null
    }

    LaunchedEffect(directPeerUid) {
        if (directPeerUid != null) {
            userRepository.observeUserProfile(directPeerUid)
                .catchToFallback(null)
                .collect { user -> otherUser = user }
        } else {
            otherUser = null
        }
    }

    // Scroll to bottom on new messages
    LaunchedEffect(messages?.size) {
        val count = messages?.size ?: 0
        if (count > 0) {
            listState.animateScrollToItem(count - 1)
        }
    }

    // Auto mark incoming messages as read
    LaunchedEffect(messages) {
        messages?.forEach { msg ->
            if (msg.senderId != currentUserId && msg.status != "read") {
                messageRepository.markMessageRead(chatId, msg.messageId)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceRecorder.cancelRecording()
            voicePlayer.stop()
            liveLocationJob?.cancel()
            coroutineScope.launch {
                messageRepository.setTyping(chatId, false, "")
            }
        }
    }

    val pinnedMessage = remember(messages, chat?.pinnedMessageId) {
        messages?.firstOrNull { it.messageId == chat?.pinnedMessageId }
    }

    val filteredMessages = remember(messages, searchInChatQuery) {
        val list = messages ?: emptyList()
        if (searchInChatQuery.isBlank()) list else {
            list.filter {
                it.content.contains(searchInChatQuery, ignoreCase = true) ||
                    it.fileName.contains(searchInChatQuery, ignoreCase = true)
            }
        }
    }

    val isSavedMessages = chat?.isSavedMessages == true || chatId == "saved_$currentUserId"
    val isUserMember = chat?.participantUids?.contains(currentUserId) == true
    val isChannel = chat?.type == "channel"
    val isGroup = chat?.type == "group"
    val canPostInChat = when {
        chat == null -> true
        isSavedMessages -> true
        isChannel -> chat!!.hasAdminPermission(currentUserId, AdminPermissions.CAN_POST_MESSAGES)
        isGroup && chat!!.onlyAdminsCanPost -> chat!!.hasAdminPermission(currentUserId, AdminPermissions.CAN_POST_MESSAGES)
        else -> true
    }
    val canDeleteAny = chat?.hasAdminPermission(currentUserId, AdminPermissions.CAN_DELETE_MESSAGES) == true

    fun stopLiveLocationSharing(msgId: String, lat: Double = 0.0, lng: Double = 0.0) {
        liveLocationJob?.cancel()
        liveLocationJob = null
        if (activeLiveLocationMsgId == msgId) {
            activeLiveLocationMsgId = null
        }
        coroutineScope.launch {
            try {
                messageRepository.updateLiveLocation(
                    chatId = chatId,
                    messageId = msgId,
                    latitude = lat,
                    longitude = lng,
                    active = false
                )
                Toast.makeText(context, "تم إيقاف مشاركة الموقع المباشر", Toast.LENGTH_SHORT).show()
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
                    IconButton(onClick = onBack, modifier = Modifier.testTag("chat_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                title = {
                    if (isSearchInChatActive) {
                        OutlinedTextField(
                            value = searchInChatQuery,
                            onValueChange = { searchInChatQuery = it },
                            placeholder = { Text("بحث في الرسائل...", fontSize = 14.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                    } else {
                        val chatTitle = when {
                            isSavedMessages -> "المحفوظات"
                            directPeerUid != null -> {
                                contactByUid[directPeerUid]?.effectiveDisplayName()?.takeIf { it.isNotBlank() }
                                    ?: otherUser?.displayName?.ifBlank { "@${otherUser?.username ?: ""}" }
                                    ?: chat?.name
                                    ?: "محادثة"
                            }
                            else -> chat?.name ?: "محادثة"
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable {
                                    if (directPeerUid != null) {
                                        onNavigateToUserProfile(directPeerUid)
                                    } else {
                                        onNavigateToChatInfo(chatId)
                                    }
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            if (isSavedMessages) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(TelegramBlue, androidx.compose.foundation.shape.CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Bookmark, contentDescription = null, tint = Color.White)
                                }
                            } else {
                                val avatarUrl = when {
                                    directPeerUid != null && otherUser?.avatarPrivacy != "nobody" -> otherUser?.avatarUrl ?: ""
                                    else -> chat?.avatarUrl ?: ""
                                }
                                AvatarView(
                                    name = chatTitle,
                                    avatarUrl = avatarUrl,
                                    size = 40.dp,
                                    isOnline = otherUser?.isOnline == true,
                                    showOnlineDot = directPeerUid != null
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column {
                                Text(
                                    text = chatTitle,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                val subtitle = when {
                                    isSavedMessages -> "رسائلك وملفاتك المحفوظة"
                                    typingUsers.isNotEmpty() -> "يكتب الآن..."
                                    directPeerUid != null && otherUser != null -> {
                                        TimeUtils.formatLastSeen(
                                            otherUser?.lastSeen,
                                            otherUser?.isOnline == true,
                                            otherUser?.lastSeenPrivacy ?: "everyone"
                                        )
                                    }
                                    isGroup -> "${chat?.participantUids?.size ?: 0} أعضاء"
                                    isChannel -> "${chat?.participantUids?.size ?: 0} مشتركين"
                                    else -> ""
                                }

                                Text(
                                    text = subtitle,
                                    fontSize = 12.sp,
                                    color = if (typingUsers.isNotEmpty()) TelegramBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (!isSavedMessages) {
                        IconButton(
                            onClick = {
                                val nextMuted = !isChatMuted
                                coroutineScope.launch {
                                    try {
                                        chatRepository.updateChatSettings(chatId, isMuted = nextMuted)
                                        Toast.makeText(
                                            context,
                                            if (nextMuted) "تم كتم إشعارات المحادثة" else "تم تفعيل إشعارات المحادثة",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "تعذر تحديث إعداد الإشعارات", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.testTag("chat_mute_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (isChatMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                                contentDescription = if (isChatMuted) "إلغاء كتم الإشعارات" else "كتم الإشعارات"
                            )
                        }
                    }
                    IconButton(onClick = {
                        isSearchInChatActive = !isSearchInChatActive
                        if (!isSearchInChatActive) searchInChatQuery = ""
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "بحث في المحادثة")
                    }
                    IconButton(
                        onClick = { onNavigateToChatInfo(chatId) },
                        modifier = Modifier.testTag("chat_info_button")
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "معلومات المحادثة")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Active Live Location Banner
            val currentLiveId = activeLiveLocationMsgId
            if (currentLiveId != null) {
                val liveMsg = messages?.firstOrNull { it.messageId == currentLiveId }
                Surface(
                    color = Color(0xFFE53935).copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.ShareLocation, contentDescription = null, tint = Color(0xFFE53935), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "تتم مشاركة موقعك المباشر الآن",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFE53935),
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                stopLiveLocationSharing(
                                    currentLiveId,
                                    liveMsg?.latitude ?: 0.0,
                                    liveMsg?.longitude ?: 0.0
                                )
                            }
                        ) {
                            Icon(Icons.Default.StopCircle, contentDescription = null, tint = Color(0xFFE53935), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إيقاف", color = Color(0xFFE53935), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Pinned message banner
            if (pinnedMessage != null) {
                PinMessageHeader(
                    pinnedMessage = pinnedMessage,
                    onClick = {
                        val index = messages?.indexOfFirst { it.messageId == pinnedMessage.messageId } ?: -1
                        if (index >= 0) {
                            coroutineScope.launch { listState.animateScrollToItem(index) }
                        }
                    },
                    onUnpin = {
                        coroutineScope.launch {
                            try {
                                chatRepository.pinMessage(chatId, "")
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                Toast.makeText(context, "تعذر إلغاء التثبيت", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }

            // Message Stream List
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = TelegramBlue)
                    }
                } else if (filteredMessages.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = when {
                                searchInChatQuery.isNotBlank() -> "لم يتم العثور على رسائل مطابقة"
                                isSavedMessages -> "مساحتك السحابية الخاصة!\nاحفظ هنا الملاحظات، الصور، الفيديوهات، الصوتيات، الملفات، الروابط والمواقع."
                                else -> "لا توجد رسائل بعد.\nأرسل رسالة للبدء!"
                            },
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 4.dp)
                    ) {
                        items(filteredMessages, key = { it.messageId }) { msg ->
                            val isOutgoing = msg.senderId == currentUserId
                            val resolvedSenderName = contactByUid[msg.senderId]?.effectiveDisplayName()
                                ?.takeIf { it.isNotBlank() }
                                ?: msg.senderName
                            val displayMessage = if (resolvedSenderName != msg.senderName) {
                                msg.copy(senderName = resolvedSenderName)
                            } else {
                                msg
                            }

                            if (isChannel && !isOutgoing) {
                                LaunchedEffect(msg.messageId) {
                                    messageRepository.incrementViews(chatId, msg.messageId)
                                }
                            }

                            MessageBubble(
                                message = displayMessage,
                                isOutgoing = isOutgoing,
                                isChannel = isChannel,
                                canDeleteAnyMessage = canDeleteAny,
                                playingVoiceId = playingVoiceId,
                                voiceProgress = voiceProgress,
                                onPlayVoice = { id, url ->
                                    voicePlayer.playVoice(id, url, coroutineScope) { err ->
                                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onReply = { replyingMessage = it },
                                onEdit = {
                                    editingMessage = it
                                    inputText = it.content
                                },
                                onForward = { forwardingMessage = it },
                                onStopLiveLocation = { target ->
                                    stopLiveLocationSharing(target.messageId, target.latitude, target.longitude)
                                },
                                onSenderClick = { senderUid ->
                                    if (senderUid != currentUserId) {
                                        onNavigateToUserProfile(senderUid)
                                    }
                                },
                                onDelete = { targetMsg ->
                                    coroutineScope.launch {
                                        try {
                                            if (targetMsg.mediaUrl.startsWith("https://")) {
                                                storageRepository.deleteByDownloadUrl(targetMsg.mediaUrl)
                                            }
                                            messageRepository.deleteMessage(chatId, targetMsg.messageId)
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "تعذر حذف الرسالة", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onPin = { targetMsg ->
                                    coroutineScope.launch {
                                        try {
                                            chatRepository.pinMessage(chatId, targetMsg.messageId)
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "تعذر تثبيت الرسالة", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Bottom Input Bar or Restriction / Join Bar
            if (isChannel && !isUserMember && !canPostInChat) {
                Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    try {
                                        chatRepository.joinChannel(chatId)
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "تعذر الانضمام للقناة", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text("انضمام للقناة", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            } else if (!canPostInChat) {
                Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (isChannel) {
                                "أنت مشترك في هذه القناة. المشرفون المخوّلون فقط يمكنهم النشر."
                            } else {
                                "النشر في هذه المجموعة مقتصر حالياً على المشرفين فقط."
                            },
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                val senderDisplayName = currentUserProfile?.displayName?.ifBlank { "مستخدم" } ?: "مستخدم"
                val senderAvatarUrl = currentUserProfile?.avatarUrl ?: ""

                ChatInputBar(
                    text = inputText,
                    isUploadingMedia = isUploadingMedia,
                    onTextChange = {
                        inputText = it
                        if (!isSavedMessages) {
                            coroutineScope.launch {
                                messageRepository.setTyping(
                                    chatId,
                                    it.isNotBlank(),
                                    senderDisplayName
                                )
                            }
                        }
                    },
                    onSendText = { textToSend ->
                        val cleanText = textToSend.trim()
                        if (cleanText.isNotBlank()) {
                            val reply = replyingMessage
                            inputText = ""
                            replyingMessage = null
                            coroutineScope.launch {
                                try {
                                    messageRepository.setTyping(chatId, false, "")
                                    messageRepository.sendMessage(
                                        chatId = chatId,
                                        type = "text",
                                        content = cleanText,
                                        replyToMessageId = reply?.messageId ?: "",
                                        replyToText = reply?.content ?: "",
                                        replyToSenderName = reply?.senderName ?: "",
                                        senderName = senderDisplayName,
                                        senderAvatar = senderAvatarUrl
                                    )
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    Toast.makeText(context, "تعذر إرسال الرسالة", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    onSendImageUri = { imageUri ->
                        coroutineScope.launch {
                            isUploadingMedia = true
                            try {
                                val remoteUrl = storageRepository.uploadImageUri(chatId, imageUri)
                                messageRepository.sendMessage(
                                    chatId = chatId,
                                    type = "image",
                                    content = "صورة",
                                    mediaUrl = remoteUrl,
                                    senderName = senderDisplayName,
                                    senderAvatar = senderAvatarUrl
                                )
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Toast.makeText(context, e.localizedMessage ?: "تعذر رفع وإرسال الصورة", Toast.LENGTH_LONG).show()
                            } finally {
                                isUploadingMedia = false
                            }
                        }
                    },
                    onSendVideoUri = { videoUri ->
                        coroutineScope.launch {
                            isUploadingMedia = true
                            try {
                                val remoteUrl = storageRepository.uploadVideoUri(chatId, videoUri)
                                messageRepository.sendMessage(
                                    chatId = chatId,
                                    type = "video",
                                    content = "فيديو",
                                    mediaUrl = remoteUrl,
                                    senderName = senderDisplayName,
                                    senderAvatar = senderAvatarUrl
                                )
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Toast.makeText(context, e.localizedMessage ?: "تعذر رفع وإرسال الفيديو", Toast.LENGTH_LONG).show()
                            } finally {
                                isUploadingMedia = false
                            }
                        }
                    },
                    onSendVoice = { localPath, dur ->
                        coroutineScope.launch {
                            isUploadingMedia = true
                            val file = File(localPath)
                            try {
                                val remoteUrl = storageRepository.uploadVoiceFile(chatId, file)
                                messageRepository.sendMessage(
                                    chatId = chatId,
                                    type = "voice",
                                    content = "رسالة صوتية",
                                    mediaUrl = remoteUrl,
                                    durationSec = dur,
                                    senderName = senderDisplayName,
                                    senderAvatar = senderAvatarUrl
                                )
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Toast.makeText(context, e.localizedMessage ?: "تعذر رفع الرسالة الصوتية", Toast.LENGTH_LONG).show()
                            } finally {
                                runCatching { file.delete() }
                                isUploadingMedia = false
                            }
                        }
                    },
                    onOpenLocationPicker = { showLocationSheet = true },
                    onSendDocumentUri = { docUri ->
                        coroutineScope.launch {
                            isUploadingMedia = true
                            try {
                                val (remoteUrl, meta) = storageRepository.uploadDocumentUri(context, chatId, docUri)
                                messageRepository.sendMessage(
                                    chatId = chatId,
                                    type = "document",
                                    content = meta.name,
                                    mediaUrl = remoteUrl,
                                    fileName = meta.name,
                                    fileSize = meta.sizeBytes,
                                    senderName = senderDisplayName,
                                    senderAvatar = senderAvatarUrl
                                )
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Toast.makeText(context, e.localizedMessage ?: "تعذر رفع وإرسال الملف", Toast.LENGTH_LONG).show()
                            } finally {
                                isUploadingMedia = false
                            }
                        }
                    },
                    onSendSticker = { sticker ->
                        coroutineScope.launch {
                            try {
                                messageRepository.sendMessage(
                                    chatId = chatId,
                                    type = "sticker",
                                    content = sticker,
                                    senderName = senderDisplayName,
                                    senderAvatar = senderAvatarUrl
                                )
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                Toast.makeText(context, "تعذر إرسال الملصق", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    replyingMessage = replyingMessage,
                    editingMessage = editingMessage,
                    onCancelReply = { replyingMessage = null },
                    onCancelEdit = {
                        editingMessage = null
                        inputText = ""
                    },
                    onConfirmEdit = { newContent ->
                        val msg = editingMessage
                        if (msg != null && newContent.isNotBlank()) {
                            editingMessage = null
                            inputText = ""
                            coroutineScope.launch {
                                try {
                                    messageRepository.editMessage(chatId, msg.messageId, newContent)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    Toast.makeText(context, "تعذر تعديل الرسالة", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    onStartVoiceRecording = {
                        voiceRecorder.startRecording() != null
                    },
                    onStopVoiceRecording = {
                        val (file, dur) = voiceRecorder.stopRecording()
                        Pair(file?.absolutePath, dur)
                    },
                    onCancelVoiceRecording = {
                        voiceRecorder.cancelRecording()
                    }
                )
            }
        }
    }

    // Location Share Sheet
    if (showLocationSheet) {
        val senderDisplayName = currentUserProfile?.displayName?.ifBlank { "مستخدم" } ?: "مستخدم"
        val senderAvatarUrl = currentUserProfile?.avatarUrl ?: ""
        LocationShareSheet(
            onDismiss = { showLocationSheet = false },
            onSendStaticLocation = { lat, lng, label ->
                coroutineScope.launch {
                    try {
                        messageRepository.sendMessage(
                            chatId = chatId,
                            type = "location",
                            content = label,
                            latitude = lat,
                            longitude = lng,
                            senderName = senderDisplayName,
                            senderAvatar = senderAvatarUrl
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        Toast.makeText(context, "تعذر مشاركة الموقع", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onSendLiveLocation = { lat, lng, label, durationMinutes ->
                coroutineScope.launch {
                    try {
                        val expiresAt = System.currentTimeMillis() + (durationMinutes * 60_000L)
                        val sent = messageRepository.sendMessage(
                            chatId = chatId,
                            type = "live_location",
                            content = label,
                            latitude = lat,
                            longitude = lng,
                            isLiveLocation = true,
                            liveLocationActive = true,
                            liveLocationExpiresAt = expiresAt,
                            senderName = senderDisplayName,
                            senderAvatar = senderAvatarUrl
                        )
                        activeLiveLocationMsgId = sent.messageId
                        liveLocationJob?.cancel()
                        liveLocationJob = coroutineScope.launch {
                            try {
                                LocationHelper.observeLiveLocationUpdates(context, 10_000L).collect { loc ->
                                    if (System.currentTimeMillis() >= expiresAt) {
                                        stopLiveLocationSharing(sent.messageId, loc.latitude, loc.longitude)
                                        return@collect
                                    }
                                    val newLabel = LocationHelper.reverseGeocodeLabel(context, loc.latitude, loc.longitude)
                                    messageRepository.updateLiveLocation(
                                        chatId = chatId,
                                        messageId = sent.messageId,
                                        latitude = loc.latitude,
                                        longitude = loc.longitude,
                                        active = true,
                                        label = newLabel
                                    )
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        Toast.makeText(context, "تعذر بدء مشاركة الموقع المباشر", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // Forward Message Sheet
    forwardingMessage?.let { targetMsg ->
        val senderDisplayName = currentUserProfile?.displayName?.ifBlank { "مستخدم" } ?: "مستخدم"
        val senderAvatarUrl = currentUserProfile?.avatarUrl ?: ""
        val originLabel = targetMsg.senderName.ifBlank { senderDisplayName }

        ModalBottomSheet(onDismissRequest = { forwardingMessage = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "إعادة توجيه إلى...",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Always allow forwarding to Saved Messages ("المحفوظات")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            forwardingMessage = null
                            coroutineScope.launch {
                                try {
                                    val savedChat = chatRepository.getOrCreateSavedMessagesChat()
                                    messageRepository.forwardMessage(
                                        source = targetMsg,
                                        targetChatId = savedChat.chatId,
                                        senderName = senderDisplayName,
                                        senderAvatar = senderAvatarUrl,
                                        forwardedFromLabel = originLabel
                                    )
                                    Toast.makeText(context, "تم الحفظ في المحفوظات", Toast.LENGTH_SHORT).show()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    Toast.makeText(context, "تعذر الحفظ في المحفوظات", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .padding(vertical = 10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(TelegramBlue, androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Bookmark, contentDescription = null, tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("المحفوظات", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("حفظ هذه الرسالة في مساحتك الخاصة", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Filled.Forward, contentDescription = null, tint = TelegramBlue)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))

                LazyColumn(modifier = Modifier.height(260.dp)) {
                    items(
                        allUserChats.filter { !it.isSavedMessages && it.chatId != chatId },
                        key = { it.chatId }
                    ) { destChat ->
                        val peerUid = if (destChat.type == "direct") {
                            destChat.participantUids.firstOrNull { it != currentUserId }
                        } else null
                        val destName = if (peerUid != null) {
                            contactByUid[peerUid]?.effectiveDisplayName()?.takeIf { it.isNotBlank() }
                                ?: destChat.name
                        } else {
                            destChat.name
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    forwardingMessage = null
                                    coroutineScope.launch {
                                        try {
                                            messageRepository.forwardMessage(
                                                source = targetMsg,
                                                targetChatId = destChat.chatId,
                                                senderName = senderDisplayName,
                                                senderAvatar = senderAvatarUrl,
                                                forwardedFromLabel = originLabel
                                            )
                                            Toast.makeText(context, "تمت إعادة التوجيه إلى $destName", Toast.LENGTH_SHORT).show()
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "تعذر إعادة التوجيه", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            AvatarView(name = destName, avatarUrl = destChat.avatarUrl, size = 42.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(destName.ifBlank { "محادثة" }, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Filled.Forward, contentDescription = null, tint = TelegramBlue)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
