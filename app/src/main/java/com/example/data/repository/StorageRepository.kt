package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID

data class FileMetadata(
    val name: String,
    val sizeBytes: Long
)

class StorageRepository {
    private val storage by lazy { Firebase.storage }
    private val auth by lazy { Firebase.auth }

    private fun requireUid(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("يجب تسجيل الدخول أولاً قبل رفع الملفات.")
    }

    suspend fun uploadVoiceFile(chatId: String, file: File): String {
        val uid = requireUid()
        if (!file.exists() || file.length() <= 0L) {
            throw IllegalStateException("ملف التسجيل الصوتي غير موجود أو فارغ.")
        }
        val ref = storage.reference.child("voices/$chatId/${uid}_${UUID.randomUUID()}.m4a")
        return uploadUriInternal(ref, Uri.fromFile(file))
    }

    suspend fun uploadImageUri(chatId: String, uri: Uri): String {
        val uid = requireUid()
        val ref = storage.reference.child("images/$chatId/${uid}_${UUID.randomUUID()}.jpg")
        return uploadUriInternal(ref, uri)
    }

    suspend fun uploadVideoUri(chatId: String, uri: Uri): String {
        val uid = requireUid()
        val ref = storage.reference.child("videos/$chatId/${uid}_${UUID.randomUUID()}.mp4")
        return uploadUriInternal(ref, uri)
    }

    suspend fun uploadDocumentUri(context: Context, chatId: String, uri: Uri): Pair<String, FileMetadata> {
        val uid = requireUid()
        val meta = queryFileMetadata(context, uri)
        val safeFileName = meta.name.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80).ifBlank { "document" }
        val ref = storage.reference.child("documents/$chatId/${uid}_${UUID.randomUUID()}_$safeFileName")
        val url = uploadUriInternal(ref, uri)
        return Pair(url, meta)
    }

    suspend fun uploadUserAvatar(uri: Uri): String {
        val uid = requireUid()
        val ref = storage.reference.child("avatars/$uid/avatar_${UUID.randomUUID()}.jpg")
        return uploadUriInternal(ref, uri)
    }

    suspend fun uploadChatMedia(chatId: String, prefix: String, uri: Uri): String {
        requireUid()
        val ref = storage.reference.child("chatMedia/$chatId/${prefix}_${UUID.randomUUID()}.jpg")
        return uploadUriInternal(ref, uri)
    }

    suspend fun deleteByDownloadUrl(downloadUrl: String) {
        if (!downloadUrl.startsWith("https://")) return
        try {
            storage.getReferenceFromUrl(downloadUrl).delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Non-fatal cleanup
        }
    }

    private suspend fun uploadUriInternal(ref: com.google.firebase.storage.StorageReference, uri: Uri): String {
        try {
            ref.putFile(uri).await()
            return ref.downloadUrl.await().toString()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException("تعذر رفع الملف إلى Firebase Storage: ${e.localizedMessage ?: "تحقق من الاتصال وإعدادات Storage"}", e)
        }
    }

    fun queryFileMetadata(context: Context, uri: Uri): FileMetadata {
        var name = "ملف"
        var size = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        } catch (_: Exception) {
            // fallback below
        }
        if (name == "ملف") {
            name = uri.lastPathSegment?.substringAfterLast('/') ?: "ملف"
        }
        return FileMetadata(name = name, sizeBytes = size.coerceAtLeast(1L))
    }
}
