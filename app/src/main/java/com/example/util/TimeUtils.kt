package com.example.util

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TimeUtils {

    fun formatMessageTime(timestamp: Timestamp?, isArabic: Boolean = true): String {
        if (timestamp == null) return ""
        val locale = if (isArabic) Locale("ar") else Locale.getDefault()
        val format = SimpleDateFormat("h:mm a", locale)
        return format.format(timestamp.toDate())
    }

    fun formatChatListTime(timestamp: Timestamp?, isArabic: Boolean = true): String {
        if (timestamp == null) return ""
        val date = timestamp.toDate()
        val now = Calendar.getInstance()
        val msgCal = Calendar.getInstance().apply { time = date }

        val locale = if (isArabic) Locale("ar") else Locale.getDefault()

        return when {
            isSameDay(now, msgCal) -> {
                SimpleDateFormat("h:mm a", locale).format(date)
            }
            isYesterday(now, msgCal) -> {
                if (isArabic) "أمس" else "Yesterday"
            }
            isWithinAWeek(now, msgCal) -> {
                SimpleDateFormat("EEEE", locale).format(date)
            }
            else -> {
                SimpleDateFormat("dd/MM/yy", locale).format(date)
            }
        }
    }

    fun formatLastSeen(timestamp: Timestamp?, isOnline: Boolean, privacy: String = "everyone", isArabic: Boolean = true): String {
        if (privacy == "nobody") {
            return if (isArabic) "آخر ظهور مؤخراً" else "Last seen recently"
        }
        if (isOnline) {
            return if (isArabic) "متصل الآن" else "online"
        }
        if (timestamp == null) {
            return if (isArabic) "غير متصل" else "offline"
        }

        val date = timestamp.toDate()
        val diffMillis = System.currentTimeMillis() - date.time
        val diffMinutes = (diffMillis / (1000 * 60)).toInt()

        if (diffMinutes < 1) {
            return if (isArabic) "متصل منذ قليل" else "last seen just now"
        }
        if (diffMinutes < 60) {
            return if (isArabic) "آخر ظهور منذ $diffMinutes دقيقة" else "last seen $diffMinutes min ago"
        }

        val now = Calendar.getInstance()
        val lastSeenCal = Calendar.getInstance().apply { time = date }
        val locale = if (isArabic) Locale("ar") else Locale.getDefault()
        val timeStr = SimpleDateFormat("h:mm a", locale).format(date)

        return if (isSameDay(now, lastSeenCal)) {
            if (isArabic) "آخر ظهور اليوم $timeStr" else "last seen today at $timeStr"
        } else if (isYesterday(now, lastSeenCal)) {
            if (isArabic) "آخر ظهور أمس $timeStr" else "last seen yesterday at $timeStr"
        } else {
            val dateStr = SimpleDateFormat("dd MMMM", locale).format(date)
            if (isArabic) "آخر ظهور $dateStr" else "last seen on $dateStr"
        }
    }

    private fun isSameDay(c1: Calendar, c2: Calendar): Boolean {
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
                c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }

    private fun isYesterday(now: Calendar, then: Calendar): Boolean {
        val clone = now.clone() as Calendar
        clone.add(Calendar.DAY_OF_YEAR, -1)
        return isSameDay(clone, then)
    }

    private fun isWithinAWeek(now: Calendar, then: Calendar): Boolean {
        val diff = now.timeInMillis - then.timeInMillis
        return diff < 7 * 24 * 60 * 60 * 1000L
    }
}
