package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.BlockedUser
import com.example.data.model.Contact
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ContactRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeContacts(userId: String): Flow<List<Contact>> = flow {
        val col = db.collection("users").document(userId).collection("contacts")
        emitAll(
            col.snapshots()
                .map { snapshot -> snapshot.toObjects(Contact::class.java) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, col.path)
                    }
                    throw error
                }
        )
    }

    suspend fun addContact(contactUid: String, contactUsername: String, contactName: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("contacts").document(contactUid)
        val payload = mapOf(
            "contactUid" to contactUid,
            "contactUsername" to contactUsername,
            "contactName" to contactName,
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    fun observeBlockedUsers(userId: String): Flow<List<BlockedUser>> = flow {
        val col = db.collection("users").document(userId).collection("blockedUsers")
        emitAll(
            col.snapshots()
                .map { snapshot -> snapshot.toObjects(BlockedUser::class.java) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, col.path)
                    }
                    throw error
                }
        )
    }

    suspend fun blockUser(targetUid: String, username: String, name: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("blockedUsers").document(targetUid)
        val payload = mapOf(
            "blockedUid" to targetUid,
            "blockedUsername" to username,
            "blockedName" to name,
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun unblockUser(targetUid: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("blockedUsers").document(targetUid)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }
}
