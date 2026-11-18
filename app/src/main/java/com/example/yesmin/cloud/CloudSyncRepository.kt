package com.example.yesmin.cloud

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CloudSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val allowedLanguages = setOf("en", "bn", "hi")
    private val validIdRegex = Regex("^[a-zA-Z0-9_\\-]{1,128}$")

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun validateId(id: String, label: String): String {
        val clean = id.trim()
        require(validIdRegex.matches(clean)) { "$label must match ^[a-zA-Z0-9_-]{1,128}$" }
        return clean
    }

    private fun sanitizeLanguageCode(code: String): String {
        val clean = code.trim().lowercase()
        return if (clean in allowedLanguages) clean else "en"
    }

    fun observeConversations(userId: String): Flow<List<CloudConversation>> = flow {
        if (userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$userId/conversations"
        emitAll(
            db.collection("users")
                .document(userId)
                .collection("conversations")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudConversation::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeMessages(userId: String, conversationId: String): Flow<List<CloudMessage>> {
        if (userId.isBlank() || !validIdRegex.matches(conversationId.trim())) {
            return flowOf(emptyList())
        }
        val cleanConvId = conversationId.trim()
        val path = "users/$userId/conversations/$cleanConvId/messages"
        return db.collection("users")
            .document(userId)
            .collection("conversations")
            .document(cleanConvId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    CloudMessage::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val toLog = firestoreError ?: (error as? Exception)
                if (toLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(toLog, OperationType.LIST, path)
                }
                throw error
            }
    }

    fun observeReminders(userId: String): Flow<List<CloudReminder>> = flow {
        if (userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$userId/reminders"
        emitAll(
            db.collection("users")
                .document(userId)
                .collection("reminders")
                .orderBy("triggerAtMillis", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudReminder::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureConversation(
        existingConversationId: String?,
        title: String,
        languageCode: String
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/conversations"
        return try {
            val uid = requireUserId()
            if (!existingConversationId.isNullOrBlank() && validIdRegex.matches(existingConversationId.trim())) {
                val cleanId = existingConversationId.trim()
                val docRef = db.collection("users").document(uid).collection("conversations").document(cleanId)
                docPath = docRef.path
                val snap = docRef.get().await()
                if (snap.exists()) {
                    return Result.success(cleanId)
                }
            }

            val convRef = db.collection("users").document(uid).collection("conversations").document()
            docPath = convRef.path
            val safeTitle = title.trim().ifBlank { "YESMIN Session" }.take(120)
            val safeLang = sanitizeLanguageCode(languageCode)
            val payload = mapOf(
                "title" to safeTitle,
                "languageCode" to safeLang,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            convRef.set(payload).await()
            Result.success(convRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addMessage(
        conversationId: String,
        role: String,
        content: String,
        modelUsed: String? = null,
        verifiedDeviceData: Boolean = false,
        actionSummary: String? = null,
        updateTitleIfDefault: Boolean = false
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/conversations/$conversationId/messages"
        return try {
            val uid = requireUserId()
            val cleanConvId = validateId(conversationId, "conversationId")
            val safeRole = role.trim().lowercase()
            require(safeRole == "user" || safeRole == "model") { "role must be 'user' or 'model'" }
            val safeContent = content.trim().take(16_000)
            require(safeContent.isNotEmpty()) { "content must not be blank" }

            val convRef = db.collection("users").document(uid).collection("conversations").document(cleanConvId)
            val msgRef = convRef.collection("messages").document()
            docPath = msgRef.path

            val cleanModelUsed = modelUsed?.trim()?.take(80)?.takeIf { it.isNotEmpty() }
            val cleanActionSummary = actionSummary?.trim()?.take(300)?.takeIf { it.isNotEmpty() }

            @Suppress("UNCHECKED_CAST")
            val payload = mapOf(
                "role" to safeRole,
                "content" to safeContent,
                "modelUsed" to cleanModelUsed,
                "verifiedDeviceData" to verifiedDeviceData,
                "actionSummary" to cleanActionSummary,
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            msgRef.set(payload).await()

            val convDoc = convRef.get().await()
            val currentTitle = convDoc.getString("title").orEmpty()
            val shouldRename = updateTitleIfDefault &&
                safeRole == "user" &&
                (currentTitle.isBlank() || currentTitle == "YESMIN Session" || currentTitle == "New Conversation")

            val convUpdate = if (shouldRename) {
                mapOf(
                    "title" to safeContent.take(42),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            } else {
                mapOf(
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            }
            convRef.update(convUpdate).await()
            Result.success(msgRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteConversation(conversationId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/conversations/$conversationId"
        return try {
            val uid = requireUserId()
            val cleanConvId = validateId(conversationId, "conversationId")
            val convRef = db.collection("users").document(uid).collection("conversations").document(cleanConvId)
            docPath = convRef.path

            val messagesPath = "$docPath/messages"
            val messagesSnapshot = try {
                convRef.collection("messages").get().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.LIST, messagesPath)
                throw e
            }

            messagesSnapshot.documents.forEach { msgDoc ->
                msgDoc.reference.delete().await()
            }
            convRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun clearAllConversations(): Result<Unit> {
        var listPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/conversations"
        return try {
            val uid = requireUserId()
            val convCollection = db.collection("users").document(uid).collection("conversations")
            listPath = convCollection.path
            val conversations = convCollection.get().await()
            conversations.documents.forEach { convDoc ->
                deleteConversation(convDoc.id).getOrThrow()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, listPath)
            Result.failure(e)
        }
    }

    suspend fun createReminder(
        title: String,
        notes: String,
        triggerAtMillis: Long,
        docId: String? = null
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/reminders"
        return try {
            val uid = requireUserId()
            val safeTitle = title.trim().take(200)
            require(safeTitle.isNotEmpty()) { "Reminder title must not be blank" }
            val safeNotes = notes.trim().take(1_000)
            require(triggerAtMillis > 0L) { "Reminder triggerAtMillis must be positive" }

            val remindersCol = db.collection("users").document(uid).collection("reminders")
            val remRef = if (!docId.isNullOrBlank()) {
                remindersCol.document(validateId(docId, "reminderId"))
            } else {
                remindersCol.document()
            }
            docPath = remRef.path

            val payload = mapOf(
                "title" to safeTitle,
                "notes" to safeNotes,
                "triggerAtMillis" to triggerAtMillis,
                "completed" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            remRef.set(payload).await()
            Result.success(remRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateReminderCompleted(reminderId: String, completed: Boolean): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/reminders/$reminderId"
        return try {
            val uid = requireUserId()
            val cleanId = validateId(reminderId, "reminderId")
            val remRef = db.collection("users").document(uid).collection("reminders").document(cleanId)
            docPath = remRef.path
            remRef.update(
                mapOf(
                    "completed" to completed,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteReminder(reminderId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/reminders/$reminderId"
        return try {
            val uid = requireUserId()
            val cleanId = validateId(reminderId, "reminderId")
            val remRef = db.collection("users").document(uid).collection("reminders").document(cleanId)
            docPath = remRef.path
            remRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun clearAllReminders(): Result<Unit> {
        var listPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/reminders"
        return try {
            val uid = requireUserId()
            val col = db.collection("users").document(uid).collection("reminders")
            listPath = col.path
            val snapshot = col.get().await()
            snapshot.documents.forEach { doc ->
                doc.reference.delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, listPath)
            Result.failure(e)
        }
    }
}
