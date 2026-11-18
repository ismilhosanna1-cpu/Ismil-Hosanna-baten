package com.example.yesmin.data

import com.example.yesmin.database.ConversationDao
import com.example.yesmin.database.ConversationEntity
import com.example.yesmin.database.MessageDao
import com.example.yesmin.database.MessageEntity
import kotlinx.coroutines.flow.Flow

class ConversationRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) {
    val conversations: Flow<List<ConversationEntity>> = conversationDao.observeConversations()

    fun observeMessages(conversationId: Long): Flow<List<MessageEntity>> =
        messageDao.observeMessages(conversationId)

    suspend fun createConversation(title: String = "New Conversation", languageCode: String = "en"): Long {
        return conversationDao.insertConversation(
            ConversationEntity(
                title = title,
                languageCode = languageCode
            )
        )
    }

    suspend fun ensureActiveConversation(currentId: Long?, languageCode: String): Long {
        if (currentId != null && conversationDao.getConversation(currentId) != null) {
            return currentId
        }
        return createConversation(title = "YESMIN Session", languageCode = languageCode)
    }

    suspend fun addMessage(
        conversationId: Long,
        role: String,
        content: String,
        modelUsed: String? = null,
        isVerifiedDeviceData: Boolean = false,
        actionSummary: String? = null
    ): Long {
        val id = messageDao.insertMessage(
            MessageEntity(
                conversationId = conversationId,
                role = role,
                content = content,
                modelUsed = modelUsed,
                isVerifiedDeviceData = isVerifiedDeviceData,
                actionSummary = actionSummary
            )
        )
        val conv = conversationDao.getConversation(conversationId)
        val updatedTitle = if (conv != null && (conv.title == "New Conversation" || conv.title == "YESMIN Session") && role == "user") {
            content.trim().take(42).ifBlank { conv.title }
        } else {
            conv?.title ?: "YESMIN Session"
        }
        conversationDao.updateConversationMeta(conversationId, updatedTitle, System.currentTimeMillis())
        return id
    }

    suspend fun updateMessageContent(messageId: Long, content: String) {
        messageDao.updateMessageContent(messageId, content)
    }

    suspend fun getContextWindow(conversationId: Long, limit: Int = 12): List<MessageEntity> {
        return messageDao.getRecentMessages(conversationId, limit)
            .filter { it.role == "user" || it.role == "model" }
            .reversed()
    }

    suspend fun deleteConversation(conversationId: Long) {
        conversationDao.deleteConversationById(conversationId)
    }

    suspend fun clearAllConversations() {
        conversationDao.deleteAllConversations()
    }
}
