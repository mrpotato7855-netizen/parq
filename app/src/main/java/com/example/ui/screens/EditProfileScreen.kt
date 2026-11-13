package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.StorageRepository
import com.example.data.repository.UserRepository
import com.example.ui.components.AvatarView
import com.example.ui.theme.TelegramBlue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    currentUserId: String,
    userRepository: UserRepository,
    storageRepository: StorageRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var displayName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var avatarUrl by remember { mutableStateOf("") }
    var pendingLocalAvatarUri by remember { mutableStateOf<Uri?>(null) }
    var lastSeenPrivacy by remember { mutableStateOf("everyone") }
    var avatarPrivacy by remember { mutableStateOf("everyone") }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingLocalAvatarUri = uri
            avatarUrl = uri.toString()
        }
    }

    LaunchedEffect(currentUserId) {
        try {
            val profile = userRepository.observeUserProfile(currentUserId).first()
                ?: userRepository.getOrCreateProfile("User")
            displayName = profile.displayName
            username = profile.username
            bio = profile.bio
            avatarUrl = profile.avatarUrl
            lastSeenPrivacy = profile.lastSeenPrivacy
            avatarPrivacy = profile.avatarPrivacy
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Toast.makeText(context, "تعذر تحميل الملف الشخصي", Toast.LENGTH_SHORT).show()
        } finally {
            isLoading = false
        }
    }

    val saveProfile: () -> Unit = {
        if (!isSaving && displayName.isNotBlank() && username.trim().length >= 3) {
            isSaving = true
            coroutineScope.launch {
                try {
                    val finalAvatarUrl = if (pendingLocalAvatarUri != null) {
                        storageRepository.uploadUserAvatar(pendingLocalAvatarUri!!)
                    } else {
                        avatarUrl
                    }
                    userRepository.updateUserProfile(
                        displayName = displayName.trim(),
                        username = username.trim(),
                        bio = bio.trim(),
                        avatarUrl = finalAvatarUrl,
                        lastSeenPrivacy = lastSeenPrivacy,
                        avatarPrivacy = avatarPrivacy
                    )
                    onBack()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Toast.makeText(context, "تعذر حفظ التغييرات: ${e.localizedMessage ?: ""}", Toast.LENGTH_SHORT).show()
                } finally {
                    isSaving = false
                }
            }
        } else if (username.trim().length < 3) {
            Toast.makeText(context, "يجب أن يكون اسم المستخدم 3 أحرف على الأقل", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                title = { Text("تعديل الملف الشخصي", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(
                        onClick = saveProfile,
                        enabled = !isSaving && displayName.isNotBlank() && username.isNotBlank(),
                        modifier = Modifier.testTag("save_profile_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = TelegramBlue)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = "حفظ", tint = TelegramBlue)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        modifier = modifier
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TelegramBlue)
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .clickable {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AvatarView(
                        name = displayName.ifBlank { "User" },
                        avatarUrl = avatarUrl,
                        size = 90.dp
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "تغيير الصورة", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("الاسم") },
                    placeholder = { Text("الاسم الظاهر للآخرين") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("edit_display_name_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' } },
                    label = { Text("اسم المستخدم (Username)") },
                    placeholder = { Text("username") },
                    prefix = { Text("@", color = TelegramBlue, fontWeight = FontWeight.Bold) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("edit_username_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("نبذة شخصية (Bio)") },
                    placeholder = { Text("اكتب بضع كلمات عنك...") },
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("edit_bio_input")
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = saveProfile,
                    colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text("حفظ التغييرات", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
