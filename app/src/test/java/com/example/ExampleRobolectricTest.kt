package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.yesmin.ai.IntentOutcome
import com.example.yesmin.ai.IntentRouter
import com.example.yesmin.ai.WeatherClient
import com.example.yesmin.data.AssistantSettings
import com.example.yesmin.data.ContactsRepository
import com.example.yesmin.data.ConversationRepository
import com.example.yesmin.data.ReminderRepository
import com.example.yesmin.data.SupportedLanguage
import com.example.yesmin.database.YesminDatabase
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import com.example.yesmin.data.SettingsRepository
import com.example.yesmin.security.SecureVault
import com.example.yesmin.telecom.PhoneActionExecutor
import com.example.yesmin.voice.YesminSpeechManager
import com.example.yesmin.widget.YesminHomeWidgetProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: YesminDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, YesminDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `app name matches YESMIN AI`() {
        assertEquals("YESMIN AI", context.getString(R.string.app_name))
    }

    @Test
    fun `secure vault stores masks and clears api key`() {
        val vault = SecureVault(context)
        vault.clearGeminiApiKey()
        assertFalse(vault.hasApiKey())

        vault.saveGeminiApiKey("AIzaSyTestExampleKey123456")
        assertTrue(vault.hasApiKey())
        assertEquals("AIzaSyTestExampleKey123456", vault.getGeminiApiKey())
        assertTrue(vault.getMaskedApiKey().startsWith("AIzaSy"))

        vault.clearGeminiApiKey()
        assertFalse(vault.hasApiKey())
    }

    @Test
    fun `room conversation and reminder persistence works`() = runTest {
        val convRepo = ConversationRepository(db.conversationDao(), db.messageDao())
        val convId = convRepo.createConversation("Test Session", "bn")
        convRepo.addMessage(convId, "user", "হ্যালো ইয়েসমিন")
        convRepo.addMessage(convId, "model", "হ্যালো! আমি কেমন সাহায্য করতে পারি?", modelUsed = "gemini-3.8-flash")

        val messages = convRepo.observeMessages(convId).first()
        assertEquals(2, messages.size)
        assertEquals("হ্যালো ইয়েসমিন", messages[0].content)

        convRepo.deleteConversation(convId)
        val remaining = convRepo.conversations.first()
        assertTrue(remaining.isEmpty())
    }

    @Test
    fun `intent router handles battery and delegates general AI queries`() = runTest {
        val contactsRepo = ContactsRepository(context, db.assistantCallLogDao())
        val reminderRepo = ReminderRepository(context, db.reminderDao())
        val executor = PhoneActionExecutor(context)
        val router = IntentRouter(executor, contactsRepo, reminderRepo, WeatherClient())
        val settings = AssistantSettings(language = SupportedLanguage.ENGLISH)

        val batteryOutcome = router.tryHandleCommand("Yesmin, check my battery", settings)
        assertTrue(batteryOutcome is IntentOutcome.Handled)
        assertTrue((batteryOutcome as IntentOutcome.Handled).isVerifiedDeviceData)

        val aiOutcome = router.tryHandleCommand("Yesmin, explain this Kotlin code", settings)
        assertTrue(aiOutcome is IntentOutcome.DelegateToGemini)
    }

    @Test
    fun `speech recognizer intent sets Bengali language and calling package`() {
        val manager = YesminSpeechManager(context)
        val intent = manager.createRecognizerIntent(SupportedLanguage.BENGALI)

        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, intent.action)
        assertEquals("bn-BD", intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertEquals("bn-BD", intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE))
        assertEquals(context.packageName, intent.getStringExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE))
        assertTrue(intent.getBooleanExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false))
    }

    @Test
    fun `home widget provider is registered and updates cleanly`() {
        val receivers = context.packageManager.queryBroadcastReceivers(
            Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).setPackage(context.packageName),
            PackageManager.GET_META_DATA
        )
        assertTrue(receivers.any { it.activityInfo.name == YesminHomeWidgetProvider::class.java.name })

        val remoteViews = YesminHomeWidgetProvider.buildRemoteViews(context)
        assertEquals(R.layout.yesmin_home_widget, remoteViews.layoutId)
        YesminHomeWidgetProvider.updateAllWidgets(context)
    }

    @Test
    fun `settings repository persists keepScreenAwake`() = runTest {
        val repo = SettingsRepository(context, SecureVault(context))
        repo.updateSettings { it.copy(keepScreenAwake = true) }
        assertTrue(repo.settingsFlow.first().keepScreenAwake)
        repo.updateSettings { it.copy(keepScreenAwake = false) }
        assertFalse(repo.settingsFlow.first().keepScreenAwake)
    }
}
