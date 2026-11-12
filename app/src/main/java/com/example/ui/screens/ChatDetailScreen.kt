package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Chat
import com.example.data.model.Message
import com.example.data.model.User
import com.example.data.repository.ChatRepository
import com.example.data.repository.MessageRepository
import com.example.data.repository.UserRepository
import com.example.ui.components.AvatarView
import com.example.ui.components.ChatInputBar
import com.example.ui.components.MessageBubble
import com.example.ui.components.PinMessageHeader
import com.example.ui.theme.TelegramBlue
import com.example.util.TimeUtils
import com.example.util.VoicePlayer
import com.example.util.VoiceRecorder
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    chatId: String,
    currentUserId: String,
    chatRepository: ChatRepository,
    messageRepository: MessageRepository,
    userRepository: UserRepository,
    onBack: () -> Unit,
    onNavigateToChatInfo: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var chat by remember { mutableStateOf<Chat?>(null) }
    var otherUser by remember { mutableStateOf<User?>(null) }
    var currentUserProfile by remember { mutableStateOf<User?>(null) }

    val messages by messageRepository.observeMessages(chatId).collectAsState(initial = null)
    val typingUsers by messageRepository.observeTypingStatus(chatId).collectAsState(initial = emptyList())

    val voiceRecorder = remember { VoiceRecorder(context) }
    val voicePlayer = remember { VoicePlayer() }
    val playingVoiceId by voicePlayer.currentPlayingId.collectAsState()
    val voiceProgress by voicePlayer.progress.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var replyingMessage by remember { mutableStateOf<Message?>(null) }
    var editingMessage by remember { mutableStateOf<Message?>(null) }
    var showTopMenu by remember { mutableStateOf(false) }
    var searchInChatQuery by remember { mutableStateOf("") }
    var isSearchInChatActive by remember { mutableStateOf(false) }

    // Load initial chat & user details
    LaunchedEffect(chatId) {
        val loadedChat = chatRepository.getChatById(chatId)
        chat = loadedChat
        currentUserProfile = userRepository.getOrCreateProfile("")

        if (loadedChat != null && loadedChat.type == "direct") {
            val otherUid = loadedChat.participantUids.firstOrNull { it != currentUserId }
            if (otherUid != null) {
                userRepository.observeUserProfile(otherUid).collect { user ->
                    otherUser = user
                }
            }
        }
    }

    // Scroll to bottom on new messages
    LaunchedEffect(messages?.size) {
        val count = messages?.size ?: 0
        if (count > 0) {
            listState.animateScrollToItem(count - 1)
        }
    }

    // Auto mark as read
    LaunchedEffect(messages) {
        messages?.forEach { msg ->
            if (msg.senderId != currentUserId && msg.status != "read") {
                messageRepository.markMessageRead(chatId, msg.messageId)
            }
        }
    }

    // Cleanup voice player on leave
    DisposableEffect(Unit) {
        onDispose {
            voicePlayer.stop()
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
            list.filter { it.content.contains(searchInChatQuery, ignoreCase = true) }
        }
    }

    val isUserMember = remember(chat, currentUserId) {
        chat?.participantUids?.contains(currentUserId) == true
    }
    val isChannel = chat?.type == "channel"
    val isOwnerOrAdmin = chat?.ownerId == currentUserId || chat?.adminUids?.contains(currentUserId) == true

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("chat_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { onNavigateToChatInfo(chatId) }
                                .padding(vertical = 4.dp)
                        ) {
                            val chatName = if (chat?.type == "direct" && otherUser != null) {
                                otherUser!!.displayName.ifBlank { "@${otherUser!!.username}" }
                            } else {
                                chat?.name ?: "محادثة"
                            }

                            AvatarView(
                                name = chatName,
                                avatarUrl = if (chat?.type == "direct") (otherUser?.avatarUrl ?: "") else (chat?.avatarUrl ?: ""),
                                size = 40.dp,
                                isOnline = otherUser?.isOnline == true,
                                showOnlineDot = chat?.type == "direct"
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Column {
                                Text(
                                    text = chatName,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                val subtitle = when {
                                    typingUsers.isNotEmpty() -> "يكتب الآن..."
                                    chat?.type == "direct" && otherUser != null -> {
                                        TimeUtils.formatLastSeen(
                                            otherUser?.lastSeen,
                                            otherUser?.isOnline == true,
                                            otherUser?.lastSeenPrivacy ?: "everyone"
                                        )
                                    }
                                    chat?.type == "group" -> "${chat?.participantUids?.size ?: 0} أعضاء"
                                    chat?.type == "channel" -> "${chat?.participantUids?.size ?: 0} مشتركين"
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
                    IconButton(onClick = {
                        isSearchInChatActive = !isSearchInChatActive
                        if (!isSearchInChatActive) searchInChatQuery = ""
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "Search in chat")
                    }
                    IconButton(onClick = { onNavigateToChatInfo(chatId) }) {
                        Icon(Icons.Default.Info, contentDescription = "Chat details")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
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
            // Pinned message banner
            if (pinnedMessage != null) {
                PinMessageHeader(
                    pinnedMessage = pinnedMessage,
                    onClick = {
                        val index = messages?.indexOfFirst { it.messageId == pinnedMessage.messageId } ?: -1
                        if (index >= 0) {
                            coroutineScope.launch {
                                listState.animateScrollToItem(index)
                            }
                        }
                    },
                    onUnpin = {
                        coroutineScope.launch {
                            chatRepository.pinMessage(chatId, "")
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
                            text = if (searchInChatQuery.isNotBlank()) "لم يتم العثور على رسائل مطابقة" else "لا توجد رسائل بعد.\nأرسل رسالة للبدء! 👋",
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
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

                            // Increment views for channels
                            if (isChannel && !isOutgoing) {
                                LaunchedEffect(msg.messageId) {
                                    messageRepository.incrementViews(chatId, msg.messageId)
                                }
                            }

                            MessageBubble(
                                message = msg,
                                isOutgoing = isOutgoing,
                                isChannel = isChannel,
                                playingVoiceId = playingVoiceId,
                                voiceProgress = voiceProgress,
                                onPlayVoice = { id, url ->
                                    voicePlayer.playVoice(id, url, coroutineScope)
                                },
                                onReply = { replyingMessage = it },
                                onEdit = {
                                    editingMessage = it
                                    inputText = it.content
                                },
                                onDelete = { targetMsg ->
                                    coroutineScope.launch {
                                        messageRepository.deleteMessage(chatId, targetMsg.messageId)
                                    }
                                },
                                onPin = { targetMsg ->
                                    coroutineScope.launch {
                                        chatRepository.pinMessage(chatId, targetMsg.messageId)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Bottom Input Bar or Channel Join Bar
            if (isChannel && !isOwnerOrAdmin) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier.padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isUserMember) {
                            Text(
                                text = "أنت مشترك في هذه القناة. المشرفون فقط يمكنهم النشر.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        chatRepository.joinChannel(chatId)
                                        chat = chatRepository.getChatById(chatId)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                            ) {
                                Text("انضمام للقناة", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            } else {
                ChatInputBar(
                    text = inputText,
                    onTextChange = {
                        inputText = it
                        coroutineScope.launch {
                            messageRepository.setTyping(
                                chatId,
                                it.isNotBlank(),
                                currentUserProfile?.displayName ?: "User"
                            )
                        }
                    },
                    onSendText = { textToSend ->
                        val text = textToSend.trim()
                        if (text.isNotBlank()) {
                            val reply = replyingMessage
                            inputText = ""
                            replyingMessage = null
                            coroutineScope.launch {
                                messageRepository.setTyping(chatId, false, "")
                                messageRepository.sendMessage(
                                    chatId = chatId,
                                    type = "text",
                                    content = text,
                                    replyToMessageId = reply?.messageId ?: "",
                                    replyToText = reply?.content ?: "",
                                    replyToSenderName = reply?.senderName ?: "",
                                    senderName = currentUserProfile?.displayName ?: "User",
                                    senderAvatar = currentUserProfile?.avatarUrl ?: ""
                                )
                            }
                        }
                    },
                    onSendImage = { imageUrl ->
                        coroutineScope.launch {
                            messageRepository.sendMessage(
                                chatId = chatId,
                                type = "image",
                                content = "صورة",
                                mediaUrl = imageUrl,
                                senderName = currentUserProfile?.displayName ?: "User",
                                senderAvatar = currentUserProfile?.avatarUrl ?: ""
                            )
                        }
                    },
                    onSendVoice = { filePath, dur ->
                        coroutineScope.launch {
                            messageRepository.sendMessage(
                                chatId = chatId,
                                type = "voice",
                                content = "رسالة صوتية",
                                mediaUrl = filePath,
                                durationSec = dur,
                                senderName = currentUserProfile?.displayName ?: "User",
                                senderAvatar = currentUserProfile?.avatarUrl ?: ""
                            )
                        }
                    },
                    onSendLocation = { lat, lng, locName ->
                        coroutineScope.launch {
                            messageRepository.sendMessage(
                                chatId = chatId,
                                type = "location",
                                content = locName,
                                latitude = lat,
                                longitude = lng,
                                senderName = currentUserProfile?.displayName ?: "User",
                                senderAvatar = currentUserProfile?.avatarUrl ?: ""
                            )
                        }
                    },
                    onSendDocument = { fileName, fileSize ->
                        coroutineScope.launch {
                            messageRepository.sendMessage(
                                chatId = chatId,
                                type = "document",
                                content = fileName,
                                fileName = fileName,
                                fileSize = fileSize,
                                senderName = currentUserProfile?.displayName ?: "User",
                                senderAvatar = currentUserProfile?.avatarUrl ?: ""
                            )
                        }
                    },
                    onSendSticker = { sticker ->
                        coroutineScope.launch {
                            messageRepository.sendMessage(
                                chatId = chatId,
                                type = "sticker",
                                content = sticker,
                                senderName = currentUserProfile?.displayName ?: "User",
                                senderAvatar = currentUserProfile?.avatarUrl ?: ""
                            )
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
                                messageRepository.editMessage(chatId, msg.messageId, newContent)
                            }
                        }
                    },
                    onStartVoiceRecording = {
                        voiceRecorder.startRecording()
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
}
