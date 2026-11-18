package com.example.yesmin.core

import android.content.Context
import com.example.yesmin.ai.FirebaseAiService
import com.example.yesmin.ai.GeminiApiClient
import com.example.yesmin.ai.IntentRouter
import com.example.yesmin.ai.WeatherClient
import com.example.yesmin.data.ContactsRepository
import com.example.yesmin.data.ConversationRepository
import com.example.yesmin.data.ReminderRepository
import com.example.yesmin.data.SettingsRepository
import com.example.yesmin.database.YesminDatabase
import com.example.yesmin.security.SecureVault
import com.example.yesmin.telecom.PhoneActionExecutor
import com.example.yesmin.voice.YesminSpeechManager
import com.example.yesmin.voice.YesminTtsManager

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: YesminDatabase = YesminDatabase.getInstance(appContext)
    val secureVault: SecureVault = SecureVault(appContext)
    val settingsRepository: SettingsRepository = SettingsRepository(appContext, secureVault)
    val conversationRepository: ConversationRepository = ConversationRepository(
        database.conversationDao(),
        database.messageDao()
    )
    val contactsRepository: ContactsRepository = ContactsRepository(
        appContext,
        database.assistantCallLogDao()
    )
    val reminderRepository: ReminderRepository = ReminderRepository(
        appContext,
        database.reminderDao()
    )

    val geminiApiClient: GeminiApiClient = GeminiApiClient()
    val firebaseAiService: FirebaseAiService by lazy { FirebaseAiService() }
    val weatherClient: WeatherClient = WeatherClient()
    val phoneActionExecutor: PhoneActionExecutor = PhoneActionExecutor(appContext)
    val intentRouter: IntentRouter = IntentRouter(
        phoneActionExecutor = phoneActionExecutor,
        contactsRepository = contactsRepository,
        reminderRepository = reminderRepository,
        weatherClient = weatherClient
    )
    val ttsManager: YesminTtsManager = YesminTtsManager(appContext)
    val speechManager: YesminSpeechManager = YesminSpeechManager(appContext)
}
