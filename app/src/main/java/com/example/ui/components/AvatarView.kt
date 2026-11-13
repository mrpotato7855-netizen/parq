package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.AvatarGradients
import com.example.ui.theme.TelegramGreenCheck

@Composable
fun AvatarView(
    name: String,
    avatarUrl: String = "",
    size: Dp = 48.dp,
    isOnline: Boolean = false,
    showOnlineDot: Boolean = false,
    modifier: Modifier = Modifier
) {
    val cleanName = name.ifBlank { "Barq" }
    val initial = cleanName.first().uppercase()

    val gradientIndex = (cleanName.hashCode() and Int.MAX_VALUE) % AvatarGradients.size
    val gradient = AvatarGradients[gradientIndex]

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (avatarUrl.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(avatarUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = cleanName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            listOf(gradient.first, gradient.second)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    color = Color.White,
                    fontSize = (size.value * 0.42f).sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (showOnlineDot && isOnline) {
            val dotSize = (size.value * 0.28f).coerceIn(8f, 16f).dp
            val borderSize = (size.value * 0.05f).coerceIn(1.5f, 2.5f).dp
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .align(Alignment.BottomEnd)
                    .border(borderSize, MaterialTheme.colorScheme.surface, CircleShape)
                    .clip(CircleShape)
                    .background(TelegramGreenCheck)
            )
        }
    }
}
