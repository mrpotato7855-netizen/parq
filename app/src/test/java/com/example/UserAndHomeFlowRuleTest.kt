package com.example

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.AdminPermissions
import com.example.data.repository.ChatRepository
import com.example.data.repository.ContactRepository
import com.example.data.repository.MessageRepository
import com.example.data.repository.UserRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.robolectric.annotation.Config

@Config(sdk = [36])
class UserAndHomeFlowRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun getOrCreateProfile_existingUser_deserializesWithoutCrash() = runBlocking {
        val uid = signInTestUser("profile_roundtrip@example.com")
        val userRepo = UserRepository(firestore)

        val created = withTimeout(5000L) {
            userRepo.getOrCreateProfile("Alice Barq", "")
        }
        assertEquals(uid, created.userId)

        val fetched = withTimeout(5000L) {
            userRepo.getOrCreateProfile("Alice Barq", "")
        }
        assertEquals(uid, fetched.userId)
        assertEquals("Alice Barq", fetched.displayName)

        val observed = withTimeout(5000L) {
            userRepo.observeUserProfile(uid).first { it != null }
        }
        assertNotNull(observed)
        assertEquals(uid, observed?.userId)
    }

    @Test
    fun chatsSettingsContactsAndMessages_roundTripDeserialization_succeeds() = runBlocking {
        val uid = signInTestUser("chat_roundtrip@example.com")
        val chatRepo = ChatRepository(firestore)
        val msgRepo = MessageRepository(firestore)
        val contactRepo = ContactRepository(firestore)

        val group = withTimeout(5000L) {
            chatRepo.createGroup(
                name = "Roundtrip Group",
                about = "Testing deserialization",
                avatarUrl = "",
                memberUids = emptyList()
            )
        }
        withTimeout(5000L) {
            chatRepo.updateChatSettings(group.chatId, pinned = true, isMuted = false)
            contactRepo.addContact("friend_123", "friend123", "Friend User")
            msgRepo.sendMessage(
                chatId = group.chatId,
                type = "text",
                content = "مرحباً بكم في برق",
                senderName = "Alice"
            )
        }

        val observedChats = withTimeout(5000L) {
            chatRepo.observeUserChats(uid).first { list -> list.any { it.chatId == group.chatId } }
        }
        assertTrue(observedChats.any { it.chatId == group.chatId && it.name == "Roundtrip Group" })

        val observedSettings = withTimeout(5000L) {
            chatRepo.observeChatSettings(uid).first { it.containsKey(group.chatId) }
        }
        assertEquals(true, observedSettings[group.chatId]?.pinned)

        val observedContacts = withTimeout(5000L) {
            contactRepo.observeContacts(uid).first { it.isNotEmpty() }
        }
        assertTrue(observedContacts.any { it.contactUid == "friend_123" })

        val observedMessages = withTimeout(5000L) {
            msgRepo.observeMessages(group.chatId).first { it.isNotEmpty() }
        }
        assertTrue(observedMessages.any { it.content == "مرحباً بكم في برق" })
    }

    @Test
    fun createOrGetDirectChat_newDirectChatAndUserSearch_succeeds() = runBlocking {
        val uid = signInTestUser("direct_chat_creator@example.com")
        val userRepo = UserRepository(firestore)
        val chatRepo = ChatRepository(firestore)

        val me = withTimeout(5000L) {
            userRepo.getOrCreateProfile("Zaid Developer", "")
        }

        val byDisplayName = withTimeout(5000L) {
            userRepo.searchUsers("Zaid")
        }
        assertTrue(byDisplayName.any { it.userId == uid })

        val target = com.example.data.model.User(
            userId = "other_user_999",
            username = "other999",
            displayName = "Other Friend"
        )
        val directChat = withTimeout(5000L) {
            chatRepo.createOrGetDirectChat(target)
        }
        assertEquals("direct", directChat.type)
        assertTrue(directChat.participantUids.contains(uid))
        assertTrue(directChat.participantUids.contains("other_user_999"))

        val fetchedChat = withTimeout(5000L) {
            chatRepo.createOrGetDirectChat(target)
        }
        assertEquals(directChat.chatId, fetchedChat.chatId)

        val savedChat = withTimeout(5000L) {
            chatRepo.getOrCreateSavedMessagesChat()
        }
        assertEquals("المحفوظات", savedChat.name)
        assertTrue(savedChat.isSavedMessages)
    }

    @Test
    fun groupAndChannelPostCreationEditing_updatesSettingsMembersAndAdmins() = runBlocking {
        val ownerUid = signInTestUser("owner_edit@example.com")
        val chatRepo = ChatRepository(firestore)

        val group = withTimeout(5000L) {
            chatRepo.createGroup(
                name = "Initial Group",
                about = "Old description",
                avatarUrl = "",
                memberUids = emptyList()
            )
        }

        // Update settings post-creation
        withTimeout(5000L) {
            chatRepo.updateGroupOrChannelSettings(
                chatId = group.chatId,
                name = "Updated Group",
                about = "New description",
                avatarUrl = "https://example.com/avatar.jpg",
                coverUrl = "https://example.com/cover.jpg",
                isPublic = true,
                channelUsername = "updated_group",
                onlyAdminsCanPost = true,
                joinRequiresApproval = true
            )
            chatRepo.addMemberToChat(group.chatId, "member_abc")
            chatRepo.updateAdminAndPermissions(
                chatId = group.chatId,
                targetUserId = "member_abc",
                isAdmin = true,
                permissions = mapOf(
                    AdminPermissions.CAN_EDIT_INFO to false,
                    AdminPermissions.CAN_POST_MESSAGES to true,
                    AdminPermissions.CAN_DELETE_MESSAGES to true,
                    AdminPermissions.CAN_MANAGE_MEMBERS to false,
                    AdminPermissions.CAN_PIN_MESSAGES to true
                )
            )
        }

        val updated = withTimeout(5000L) {
            chatRepo.getChatById(group.chatId)
        }!!
        assertEquals("Updated Group", updated.name)
        assertEquals("New description", updated.about)
        assertEquals("https://example.com/cover.jpg", updated.coverUrl)
        assertTrue(updated.isPublic)
        assertTrue(updated.onlyAdminsCanPost)
        assertTrue(updated.joinRequiresApproval)
        assertTrue(updated.participantUids.contains("member_abc"))
        assertTrue(updated.adminUids.contains("member_abc"))
        assertTrue(updated.hasAdminPermission(ownerUid, AdminPermissions.CAN_EDIT_INFO))
        assertFalse(updated.hasAdminPermission("member_abc", AdminPermissions.CAN_EDIT_INFO))
        assertTrue(updated.hasAdminPermission("member_abc", AdminPermissions.CAN_POST_MESSAGES))

        // Remove admin/member
        withTimeout(5000L) {
            chatRepo.removeMemberFromChat(group.chatId, "member_abc")
        }
        val afterRemoval = withTimeout(5000L) {
            chatRepo.getChatById(group.chatId)
        }!!
        assertFalse(afterRemoval.participantUids.contains("member_abc"))
        assertFalse(afterRemoval.adminUids.contains("member_abc"))
    }

    @Test
    fun contactsAndPerUserCustomNickname_staysLocalToOwnerAccount() = runBlocking {
        // First create target user's real profile
        val friendUid = signInTestUser("ahmad_real@example.com")
        val userRepo = UserRepository(firestore)
        withTimeout(5000L) {
            userRepo.getOrCreateProfile("أحمد الحقيقي", "")
        }

        // Now sign in as owner and add friend with custom alias "أبو علي"
        val ownerUid = signInTestUser("salem_owner@example.com")
        val contactRepo = ContactRepository(firestore)

        withTimeout(5000L) {
            contactRepo.addContact(
                contactUid = friendUid,
                contactUsername = "ahmad_user",
                contactName = "أحمد الحقيقي"
            )
            contactRepo.updateContactCustomName(
                contactUid = friendUid,
                customName = "أبو علي",
                contactUsername = "ahmad_user",
                realDisplayName = "أحمد الحقيقي"
            )
        }

        val ownerContacts = withTimeout(5000L) {
            contactRepo.observeContacts(ownerUid).first { it.any { c -> c.contactUid == friendUid } }
        }
        val savedEntry = ownerContacts.first { it.contactUid == friendUid }
        assertEquals("أبو علي", savedEntry.customName)
        assertEquals("أبو علي", savedEntry.effectiveDisplayName())
        assertEquals("أحمد الحقيقي", savedEntry.realDisplayName)

        // Verify target user's own profile in Firestore is untouched
        val realFriendProfile = withTimeout(5000L) {
            userRepo.getUserById(friendUid)
        }
        assertEquals("أحمد الحقيقي", realFriendProfile?.displayName)

        // Remove contact works cleanly
        withTimeout(5000L) {
            contactRepo.removeContact(friendUid)
        }
        val afterDelete = withTimeout(5000L) {
            contactRepo.observeContacts(ownerUid).first { list -> list.none { it.contactUid == friendUid } }
        }
        assertTrue(afterDelete.none { it.contactUid == friendUid })
    }

    @Test
    fun savedMessages_privateToOwner_andForwardAndLiveLocationWork() = runBlocking {
        val ownerUid = signInTestUser("saved_owner@example.com")
        val chatRepo = ChatRepository(firestore)
        val msgRepo = MessageRepository(firestore)

        val savedChat = withTimeout(5000L) {
            chatRepo.getOrCreateSavedMessagesChat()
        }
        val liveMsg = withTimeout(5000L) {
            msgRepo.sendMessage(
                chatId = savedChat.chatId,
                type = "live_location",
                content = "موقع الاختبار",
                latitude = 33.3152,
                longitude = 44.3661,
                isLiveLocation = true,
                liveLocationActive = true,
                liveLocationExpiresAt = System.currentTimeMillis() + 600_000L,
                senderName = "Owner"
            )
        }
        withTimeout(5000L) {
            msgRepo.updateLiveLocation(
                chatId = savedChat.chatId,
                messageId = liveMsg.messageId,
                latitude = 33.3200,
                longitude = 44.3700,
                active = false,
                label = "موقع محدّث"
            )
        }

        val group = withTimeout(5000L) {
            chatRepo.createGroup("Forward Source", "", "", emptyList())
        }
        val origMsg = withTimeout(5000L) {
            msgRepo.sendMessage(chatId = group.chatId, type = "text", content = "رسالة مهمة للحفظ", senderName = "Owner")
        }
        withTimeout(5000L) {
            msgRepo.forwardMessage(
                source = origMsg,
                targetChatId = savedChat.chatId,
                senderName = "Owner",
                senderAvatar = "",
                forwardedFromLabel = "Forward Source"
            )
        }

        val savedMsgs = withTimeout(5000L) {
            msgRepo.observeMessages(savedChat.chatId).first { it.size >= 2 }
        }
        assertTrue(savedMsgs.any { it.content == "رسالة مهمة للحفظ" && it.forwardedFrom == "Forward Source" })
        assertTrue(savedMsgs.any { it.messageId == liveMsg.messageId && !it.liveLocationActive })

        // Switch to another user and verify Saved Messages are strictly denied by Firestore rules
        signInTestUser("intruder_user@example.com")
        try {
            withTimeout(5000L) {
                firestore.collection("chats").document("saved_$ownerUid").get().await()
            }
            fail("Expected PERMISSION_DENIED when reading another user's Saved Messages chat")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }

        try {
            withTimeout(5000L) {
                firestore.collection("chats").document("saved_$ownerUid")
                    .collection("messages").get().await()
            }
            fail("Expected PERMISSION_DENIED when reading another user's Saved Messages items")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }
    }
}
