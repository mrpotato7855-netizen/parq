package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Contact
import com.example.data.model.User
import com.example.data.repository.ChatRepository
import com.example.data.repository.ContactRepository
import com.example.data.repository.UserRepository
import com.example.ui.components.AvatarView
import com.example.ui.theme.TelegramBlue
import com.example.util.catchToFallback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    currentUserId: String,
    userRepository: UserRepository,
    contactRepository: ContactRepository,
    chatRepository: ChatRepository,
    onBack: () -> Unit,
    onNavigateToChat: (String) -> Unit,
    onNavigateToUserProfile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<User>>(emptyList()) }
    var discoverableUsers by remember { mutableStateOf<List<User>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var isOpeningChat by remember { mutableStateOf(false) }

    var editingContact by remember { mutableStateOf<Contact?>(null) }
    var customNameDraft by remember { mutableStateOf("") }

    val savedContacts by remember(currentUserId) {
        contactRepository.observeContacts(currentUserId).catchToFallback(emptyList())
    }.collectAsState(initial = emptyList())
    val contactUids = remember(savedContacts) { savedContacts.map { it.contactUid }.toSet() }

    LaunchedEffect(currentUserId) {
        try {
            val allUsers = userRepository.listUsers(60)
            discoverableUsers = allUsers.filter { it.userId != currentUserId }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            discoverableUsers = emptyList()
        }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.trim().isNotEmpty()) {
            isSearching = true
            searchResults = try {
                userRepository.searchUsers(searchQuery).filter { it.userId != currentUserId }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            isSearching = false
        } else {
            searchResults = emptyList()
            isSearching = false
        }
    }

    val filteredSavedContacts = remember(savedContacts, searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank()) savedContacts else {
            savedContacts.filter {
                it.effectiveDisplayName().contains(q, ignoreCase = true) ||
                    it.realDisplayName.contains(q, ignoreCase = true) ||
                    it.contactName.contains(q, ignoreCase = true) ||
                    it.contactUsername.contains(q, ignoreCase = true)
            }
        }
    }

    fun openChatWithUser(user: User) {
        if (isOpeningChat) return
        isOpeningChat = true
        coroutineScope.launch {
            try {
                val chat = chatRepository.createOrGetDirectChat(user)
                onNavigateToChat(chat.chatId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(context, "تعذر فتح المحادثة: ${e.localizedMessage ?: ""}", Toast.LENGTH_LONG).show()
            } finally {
                isOpeningChat = false
            }
        }
    }

    fun addUserToContacts(user: User) {
        coroutineScope.launch {
            try {
                contactRepository.addContact(
                    contactUid = user.userId,
                    contactUsername = user.username,
                    contactName = user.displayName.ifBlank { "@${user.username}" }
                )
                Toast.makeText(context, "تمت إضافة ${user.displayName} إلى جهات الاتصال", Toast.LENGTH_SHORT).show()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Toast.makeText(context, "تعذر الإضافة إلى جهات الاتصال", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("contacts_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                title = {
                    Text(
                        text = "جهات الاتصال",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
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
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("ابحث في جهات الاتصال أو عن مستخدم (@username)...", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TelegramBlue) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "مسح البحث")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("contacts_search_field"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    focusedBorderColor = TelegramBlue,
                    unfocusedBorderColor = Color.Transparent
                )
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (searchQuery.isBlank()) {
                    // Saved Messages ("المحفوظات") shortcut
                    item(key = "saved_messages_shortcut") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!isOpeningChat) {
                                        isOpeningChat = true
                                        coroutineScope.launch {
                                            try {
                                                val savedChat = chatRepository.getOrCreateSavedMessagesChat()
                                                onNavigateToChat(savedChat.chatId)
                                            } catch (e: CancellationException) {
                                                throw e
                                            } catch (_: Exception) {
                                                Toast.makeText(context, "تعذر فتح المحفوظات", Toast.LENGTH_SHORT).show()
                                            } finally {
                                                isOpeningChat = false
                                            }
                                        }
                                    }
                                }
                                .testTag("saved_messages_item")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Surface(
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    color = TelegramBlue,
                                    modifier = Modifier.padding(end = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Bookmark, contentDescription = null, tint = Color.White)
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("المحفوظات", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "مساحتك السحابية الخاصة لحفظ الرسائل والملفات",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "فتح المحفوظات", tint = TelegramBlue)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    }
                }

                // Saved Contacts Section (always filtered when searching)
                item(key = "saved_contacts_header") {
                    Text(
                        text = "جهات اتصالي (${filteredSavedContacts.size})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TelegramBlue,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }

                if (filteredSavedContacts.isEmpty()) {
                    item(key = "empty_contacts_hint") {
                        Text(
                            text = if (searchQuery.isBlank()) {
                                "لم تقم بإضافة أي شخص إلى جهات الاتصال بعد. افتح ملف أي مستخدم واضغط «إضافة إلى جهات الاتصال»."
                            } else {
                                "لا توجد نتائج مطابقة داخل جهات اتصالك."
                            },
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                } else {
                    items(filteredSavedContacts, key = { "contact_${it.contactUid}" }) { contact ->
                        val effectiveName = contact.effectiveDisplayName()
                        val matchedUser = discoverableUsers.firstOrNull { it.userId == contact.contactUid }
                        var showMenu by remember { mutableStateOf(false) }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    openChatWithUser(
                                        matchedUser ?: User(
                                            userId = contact.contactUid,
                                            username = contact.contactUsername,
                                            displayName = effectiveName
                                        )
                                    )
                                }
                                .testTag("saved_contact_row_${contact.contactUid}")
                        ) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    AvatarView(
                                        name = effectiveName,
                                        avatarUrl = matchedUser?.avatarUrl ?: "",
                                        size = 48.dp,
                                        isOnline = matchedUser?.isOnline == true,
                                        showOnlineDot = true
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = effectiveName,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        val realLabel = contact.realDisplayName.ifBlank { contact.contactName }
                                        Text(
                                            text = buildString {
                                                append("@${contact.contactUsername}")
                                                if (contact.customName.isNotBlank() && realLabel.isNotBlank() && contact.customName != realLabel) {
                                                    append(" • الاسم الحقيقي: $realLabel")
                                                }
                                            },
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            openChatWithUser(
                                                matchedUser ?: User(
                                                    userId = contact.contactUid,
                                                    username = contact.contactUsername,
                                                    displayName = effectiveName
                                                )
                                            )
                                        }
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "مراسلة", tint = TelegramBlue)
                                    }

                                    Box {
                                        IconButton(onClick = { showMenu = true }) {
                                            Icon(Icons.Default.MoreVert, contentDescription = "خيارات جهة الاتصال")
                                        }
                                        DropdownMenu(
                                            expanded = showMenu,
                                            onDismissRequest = { showMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("عرض الملف الشخصي") },
                                                onClick = {
                                                    showMenu = false
                                                    onNavigateToUserProfile(contact.contactUid)
                                                },
                                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("تغيير الاسم عندي فقط") },
                                                onClick = {
                                                    showMenu = false
                                                    customNameDraft = contact.customName
                                                    editingContact = contact
                                                },
                                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("حذف من جهات الاتصال", color = Color(0xFFE53935)) },
                                                onClick = {
                                                    showMenu = false
                                                    coroutineScope.launch {
                                                        try {
                                                            contactRepository.removeContact(contact.contactUid)
                                                            Toast.makeText(context, "تم الحذف من جهات الاتصال", Toast.LENGTH_SHORT).show()
                                                        } catch (e: CancellationException) {
                                                            throw e
                                                        } catch (_: Exception) {
                                                            Toast.makeText(context, "تعذر الحذف", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                },
                                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFE53935)) }
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                            }
                        }
                    }
                }

                // Global User Search Results OR Discover Users on Barq
                if (isSearching) {
                    item(key = "searching_loader") {
                        Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = TelegramBlue)
                        }
                    }
                } else {
                    val remoteList = if (searchQuery.isNotBlank()) searchResults else discoverableUsers
                    item(key = "discover_users_header") {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) {
                                "نتائج البحث في بَرْق (${remoteList.size})"
                            } else {
                                "مستخدمو بَرْق (${remoteList.size})"
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }

                    if (remoteList.isEmpty()) {
                        item(key = "no_users_found") {
                            Text(
                                text = if (searchQuery.isNotBlank()) {
                                    "لا يوجد مستخدم مطابق لـ \"$searchQuery\""
                                } else {
                                    "لا يوجد مستخدمون آخرون متاحون حالياً."
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    } else {
                        items(remoteList, key = { "user_${it.userId}" }) { user ->
                            val alreadyContact = contactUids.contains(user.userId)
                            UserItemRow(
                                user = user,
                                isAlreadyContact = alreadyContact,
                                onStartChat = { openChatWithUser(user) },
                                onOpenProfile = { onNavigateToUserProfile(user.userId) },
                                onAddContact = { addUserToContacts(user) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Rename Friend Alias Locally Dialog
    editingContact?.let { target ->
        val realLabel = target.realDisplayName.ifBlank { target.contactName }
        AlertDialog(
            onDismissRequest = { editingContact = null },
            title = { Text("تغيير اسم الصديق عندي") },
            text = {
                Column {
                    Text(
                        text = "سيظهر هذا الاسم لك فقط في جهات الاتصال وقائمة المحادثات دون تغيير اسم المستخدم أو اسمه الحقيقي ($realLabel).",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = customNameDraft,
                        onValueChange = { customNameDraft = it },
                        label = { Text("الاسم المخصص (اتركه فارغاً للاسترجاع)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val contactToSave = target
                        editingContact = null
                        coroutineScope.launch {
                            try {
                                contactRepository.updateContactCustomName(
                                    contactUid = contactToSave.contactUid,
                                    customName = customNameDraft,
                                    contactUsername = contactToSave.contactUsername,
                                    realDisplayName = realLabel
                                )
                                Toast.makeText(context, "تم تحديث الاسم", Toast.LENGTH_SHORT).show()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                Toast.makeText(context, "تعذر تحديث الاسم", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("حفظ", color = TelegramBlue, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingContact = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
private fun UserItemRow(
    user: User,
    isAlreadyContact: Boolean,
    onStartChat: () -> Unit,
    onOpenProfile: () -> Unit,
    onAddContact: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenProfile)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            AvatarView(
                name = user.displayName,
                avatarUrl = user.avatarUrl,
                size = 48.dp,
                isOnline = user.isOnline,
                showOnlineDot = true
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.displayName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "@${user.username}",
                    fontSize = 13.sp,
                    color = TelegramBlue
                )
                if (user.bio.isNotBlank()) {
                    Text(
                        text = user.bio,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            if (!isAlreadyContact) {
                IconButton(onClick = onAddContact) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = "إضافة إلى جهات الاتصال",
                        tint = TelegramBlue
                    )
                }
            }
            IconButton(onClick = onStartChat) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = "مراسلة",
                    tint = TelegramBlue
                )
            }
        }
    }
}
