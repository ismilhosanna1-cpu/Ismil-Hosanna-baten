package com.example.yesmin.ai

import com.example.yesmin.data.AssistantSettings
import com.example.yesmin.data.ContactsRepository
import com.example.yesmin.data.PendingConfirmation
import com.example.yesmin.data.ReminderRepository
import com.example.yesmin.service.YesminNotificationListener
import com.example.yesmin.telecom.CallStateHolder
import com.example.yesmin.telecom.PhoneActionExecutor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed interface IntentOutcome {
    data object DelegateToGemini : IntentOutcome
    data class Handled(
        val responseText: String,
        val isVerifiedDeviceData: Boolean = true,
        val actionSummary: String? = null,
        val pendingConfirmation: PendingConfirmation? = null
    ) : IntentOutcome
}

class IntentRouter(
    private val phoneActionExecutor: PhoneActionExecutor,
    private val contactsRepository: ContactsRepository,
    private val reminderRepository: ReminderRepository,
    private val weatherClient: WeatherClient
) {
    suspend fun tryHandleCommand(rawInput: String, settings: AssistantSettings): IntentOutcome {
        val cleaned = stripWakeWord(rawInput, settings).trim()
        val lower = cleaned.lowercase(Locale.ROOT)
        if (lower.isBlank()) return IntentOutcome.DelegateToGemini

        // 1. Incoming / Active Call Management
        if (matchesAny(lower, "answer call", "answer the phone", "pick up", "receive call", "কল ধরো", "ফোন ধরো", "कॉल उठाओ", "फोन उठाओ")) {
            val (ok, msg) = CallStateHolder.answerIncomingCall()
            return IntentOutcome.Handled("[Verified Telecom] $msg", actionSummary = if (ok) "Answered call" else "Telecom check")
        }

        if (matchesAny(lower, "reject call", "reject the call", "decline call", "কল কাটো", "ফোন কাটো", "कॉल काटो")) {
            val (ok, msg) = CallStateHolder.rejectIncomingCall()
            return IntentOutcome.Handled("[Verified Telecom] $msg", actionSummary = if (ok) "Rejected call" else "Telecom check")
        }

        if (matchesAny(lower, "end call", "hang up", "disconnect call", "কল শেষ", "फोन काटो")) {
            val (ok, msg) = CallStateHolder.endActiveCall()
            return IntentOutcome.Handled("[Verified Telecom] $msg", actionSummary = if (ok) "Ended call" else "Telecom check")
        }

        if (lower == "mute" || lower.contains("mute call") || lower.contains("mute mic")) {
            val (_, msg) = CallStateHolder.toggleMute()
            return IntentOutcome.Handled("[Verified Telecom] $msg", actionSummary = "Call audio")
        }

        if (lower.contains("speaker") && (lower.contains("on") || lower.contains("off") || lower.contains("toggle"))) {
            val (_, msg) = CallStateHolder.toggleSpeakerphone()
            return IntentOutcome.Handled("[Verified Telecom] $msg", actionSummary = "Speakerphone")
        }

        // 2. Flashlight Control
        if (lower.contains("flashlight") || lower.contains("torch") || lower.contains("টর্চ") || lower.contains("फ्लैशलाइट")) {
            val turnOn = when {
                lower.contains("off") || lower.contains("বন্ধ") || lower.contains("बंद") -> false
                lower.contains("on") || lower.contains("চালু") || lower.contains("जलाओ") -> true
                else -> null
            }
            val result = phoneActionExecutor.toggleFlashlight(turnOn)
            return IntentOutcome.Handled("[Verified Hardware] ${result.message}", actionSummary = "Flashlight")
        }

        // 3. Battery Status
        if (lower.contains("battery") || lower.contains("ব্যাটারি") || lower.contains("बैटरी")) {
            val bat = phoneActionExecutor.getBatteryTelemetry()
            val chargingTxt = if (bat.isCharging) "charging" else "not charging"
            return IntentOutcome.Handled(
                "[Verified Device Telemetry] Battery is at ${bat.percentage}% ($chargingTxt), temperature ${bat.temperatureCelsius}°C.",
                actionSummary = "Battery telemetry"
            )
        }

        // 4. Read Notifications
        if (lower.contains("read notification") || lower.contains("my notifications") || lower.contains("নোটিফিকেশন") || lower.contains("नोटिफिकेशन")) {
            val recent = YesminNotificationListener.recentNotifications.value.take(5)
            return if (recent.isEmpty()) {
                IntentOutcome.Handled(
                    "[Verified Notification Access] No recent notifications captured. Ensure Notification Access is enabled in Permissions Dashboard.",
                    actionSummary = "Notifications"
                )
            } else {
                val summary = recent.joinToString("\n") { "• ${it.appName}: ${it.title} — ${it.text}" }
                IntentOutcome.Handled(
                    "[Verified Notification Access] Latest notifications:\n$summary",
                    actionSummary = "Read ${recent.size} notifications"
                )
            }
        }

        // 5. Alarm Creation
        if (lower.contains("set alarm") || lower.contains("set an alarm") || lower.contains("অ্যালার্ম") || lower.contains("अलार्म")) {
            val (hour, minute) = parseTimeFromText(lower) ?: (7 to 0)
            val feedback = phoneActionExecutor.setSystemAlarm(hour, minute, "YESMIN Alarm")
            return IntentOutcome.Handled("[Verified System] ${feedback.message}", actionSummary = "Set alarm")
        }

        // 6. Reminders
        if (lower.startsWith("remind me") || lower.contains("create reminder") || lower.contains("মনে করিয়ে") || lower.contains("याद दिलाना")) {
            val triggerMillis = computeReminderTimestamp(lower)
            val title = cleaned
                .replace(Regex("(?i)^(remind me( to| about)?|create reminder( to)?|tomorrow|in \\d+ minutes?)\\s*"), "")
                .trim()
                .ifBlank { cleaned }
            reminderRepository.createReminder(title = title, notes = "Created by YESMIN voice/chat", triggerAtMillis = triggerMillis)
            val fmt = SimpleDateFormat("EEE, MMM d 'at' hh:mm a", Locale.getDefault())
            return IntentOutcome.Handled(
                "[Verified Reminder Saved] I scheduled \"$title\" for ${fmt.format(Date(triggerMillis))}.",
                actionSummary = "Reminder scheduled"
            )
        }

        // 7. Weather (Live Open-Meteo HTTPS API)
        if (lower.contains("weather") || lower.contains("আবহাওয়া") || lower.contains("मौसम")) {
            val city = extractCityFromWeatherQuery(cleaned)
            val weatherResult = weatherClient.fetchWeather(city)
            return weatherResult.fold(
                onSuccess = { report ->
                    IntentOutcome.Handled(
                        "[Verified Weather Service — Open-Meteo]\nLocation: ${report.locationLabel}\nTemperature: ${report.temperatureC}°C\nCondition: ${report.conditionDescription}\nWind: ${report.windSpeedKmh} km/h",
                        actionSummary = "Live weather"
                    )
                },
                onFailure = { err ->
                    IntentOutcome.Handled(
                        "Unable to fetch live weather right now (${err.localizedMessage ?: "network error"}).",
                        isVerifiedDeviceData = false
                    )
                }
            )
        }

        // 8. Outgoing Phone Call (with Confirmation Gate unless disabled)
        val callMatch = Regex("(?i)^(?:call|dial|কল করো|ফোন করো|कॉल करो)\\s+(.+)$").find(cleaned)
        if (callMatch != null) {
            val targetRaw = callMatch.groupValues[1].trim()
            val digitsOnly = targetRaw.replace(Regex("[\\s\\-()]"), "")
            val contact = if (digitsOnly.matches(Regex("^\\+?\\d{4,15}$"))) {
                null
            } else {
                contactsRepository.findBestContactMatch(targetRaw)
            }
            val numberToCall = contact?.phoneNumber ?: if (digitsOnly.matches(Regex("^\\+?\\d{4,15}$"))) digitsOnly else null
            if (numberToCall == null) {
                return IntentOutcome.Handled(
                    "[Verified Contacts] I couldn't find \"$targetRaw\" in your contacts (or Contacts permission is not granted).",
                    actionSummary = "Contact lookup"
                )
            }
            val displayTarget = contact?.let { "${it.name} ($numberToCall)" } ?: numberToCall
            return if (settings.confirmBeforeCallOrSms) {
                IntentOutcome.Handled(
                    "I found $displayTarget. Please confirm before I place this call.",
                    actionSummary = "Awaiting call confirmation",
                    pendingConfirmation = PendingConfirmation(
                        title = "Confirm Phone Call",
                        description = "Place an outgoing call to $displayTarget?",
                        actionLabel = "Call Now",
                        onConfirm = { phoneActionExecutor.placePhoneCall(numberToCall) }
                    )
                )
            } else {
                val res = phoneActionExecutor.placePhoneCall(numberToCall)
                IntentOutcome.Handled("[Verified Telecom] ${res.message}", actionSummary = "Called $displayTarget")
            }
        }

        // 9. SMS Composition (Always opens composer / asks confirmation)
        val smsMatch = Regex("(?i)^(?:send sms|text|message|মেসেজ পাঠাও)\\s+(?:to\\s+)?(.+)$").find(cleaned)
        if (smsMatch != null) {
            val target = smsMatch.groupValues[1].trim()
            val contact = contactsRepository.findBestContactMatch(target)
            val number = contact?.phoneNumber ?: target.filter { it.isDigit() || it == '+' }
            if (number.length < 4) {
                return IntentOutcome.Handled(
                    "Tell me the contact name or number to message, e.g. \"message Rahim\".",
                    isVerifiedDeviceData = false
                )
            }
            val label = contact?.let { "${it.name} ($number)" } ?: number
            return IntentOutcome.Handled(
                "Ready to open SMS composer for $label. Confirm to proceed.",
                actionSummary = "Awaiting SMS confirmation",
                pendingConfirmation = PendingConfirmation(
                    title = "Open SMS Composer",
                    description = "Compose an SMS to $label? YESMIN never sends messages silently.",
                    actionLabel = "Open Messages",
                    onConfirm = { phoneActionExecutor.openSmsComposer(number) }
                )
            )
        }

        // 10. Open Apps / Camera / Browser / Settings / Navigation / Play Music / Search
        val openMatch = Regex("(?i)^(?:open|launch|খোলো|ओपन करो|खोलो)\\s+(.+)$").find(cleaned)
        if (openMatch != null) {
            val target = openMatch.groupValues[1].trim()
            val targetLower = target.lowercase()
            val feedback = when {
                targetLower.contains("camera") -> phoneActionExecutor.openCamera()
                targetLower.contains("browser") -> phoneActionExecutor.openBrowserOrSearch(null)
                targetLower.contains("setting") || targetLower.contains("wifi") || targetLower.contains("bluetooth") ->
                    phoneActionExecutor.openSystemSettings(targetLower)
                else -> phoneActionExecutor.launchInstalledAppByName(target)
            }
            return IntentOutcome.Handled("[Verified Launcher] ${feedback.message}", actionSummary = "Open $target")
        }

        val searchMatch = Regex("(?i)^(?:search google for|search google|google|সার্চ করো|गूगल सर्च)\\s+(.+)$").find(cleaned)
        if (searchMatch != null) {
            val q = searchMatch.groupValues[1].trim()
            val fb = phoneActionExecutor.openBrowserOrSearch(q)
            return IntentOutcome.Handled("[Verified Browser] ${fb.message}", actionSummary = "Web search")
        }

        val playMatch = Regex("(?i)^(?:play|প্লে করো)\\s+(.+)$").find(cleaned)
        if (playMatch != null) {
            val q = playMatch.groupValues[1].trim()
            val fb = phoneActionExecutor.playMusicQuery(q)
            return IntentOutcome.Handled("[Verified Media] ${fb.message}", actionSummary = "Play media")
        }

        val navMatch = Regex("(?i)^(?:navigate to|directions to)\\s+(.+)$").find(cleaned)
        if (navMatch != null) {
            val dest = navMatch.groupValues[1].trim()
            val fb = phoneActionExecutor.openNavigation(dest)
            return IntentOutcome.Handled("[Verified Maps] ${fb.message}", actionSummary = "Navigation")
        }

        // Otherwise let Gemini AI handle reasoning, coding, translation, summarization, Q&A!
        return IntentOutcome.DelegateToGemini
    }

    private fun stripWakeWord(input: String, settings: AssistantSettings): String {
        var result = input.trim()
        val prefixes = listOf(
            settings.primaryWakePhrase,
            settings.secondaryWakePhrase,
            "hey yesmin",
            "yesmin",
            "হেই ইয়েসমিন",
            "ইয়েসমিন",
            "हे येस्मिन",
            "येस्मिन"
        ).filter { it.isNotBlank() }

        for (p in prefixes) {
            if (result.lowercase().startsWith(p.lowercase())) {
                result = result.substring(p.length).trimStart(',', ' ', ':', '-', '!')
                break
            }
        }
        return result
    }

    private fun matchesAny(text: String, vararg phrases: String): Boolean =
        phrases.any { text.contains(it) }

    private fun parseTimeFromText(lower: String): Pair<Int, Int>? {
        val regex = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?")
        val match = regex.find(lower) ?: return null
        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val meridiem = match.groupValues[3]
        if (meridiem == "pm" && hour in 1..11) hour += 12
        if (meridiem == "am" && hour == 12) hour = 0
        return hour.coerceIn(0, 23) to minute.coerceIn(0, 59)
    }

    private fun computeReminderTimestamp(lower: String): Long {
        val cal = Calendar.getInstance()
        val minMatch = Regex("in\\s+(\\d+)\\s+min").find(lower)
        if (minMatch != null) {
            val mins = minMatch.groupValues[1].toIntOrNull() ?: 10
            cal.add(Calendar.MINUTE, mins)
            return cal.timeInMillis
        }
        if (lower.contains("tomorrow") || lower.contains("আগামীকাল") || lower.contains("कल")) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
            val parsed = parseTimeFromText(lower)
            cal.set(Calendar.HOUR_OF_DAY, parsed?.first ?: 9)
            cal.set(Calendar.MINUTE, parsed?.second ?: 0)
            cal.set(Calendar.SECOND, 0)
            return cal.timeInMillis
        }
        val parsed = parseTimeFromText(lower)
        if (parsed != null) {
            cal.set(Calendar.HOUR_OF_DAY, parsed.first)
            cal.set(Calendar.MINUTE, parsed.second)
            cal.set(Calendar.SECOND, 0)
            if (cal.timeInMillis <= System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            return cal.timeInMillis
        }
        cal.add(Calendar.HOUR_OF_DAY, 1)
        return cal.timeInMillis
    }

    private fun extractCityFromWeatherQuery(cleaned: String): String {
        val match = Regex("(?i)(?:in|at|for)\\s+([A-Za-z\\s]{2,30})").find(cleaned)
        return match?.groupValues?.get(1)?.trim() ?: "Dhaka"
    }
}
