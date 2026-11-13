package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.ShareLocation
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.Message
import com.example.ui.theme.LocalChatBubbleColors
import com.example.ui.theme.TelegramBlue
import com.example.util.LocationHelper
import com.example.util.TimeUtils
import java.util.Locale

private val URL_REGEX = Regex("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    isOutgoing: Boolean,
    isChannel: Boolean = false,
    canDeleteAnyMessage: Boolean = false,
    playingVoiceId: String? = null,
    voiceProgress: Float = 0f,
    onPlayVoice: (String, String) -> Unit = { _, _ -> },
    onReply: (Message) -> Unit = {},
    onEdit: (Message) -> Unit = {},
    onDelete: (Message) -> Unit = {},
    onPin: (Message) -> Unit = {},
    onForward: (Message) -> Unit = {},
    onStopLiveLocation: (Message) -> Unit = {},
    onSenderClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val bubbleColors = LocalChatBubbleColors.current
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showVideoPlayer by remember { mutableStateOf(false) }

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
                            when (message.type) {
                                "location", "live_location" -> {
                                    LocationHelper.openInMapsApp(
                                        context,
                                        message.latitude,
                                        message.longitude,
                                        message.content
                                    )
                                }
                                "video" -> {
                                    if (message.mediaUrl.isNotBlank()) {
                                        showVideoPlayer = true
                                    }
                                }
                                "document" -> {
                                    if (message.mediaUrl.isNotBlank()) {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(message.mediaUrl)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "لا يوجد تطبيق مناسب لفتح هذا الملف", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "رابط الملف غير متوفر", Toast.LENGTH_SHORT).show()
                                    }
                                }
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
                                imageVector = Icons.AutoMirrored.Filled.Forward,
                                contentDescription = "رسالة محولة",
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

                    // Sender name for incoming messages in groups
                    if (!isOutgoing && message.senderName.isNotBlank() && !isChannel) {
                        Text(
                            text = message.senderName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TelegramBlue,
                            modifier = Modifier
                                .clickable {
                                    if (message.senderId.isNotBlank()) {
                                        onSenderClick(message.senderId)
                                    }
                                }
                                .padding(bottom = 2.dp)
                        )
                    }

                    // Message content by type
                    when (message.type) {
                        "text" -> {
                            val linkAnnotated = remember(message.content) {
                                buildAnnotatedString {
                                    val matches = URL_REGEX.findAll(message.content)
                                    var lastIdx = 0
                                    for (match in matches) {
                                        if (match.range.first > lastIdx) {
                                            append(message.content.substring(lastIdx, match.range.first))
                                        }
                                        val url = match.value
                                        withLink(
                                            LinkAnnotation.Url(
                                                url = url,
                                                styles = TextLinkStyles(
                                                    style = SpanStyle(
                                                        color = TelegramBlue,
                                                        textDecoration = TextDecoration.Underline,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                )
                                            )
                                        ) {
                                            append(url)
                                        }
                                        lastIdx = match.range.last + 1
                                    }
                                    if (lastIdx < message.content.length) {
                                        append(message.content.substring(lastIdx))
                                    }
                                }
                            }
                            Text(
                                text = linkAnnotated,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 15.sp,
                                    color = textColor,
                                    lineHeight = 20.sp
                                )
                            )
                        }
                        "image" -> {
                            Column {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(message.mediaUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "صورة",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(190.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                                if (message.content.isNotBlank() && message.content != "صورة") {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = message.content,
                                        fontSize = 14.sp,
                                        color = textColor
                                    )
                                }
                            }
                        }
                        "video" -> {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(170.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF1A1F29)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "تشغيل الفيديو",
                                            tint = Color.White,
                                            modifier = Modifier.size(34.dp)
                                        )
                                    }
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.65f),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = "فيديو • اضغط للتشغيل",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                        "voice" -> {
                            val isPlaying = playingVoiceId == message.messageId
                            val mins = message.durationSec / 60
                            val secs = message.durationSec % 60
                            val formattedDur = String.format(Locale.US, "%02d:%02d", mins, secs)

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) {
                                IconButton(
                                    onClick = { onPlayVoice(message.messageId, message.mediaUrl) },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(TelegramBlue, CircleShape)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "تشغيل الرسالة الصوتية",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    LinearProgressIndicator(
                                        progress = { if (isPlaying) voiceProgress else 0f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = TelegramBlue,
                                        trackColor = Color.LightGray.copy(alpha = 0.5f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (isPlaying) "جاري التشغيل • $formattedDur" else formattedDur,
                                        fontSize = 11.sp,
                                        color = textColor.copy(alpha = 0.65f)
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
                                        .size(42.dp)
                                        .background(TelegramBlue, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.InsertDriveFile,
                                        contentDescription = "ملف",
                                        tint = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
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
                                        text = "$sizeKb كيلوبايت • اضغط للفتح",
                                        fontSize = 11.sp,
                                        color = textColor.copy(alpha = 0.65f)
                                    )
                                }
                            }
                        }
                        "location", "live_location" -> {
                            val isLive = message.type == "live_location" || message.isLiveLocation
                            val isStillLive = isLive && message.liveLocationActive &&
                                (message.liveLocationExpiresAt == 0L || message.liveLocationExpiresAt > System.currentTimeMillis())

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(LocationHelper.osmTileUrl(message.latitude, message.longitude))
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "خريطة الموقع",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(34.dp)
                                            .background(Color(0xFFE53935).copy(alpha = 0.25f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isLive) Icons.Default.ShareLocation else Icons.Default.LocationOn,
                                            contentDescription = "دبوس الموقع",
                                            tint = Color(0xFFE53935),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    if (isLive) {
                                        Surface(
                                            color = if (isStillLive) Color(0xFFE53935) else Color.DarkGray,
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(6.dp)
                                        ) {
                                            Text(
                                                text = if (isStillLive) "مباشر الآن" else "انتهت المشاركة",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = message.content.ifBlank { "موقع جغرافي" },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textColor
                                )
                                Text(
                                    text = String.format(Locale.US, "%.5f, %.5f • اضغط للفتح في الخرائط", message.latitude, message.longitude),
                                    fontSize = 11.sp,
                                    color = TelegramBlue
                                )
                                if (isOutgoing && isStillLive) {
                                    TextButton(
                                        onClick = { onStopLiveLocation(message) },
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.StopCircle,
                                            contentDescription = null,
                                            tint = Color(0xFFE53935),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("إيقاف المشاركة", color = Color(0xFFE53935), fontSize = 12.sp)
                                    }
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
                                contentDescription = "المشاهدات",
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
                                    contentDescription = "تمت القراءة",
                                    tint = bubbleColors.checkMarkRead,
                                    modifier = Modifier.size(14.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "تم الإرسال",
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
                    text = { Text("إعادة توجيه") },
                    onClick = {
                        showMenu = false
                        onForward(message)
                    },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Forward, contentDescription = null) }
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
                if (isOutgoing || canDeleteAnyMessage) {
                    DropdownMenuItem(
                        text = { Text("حذف", color = Color(0xFFE53935)) },
                        onClick = {
                            showMenu = false
                            onDelete(message)
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFE53935)) }
                    )
                }
            }
        }
    }

    if (showVideoPlayer && message.mediaUrl.isNotBlank()) {
        Dialog(
            onDismissRequest = { showVideoPlayer = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                color = Color.Black,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                val controller = MediaController(ctx)
                                controller.setAnchorView(this)
                                setMediaController(controller)
                                setVideoURI(Uri.parse(message.mediaUrl))
                                setOnPreparedListener { mp ->
                                    mp.start()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center)
                    )
                    IconButton(
                        onClick = { showVideoPlayer = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق الفيديو", tint = Color.White)
                    }
                }
            }
        }
    }
}
