package com.example.yesmin.ui.navigation

import kotlinx.serialization.Serializable

sealed interface YesminRoute {
    @Serializable data object Splash : YesminRoute
    @Serializable data object Dashboard : YesminRoute
    @Serializable data object VoiceAssistant : YesminRoute
    @Serializable data object TextChat : YesminRoute
    @Serializable data object PhoneCall : YesminRoute
    @Serializable data object Contacts : YesminRoute
    @Serializable data object History : YesminRoute
    @Serializable data object SettingsDashboard : YesminRoute
    @Serializable data object GeminiConfig : YesminRoute
    @Serializable data object LanguageVoice : YesminRoute
    @Serializable data object PermissionsDashboard : YesminRoute
    @Serializable data object WakeWordConfig : YesminRoute
    @Serializable data object AboutPrivacy : YesminRoute
    @Serializable data object RemindersCalendar : YesminRoute
}
