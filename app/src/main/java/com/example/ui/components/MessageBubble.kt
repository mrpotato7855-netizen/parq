package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Message
import com.example.ui.theme.LocalChatBubbleColors
import com.example.ui.theme.TelegramBlue
import com.example.util.TimeUtils

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    isOutgoing: Boolean,
    isChannel: Boolean = false,
    playingVoiceId: String? = null,
    voiceProgress: Float = 0f,
    onPlayVoice: (String, String) -> Unit = { _, _ -> },
    onReply: (Message) -> Unit = {},
    onEdit: (Message) -> Unit = {},
    onDelete: (Message) -> Unit = {},
    onPin: (Message) -> Unit = {},
    onForward: (Message) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val bubbleColors = LocalChatBubbleColors.current
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    val bubbleColor = if (isOutgoing) bubbleColors.outgoingBubble else bubbleColors.incomingBubble
    val textColor = if (isOutgoing) bubbleColors.outgoingText else bubbleColors.incomingText

    val bubbleShape = if (isOutgoing) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
    }

    val alignment = if (isOutgoing) Alignment.End else Alignment.Start

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalAlignment = alignment
    ) {
        Box {
            Surface(
                shape = bubbleShape,
                color = if (message.type == "sticker") Color.Transparent else bubbleColor,
                shadowElevation = if (message.type == "sticker") 0.dp else 1.dp,
                modifier = Modifier
                    .widthIn(min = 60.dp, max = 320.dp)
                    .combinedClickable(
                        onClick = {
                            if (message.type == "location") {
                                val uri = "geo:${message.latitude},${message.longitude}?q=${message.latitude},${message.longitude}(Location)"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                                context.startActivity(intent)
                            }
                        },
                        onLongClick = { showMenu = true }
                    )
                    .testTag("message_bubble_${message.messageId}")
            ) {
                Column(
                    modifier = Modifier.padding(
                        if (message.type == "sticker") 0.dp else 8.dp
                    )
                ) {
                    // Forwarded banner
                    if (message.forwardedFrom.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Reply,
                                contentDescription = "Forwarded",
                                tint = TelegramBlue,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "محولة من ${message.forwardedFrom}",
                                fontSize = 11.sp,
                                color = TelegramBlue,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Quoted reply banner
                    if (message.replyToMessageId.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(30.dp)
                                    .background(TelegramBlue, RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = message.replyToSenderName.ifBlank { "رسالة" },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TelegramBlue
                                )
                                Text(
                                    text = message.replyToText,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = textColor.copy(alpha = 0.7f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Sender name for groups/channels incoming messages
                    if (!isOutgoing && message.senderName.isNotBlank() && !isChannel) {
                        Text(
                            text = message.senderName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TelegramBlue,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }

                    // Content depending on message type
                    when (message.type) {
                        "text" -> {
                            Text(
                                text = message.content,
                                fontSize = 15.sp,
                                color = textColor,
                                lineHeight = 20.sp
                            )
                        }
                        "image" -> {
                            Column {
                                AsyncImage(
                                    model = message.mediaUrl,
                                    contentDescription = "Photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                if (message.content.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = message.content,
                                        fontSize = 14.sp,
                                        color = textColor
                                    )
                                }
                            }
                        }
                        "voice" -> {
                            val isPlaying = playingVoiceId == message.messageId
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                IconButton(
                                    onClick = { onPlayVoice(message.messageId, message.mediaUrl) },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(TelegramBlue, CircleShape)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play voice",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    LinearProgressIndicator(
                                        progress = { if (isPlaying) voiceProgress else 0f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = TelegramBlue,
                                        trackColor = Color.LightGray.copy(alpha = 0.5f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${message.durationSec}s",
                                        fontSize = 11.sp,
                                        color = textColor.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                        "document" -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(TelegramBlue, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.InsertDriveFile,
                                        contentDescription = "File",
                                        tint = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = message.fileName.ifBlank { "مستند" },
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = textColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val sizeKb = (message.fileSize / 1024).coerceAtLeast(1)
                                    Text(
                                        text = "$sizeKb KB",
                                        fontSize = 11.sp,
                                        color = textColor.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                        "location" -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color(0xFFE53935), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = "Location",
                                        tint = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = message.content.ifBlank { "موقع جغرافي" },
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = textColor
                                    )
                                    Text(
                                        text = "اضغط للمشاهدة على الخريطة",
                                        fontSize = 11.sp,
                                        color = TelegramBlue
                                    )
                                }
                            }
                        }
                        "sticker" -> {
                            Text(
                                text = message.content,
                                fontSize = 54.sp
                            )
                        }
                    }

                    // Metadata row: edited tag, views, timestamp, ticks
                    Row(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (message.isEdited) {
                            Text(
                                text = "معدلة",
                                fontSize = 10.sp,
                                color = textColor.copy(alpha = 0.5f),
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }

                        if (isChannel && message.viewsCount > 0) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = "Views",
                                tint = textColor.copy(alpha = 0.5f),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "${message.viewsCount}",
                                fontSize = 10.sp,
                                color = textColor.copy(alpha = 0.6f),
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }

                        Text(
                            text = TimeUtils.formatMessageTime(message.createdAt),
                            fontSize = 10.sp,
                            color = textColor.copy(alpha = 0.6f)
                        )

                        if (isOutgoing) {
                            Spacer(modifier = Modifier.width(3.dp))
                            if (message.status == "read") {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Read",
                                    tint = bubbleColors.checkMarkRead,
                                    modifier = Modifier.size(14.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Sent",
                                    tint = bubbleColors.checkMarkSent,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Long-press Context Dropdown Menu
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("رد") },
                    onClick = {
                        showMenu = false
                        onReply(message)
                    },
                    leadingIcon = { Icon(Icons.Default.Reply, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("تثبيت") },
                    onClick = {
                        showMenu = false
                        onPin(message)
                    },
                    leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) }
                )
                if (isOutgoing && message.type == "text") {
                    DropdownMenuItem(
                        text = { Text("تعديل") },
                        onClick = {
                            showMenu = false
                            onEdit(message)
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                    )
                }
                DropdownMenuItem(
                    text = { Text("حذف") },
                    onClick = {
                        showMenu = false
                        onDelete(message)
                    },
                    leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) }
                )
            }
        }
    }
}
