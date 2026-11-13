package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.example.data.repository.ChatRepository
import com.example.ui.theme.TelegramBlue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateChannelScreen(
    currentUserId: String,
    chatRepository: ChatRepository,
    onBack: () -> Unit,
    onChannelCreated: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var channelName by remember { mutableStateOf("") }
    var channelAbout by remember { mutableStateOf("") }
    var channelUsername by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(true) }
    var isCreating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text("قناة جديدة", fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(TelegramBlue, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Campaign,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                OutlinedTextField(
                    value = channelName,
                    onValueChange = { channelName = it },
                    label = { Text("اسم القناة") },
                    placeholder = { Text("أدخل اسم القناة...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("channel_name_input")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = channelAbout,
                onValueChange = { channelAbout = it },
                label = { Text("وصف القناة (اختياري)") },
                placeholder = { Text("ماذا تنشر في هذه القناة؟...") },
                shape = RoundedCornerShape(12.dp),
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "نوع القناة",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row {
                FilterChip(
                    selected = isPublic,
                    onClick = { isPublic = true },
                    label = { Text("قناة عامة (Public)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TelegramBlue,
                        selectedLabelColor = Color.White
                    ),
                    shape = RoundedCornerShape(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = !isPublic,
                    onClick = { isPublic = false },
                    label = { Text("قناة خاصة (Private)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TelegramBlue,
                        selectedLabelColor = Color.White
                    ),
                    shape = RoundedCornerShape(18.dp)
                )
            }

            if (isPublic) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = channelUsername,
                    onValueChange = { channelUsername = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' } },
                    label = { Text("رابط القناة الدائم (@معرف)") },
                    placeholder = { Text("my_channel") },
                    prefix = { Text("t.me/", color = TelegramBlue, fontWeight = FontWeight.Bold) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "يمكن لأي شخص العثور على القناة عبر البحث بواسطة هذا المعرف.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "القنوات الخاصة يمكن الانضمام إليها عبر رابط دعوة خاص فقط.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (channelName.isNotBlank() && !isCreating) {
                        isCreating = true
                        coroutineScope.launch {
                            try {
                                val newChannel = chatRepository.createChannel(
                                    name = channelName.trim(),
                                    username = channelUsername.trim(),
                                    about = channelAbout.trim(),
                                    avatarUrl = "",
                                    isPublic = isPublic
                                )
                                onChannelCreated(newChannel.chatId)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Toast.makeText(context, "تعذر إنشاء القناة", Toast.LENGTH_SHORT).show()
                            } finally {
                                isCreating = false
                            }
                        }
                    }
                },
                enabled = channelName.isNotBlank() && !isCreating,
                colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("create_channel_submit_button")
            ) {
                if (isCreating) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("إنشاء القناة", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}
