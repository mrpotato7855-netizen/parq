package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Chat
import com.example.data.model.Contact
import com.example.data.repository.ChatRepository
import com.example.data.repository.ContactRepository
import com.example.ui.components.AvatarView
import com.example.ui.theme.LocalChatBubbleColors
import com.example.ui.theme.TelegramBlue
import com.example.util.TimeUtils
import com.example.util.UiState
import com.example.util.asUiState
import com.example.util.catchToFallback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    currentUserId: String,
    chatRepository: ChatRepository,
    contactRepository: ContactRepository,
    onNavigateToChat: (String) -> Unit,
    onNavigateToNewDirectChat: () -> Unit,
    onNavigateToCreateGroup: () -> Unit,
    onNavigateToCreateChannel: () -> Unit,
    onNavigateToContacts: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var retryTrigger by remember { mutableIntStateOf(0) }
    val chatsState by remember(currentUserId, retryTrigger) {
        chatRepository.observeUserChats(currentUserId).asUiState()
    }.collectAsState(initial = UiState.Loading)
    val chatSettingsMap by remember(currentUserId, retryTrigger) {
        chatRepository.observeChatSettings(currentUserId).catchToFallback(emptyMap())
    }.collectAsState(initial = emptyMap())
    val savedContacts by remember(currentUserId) {
        contactRepository.observeContacts(currentUserId).catchToFallback(emptyList())
    }.collectAsState(initial = emptyList())
    val contactByUid = remember(savedContacts) { savedContacts.associateBy { it.contactUid } }

    val chats = (chatsState as? UiState.Success)?.data
    val savedMessagesChat = remember(chats, currentUserId) {
        chats?.firstOrNull { it.isSavedMessages || it.chatId == "saved_$currentUserId" }
    }

    var selectedTab by remember { mutableStateOf("all") } // all, direct, group, channel, archived
    var currentBottomNav by remember { mutableIntStateOf(0) } // 0: Chats, 1: Contacts, 2: Settings
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    var selectedChatForActions by remember { mutableStateOf<Chat?>(null) }
    var showNewChatOptions by remember { mutableStateOf(false) }

    fun resolveChatDisplayName(chat: Chat): String {
        if (chat.isSavedMessages || chat.chatId == "saved_$currentUserId") return "المحفوظات"
        if (chat.type == "direct") {
            val peerUid = chat.participantUids.firstOrNull { it != currentUserId }
            val customLabel = peerUid?.let { contactByUid[it]?.effectiveDisplayName() }
            if (!customLabel.isNullOrBlank()) return customLabel
        }
        return chat.name.ifBlank { "محادثة" }
    }

    val filteredChats = remember(chats, selectedTab, searchQuery, chatSettingsMap, contactByUid) {
        val list = chats ?: emptyList()
        list.filter { chat ->
            if (chat.isSavedMessages || chat.chatId == "saved_$currentUserId") {
                return@filter false // Rendered cleanly as dedicated top Saved Messages card
            }
            val settings = chatSettingsMap[chat.chatId]
            val isArchived = settings?.isArchived == true

            val matchesTab = when (selectedTab) {
                "archived" -> isArchived
                "direct" -> !isArchived && chat.type == "direct"
                "group" -> !isArchived && chat.type == "group"
                "channel" -> !isArchived && chat.type == "channel"
                else -> !isArchived
            }

            val displayName = resolveChatDisplayName(chat)
            val matchesSearch = if (searchQuery.isBlank()) true else {
                displayName.contains(searchQuery, ignoreCase = true) ||
                    chat.name.contains(searchQuery, ignoreCase = true) ||
                    chat.lastMessage.contains(searchQuery, ignoreCase = true) ||
                    chat.channelUsername.contains(searchQuery, ignoreCase = true)
            }

            matchesTab && matchesSearch
        }.sortedWith(
            compareByDescending<Chat> { chatSettingsMap[it.chatId]?.pinned == true }
                .thenByDescending { it.lastMessageTime?.toDate()?.time ?: it.updatedAt?.toDate()?.time ?: 0L }
        )
    }

    val openSavedMessages: () -> Unit = {
        coroutineScope.launch {
            try {
                val savedChat = chatRepository.getOrCreateSavedMessagesChat()
                onNavigateToChat(savedChat.chatId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Toast.makeText(context, "تعذر فتح المحفوظات", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("بحث في المحادثات...", fontSize = 15.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_text_field"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "مسح")
                                    }
                                }
                            }
                        )
                    } else {
                        Text(
                            text = "بَرْق",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) searchQuery = ""
                        },
                        modifier = Modifier.testTag("search_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "بحث"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewChatOptions = true },
                containerColor = TelegramBlue,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.testTag("new_chat_fab")
            ) {
                Icon(Icons.Default.Edit, contentDescription = "محادثة جديدة")
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = currentBottomNav == 0,
                    onClick = { currentBottomNav = 0 },
                    icon = { Icon(Icons.Default.Chat, contentDescription = "المحادثات") },
                    label = { Text("المحادثات") }
                )
                NavigationBarItem(
                    selected = currentBottomNav == 1,
                    onClick = {
                        currentBottomNav = 1
                        onNavigateToContacts()
                    },
                    icon = { Icon(Icons.Default.People, contentDescription = "جهات الاتصال") },
                    label = { Text("جهات الاتصال") }
                )
                NavigationBarItem(
                    selected = currentBottomNav == 2,
                    onClick = {
                        currentBottomNav = 2
                        onNavigateToSettings()
                    },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "الإعدادات") },
                    label = { Text("الإعدادات") }
                )
            }
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    CategoryChip(label = "الكل", selected = selectedTab == "all") { selectedTab = "all" }
                }
                item {
                    CategoryChip(label = "الخاصة", selected = selectedTab == "direct") { selectedTab = "direct" }
                }
                item {
                    CategoryChip(label = "المجموعات", selected = selectedTab == "group") { selectedTab = "group" }
                }
                item {
                    CategoryChip(label = "القنوات", selected = selectedTab == "channel") { selectedTab = "channel" }
                }
                item {
                    CategoryChip(label = "المؤرشفة", selected = selectedTab == "archived") { selectedTab = "archived" }
                }
            }

            when (val state = chatsState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = TelegramBlue)
                    }
                }
                is UiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(
                                text = "تعذر تحميل المحادثات",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = state.message,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(onClick = { retryTrigger++ }) {
                                Text("إعادة المحاولة")
                            }
                        }
                    }
                }
                is UiState.Success -> {
                    val showSavedRow = (selectedTab == "all" || selectedTab == "direct") &&
                        (searchQuery.isBlank() || "المحفوظات".contains(searchQuery, ignoreCase = true))

                    if (filteredChats.isEmpty() && !showSavedRow) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .background(TelegramBlue.copy(alpha = 0.1f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Chat,
                                        contentDescription = null,
                                        tint = TelegramBlue,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = if (selectedTab == "archived") "لا توجد محادثات مؤرشفة" else "لا توجد محادثات هنا بعد",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "ابدأ محادثة جديدة أو انشئ مجموعة أو قناة عبر الزر أدناه",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            if (showSavedRow) {
                                item(key = "saved_messages_pinned_top") {
                                    SavedMessagesHomeRow(
                                        lastSnippet = savedMessagesChat?.lastMessage?.takeIf { it.isNotBlank() }
                                            ?: "مساحتك السحابية الخاصة لحفظ الرسائل والملفات والروابط",
                                        timeLabel = savedMessagesChat?.let {
                                            TimeUtils.formatChatListTime(it.lastMessageTime ?: it.updatedAt)
                                        } ?: "",
                                        onClick = openSavedMessages
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                }
                            }

                            items(filteredChats, key = { it.chatId }) { chat ->
                                val settings = chatSettingsMap[chat.chatId]
                                ChatListItem(
                                    chat = chat,
                                    displayName = resolveChatDisplayName(chat),
                                    isPinned = settings?.pinned == true,
                                    isMuted = settings?.isMuted == true,
                                    isOwnLastMessage = chat.lastMessageSenderId == currentUserId,
                                    onClick = { onNavigateToChat(chat.chatId) },
                                    onLongClick = { selectedChatForActions = chat }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewChatOptions) {
        ModalBottomSheet(onDismissRequest = { showNewChatOptions = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "محادثة جديدة",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                ActionItem(
                    icon = Icons.Default.Bookmark,
                    title = "المحفوظات",
                    subtitle = "فتح مساحتك الخاصة لحفظ الملاحظات والملفات",
                    onClick = {
                        showNewChatOptions = false
                        openSavedMessages()
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
                ActionItem(
                    icon = Icons.Default.Person,
                    title = "محادثة خاصة جديدة",
                    subtitle = "مراسلة مستخدم بواسطة المعرّف أو جهات الاتصال",
                    onClick = {
                        showNewChatOptions = false
                        onNavigateToNewDirectChat()
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
                ActionItem(
                    icon = Icons.Default.Group,
                    title = "مجموعة جديدة",
                    subtitle = "إنشاء مجموعة وإضافة الأعضاء والمشرفين",
                    onClick = {
                        showNewChatOptions = false
                        onNavigateToCreateGroup()
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
                ActionItem(
                    icon = Icons.Default.Campaign,
                    title = "قناة جديدة",
                    subtitle = "إنشاء قناة عامة أو خاصة لنشر الأخبار والمنشورات",
                    onClick = {
                        showNewChatOptions = false
                        onNavigateToCreateChannel()
                    }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (selectedChatForActions != null) {
        val chat = selectedChatForActions!!
        val settings = chatSettingsMap[chat.chatId]
        val isPinned = settings?.pinned == true
        val isMuted = settings?.isMuted == true
        val isArchived = settings?.isArchived == true

        ModalBottomSheet(onDismissRequest = { selectedChatForActions = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = resolveChatDisplayName(chat),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                ActionItem(
                    icon = Icons.Default.PushPin,
                    title = if (isPinned) "إلغاء التثبيت" else "تثبيت في الأعلى",
                    subtitle = if (isPinned) "إزالة المحادثة من قائمة المثبتة" else "تثبيت المحادثة في أعلى القائمة",
                    onClick = {
                        selectedChatForActions = null
                        coroutineScope.launch {
                            try {
                                chatRepository.updateChatSettings(chat.chatId, pinned = !isPinned)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                Toast.makeText(context, "تعذر تحديث المحادثة", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                ActionItem(
                    icon = if (isMuted) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                    title = if (isMuted) "إلغاء الكتم" else "كتم الإشعارات",
                    subtitle = if (isMuted) "تفعيل صوت الإشعارات لهذه المحادثة" else "إيقاف تنبيهات الرسائل الجديدة",
                    onClick = {
                        selectedChatForActions = null
                        coroutineScope.launch {
                            try {
                                chatRepository.updateChatSettings(chat.chatId, isMuted = !isMuted)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                Toast.makeText(context, "تعذر تحديث الإشعارات", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                ActionItem(
                    icon = if (isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                    title = if (isArchived) "إلغاء الأرشفة" else "أرشفة المحادثة",
                    subtitle = if (isArchived) "إعادة المحادثة إلى القائمة الرئيسية" else "نقل المحادثة إلى الأرشيف",
                    onClick = {
                        selectedChatForActions = null
                        coroutineScope.launch {
                            try {
                                chatRepository.updateChatSettings(chat.chatId, isArchived = !isArchived)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                Toast.makeText(context, "تعذر أرشفة المحادثة", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SavedMessagesHomeRow(
    lastSnippet: String,
    timeLabel: String,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("home_saved_messages_row")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(TelegramBlue, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = "المحفوظات",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "المحفوظات",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (timeLabel.isNotBlank()) {
                        Text(
                            text = timeLabel,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = lastSnippet,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatListItem(
    chat: Chat,
    displayName: String,
    isPinned: Boolean,
    isMuted: Boolean,
    isOwnLastMessage: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val bubbleColors = LocalChatBubbleColors.current

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("chat_item_${chat.chatId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarView(
                name = displayName,
                avatarUrl = chat.avatarUrl,
                size = 54.dp
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        if (chat.type == "channel") {
                            Icon(
                                imageVector = Icons.Default.Campaign,
                                contentDescription = "قناة",
                                tint = TelegramBlue,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(end = 4.dp)
                            )
                        } else if (chat.type == "group") {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = "مجموعة",
                                tint = TelegramBlue,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(end = 4.dp)
                            )
                        }
                        Text(
                            text = displayName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isOwnLastMessage) {
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = "مرسلة",
                                tint = bubbleColors.checkMarkRead,
                                modifier = Modifier
                                    .size(15.dp)
                                    .padding(end = 4.dp)
                            )
                        }
                        Text(
                            text = TimeUtils.formatChatListTime(chat.lastMessageTime ?: chat.updatedAt),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = chat.lastMessage.ifBlank { "لا توجد رسائل بعد" },
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isMuted) {
                            Icon(
                                imageVector = Icons.Default.NotificationsOff,
                                contentDescription = "مكتوم",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .size(15.dp)
                                    .padding(end = 4.dp)
                            )
                        }
                        if (isPinned) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "مثبتة",
                                tint = TelegramBlue,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = TelegramBlue,
            selectedLabelColor = Color.White
        ),
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
private fun ActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(TelegramBlue.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TelegramBlue,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
