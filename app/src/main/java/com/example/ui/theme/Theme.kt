package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class ChatBubbleColors(
    val outgoingBubble: Color,
    val incomingBubble: Color,
    val outgoingText: Color,
    val incomingText: Color,
    val checkMarkRead: Color,
    val checkMarkSent: Color
)

val LocalChatBubbleColors = staticCompositionLocalOf {
    ChatBubbleColors(
        outgoingBubble = TelegramLightBubbleOut,
        incomingBubble = TelegramLightBubbleIn,
        outgoingText = Color(0xFF000000),
        incomingText = Color(0xFF000000),
        checkMarkRead = TelegramGreenCheck,
        checkMarkSent = TelegramGrayCheck
    )
}

@Composable
fun BarqTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentColor: Color = TelegramBlue,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = accentColor,
            onPrimary = Color.White,
            primaryContainer = TelegramDarkSurfaceVariant,
            onPrimaryContainer = Color.White,
            secondary = TelegramLightBlue,
            background = TelegramDarkBg,
            surface = TelegramDarkSurface,
            onSurface = Color.White,
            surfaceVariant = TelegramDarkSurfaceVariant,
            onSurfaceVariant = Color(0xFFAAAAAA)
        )
    } else {
        lightColorScheme(
            primary = accentColor,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE3F2FD),
            onPrimaryContainer = Color(0xFF0D47A1),
            secondary = TelegramLightBlue,
            background = TelegramLightBg,
            surface = TelegramLightSurface,
            onSurface = Color(0xFF1F1F1F),
            surfaceVariant = TelegramLightSurfaceVariant,
            onSurfaceVariant = Color(0xFF707579)
        )
    }

    val bubbleColors = if (darkTheme) {
        ChatBubbleColors(
            outgoingBubble = TelegramDarkBubbleOut,
            incomingBubble = TelegramDarkBubbleIn,
            outgoingText = Color.White,
            incomingText = Color.White,
            checkMarkRead = TelegramGreenCheck,
            checkMarkSent = TelegramGrayCheck
        )
    } else {
        ChatBubbleColors(
            outgoingBubble = TelegramLightBubbleOut,
            incomingBubble = TelegramLightBubbleIn,
            outgoingText = Color(0xFF1A1A1A),
            incomingText = Color(0xFF1A1A1A),
            checkMarkRead = TelegramGreenCheck,
            checkMarkSent = TelegramGrayCheck
        )
    }

    CompositionLocalProvider(LocalChatBubbleColors provides bubbleColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
