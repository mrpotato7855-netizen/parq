package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.User
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.UUID

class ChatRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createGroup_authenticatedUser_success() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = ChatRepository(firestore)

        val chat = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createGroup(
                name = "Test Group ${UUID.randomUUID()}",
                about = "Testing groups",
                avatarUrl = "",
                memberUids = listOf("bob_${UUID.randomUUID()}")
            )
        }

        assertNotNull(chat.chatId)
        assertTrue(chat.chatId.startsWith("group_"))
        assertEquals("group", chat.type)
        assertEquals(aliceUid, chat.ownerId)
    }

    @Test
    fun observeUserChats_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = ChatRepository(firestore)

        val exception = assertThrows(Exception::class.java) {
            runBlocking {
                withTimeout(FLOW_TIMEOUT_MS) {
                    repo.observeUserChats("non_existent_user").first()
                }
            }
        }
        val firestoreException = checkNotNull(exception.unwrapFirestoreException()) {
            "Expected nested FirebaseFirestoreException, got $exception"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_test@example.com"
        const val DEFAULT_TIMEOUT_MS = 6000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
