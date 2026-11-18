# YESMIN AI — Advanced Android AI Companion

**YESMIN AI** (`com.yesmin.ai`) is a Kotlin + Jetpack Compose Material 3 AI voice assistant and phone control companion built for Android smartphones (Android 8.0+ / API 26+, tested for compatibility with Android 11 devices such as Redmi Note 8).

---

## Core Features Implemented

1. **Multilingual Gemini AI Brain (English, Bengali, Hindi)**
   - Connects directly to Google's official Gemini API (`generativelanguage.googleapis.com/v1beta`) with SSE streaming (`streamGenerateContent?alt=sse`) and unary fallback.
   - Selectable supported models (`gemini-3.8-flash`, `gemini-3.5-flash-lite`, `gemini-4-argon`, or custom model ID).
   - Configurable personality tone, response length, temperature, conversation memory, and custom behaviour instructions.
   - Encrypted API key storage using **Android Keystore AES-256-GCM** (`SecureVault`). No hardcoded keys.

2. **Real Android Telecom & Call Management**
   - Implements `YesminInCallService` (`android.telecom.InCallService`) with `RoleManager.ROLE_DIALER` request flow.
   - Detects incoming/outgoing calls, resolves caller names via `ContactsContract.PhoneLookup`, speaks incoming caller announcements via TTS, and supports real answer, reject, end, mute, speakerphone, and live call duration.
   - Auto-answer is strictly opt-in with explicit warning.

3. **Voice Assistant, TTS & Foreground Wake-Word Service**
   - Uses `SpeechRecognizer` with live RMS audio waveform visualization and `TextToSpeech` with female-preferred voice selection for English (`en-US`), Bengali (`bn-BD`), and Hindi (`hi-IN`), plus instant voice interruption.
   - Compliant Foreground Service (`YesminVoiceService`, `foregroundServiceType="microphone"`) displaying a persistent microphone notification with a **Stop Listening** action.

4. **Phone Control, Contacts, Reminders & Calendar**
   - Launches installed apps (`YouTube`, `WhatsApp`, `Camera`, `Browser`, `Settings`, etc.) via `PackageManager` and Android Intents.
   - Controls hardware flashlight via `CameraManager.setTorchMode`, reads real battery telemetry, fetches live weather from Open-Meteo HTTPS API, opens SMS composer (`ACTION_SENDTO`) with confirmation, schedules reminders via `AlarmManager` + `WorkManager`, and integrates with `CalendarContract`.
   - Optional user-consented `NotificationListenerService` and `AccessibilityService` helpers.

5. **14 Futuristic Jetpack Compose Screens**
   - Splash, Main AI Dashboard, Voice Assistant, Text Chat, Phone Call Management, Contacts, Conversation History, Settings Dashboard, Gemini API Configuration, Language & Voice Settings, Permissions Dashboard, Wake Word Configuration, About & Privacy, and Reminders & Calendar.

---

## Architecture

- `com.example.yesmin.core`: `YesminApplication`, `AppContainer`
- `com.example.yesmin.ai`: `GeminiApiClient`, `IntentRouter`, `WeatherClient`
- `com.example.yesmin.telecom`: `YesminInCallService`, `CallStateHolder`, `PhoneActionExecutor`
- `com.example.yesmin.voice`: `YesminSpeechManager`, `YesminTtsManager`, `YesminVoiceService`
- `com.example.yesmin.database`: `YesminDatabase`, `Entities`, `Daos` (Room + KSP)
- `com.example.yesmin.data`: `SettingsRepository`, `ConversationRepository`, `ContactsRepository`, `ReminderRepository`
- `com.example.yesmin.security`: `SecureVault`, `PermissionHelper`
- `com.example.yesmin.service`: `ReminderReceiver`, `ReminderWorker`, `YesminNotificationListener`, `YesminAccessibilityService`
- `com.example.yesmin.ui`: Theme, Navigation Compose (`@Serializable` routes), ViewModel, and 14 Screens

---

## Known Android Platform Limitations

- **Default Dialer Requirement**: Android 10+ requires YESMIN AI to be granted the `Default Dialer` role before `InCallService` can answer, reject, mute, or switch speakerphone on active calls.
- **Restricted System Toggles**: Ordinary Android apps cannot silently toggle Wi-Fi, Bluetooth, or Mobile Data on Android 10+; YESMIN opens the official Android system settings screen instead.
- **MIUI / Redmi Note 8 Background Service**: On MIUI (Android 11), set Battery Saver to *No restrictions* for YESMIN AI if you enable continuous foreground wake-word listening.
- **No Silent SMS**: YESMIN opens the system SMS composer after user confirmation rather than silently sending SMS messages.

---

## Cloud APK Build (GitHub Actions)

Workflow file: `.github/workflows/android-apk.yml`

- Triggers on push to `main`/`master`, pull requests, or manual `workflow_dispatch`.
- Sets up JDK 17, Android SDK 36, Build Tools 36.0.0, and Gradle 9.3.1.
- Runs `gradle :app:testDebugUnitTest` and `gradle :app:assembleDebug`.
- Uploads `YESMIN-AI-debug-apk` as a downloadable artifact.
- Optionally builds and uploads `YESMIN-AI-release-apk` when GitHub Secrets `RELEASE_KEYSTORE_BASE64`, `STORE_PASSWORD`, and `KEY_PASSWORD` are configured.

---

## Termux থেকে GitHub ও Cloud APK বিল্ড নির্দেশিকা (বাংলায়)

আপনার ফোনে Android Studio ছাড়াই শুধু **Termux** ও **GitHub** ব্যবহার করে APK তৈরি ও ডাউনলোড করার ধাপসমূহ:

### ১. Termux-এ প্রয়োজনীয় টুল ইনস্টল করুন
```bash
pkg update && pkg upgrade -y
pkg install git gh unzip -y
```

### ২. Git ও GitHub লগইন সেটআপ করুন
```bash
git config --global user.name "Your Name"
git config --global user.email "your-email@example.com"
gh auth login
```
(ব্রাউজারে GitHub অ্যাকাউন্ট দিয়ে অনুমোদন দিন।)

### ৩. প্রোজেক্ট ফোল্ডারে গিয়ে GitHub রিপোজিটরি তৈরি ও আপলোড করুন
```bash
cd /path/to/yesmin-ai
git init
git add .
git commit -m "Initial YESMIN AI Android release"
gh repo create yesmin-ai --private --source=. --remote=origin --push
```

### ৪. Cloud APK বিল্ড চালু এবং স্ট্যাটাস চেক করুন
Push করার সাথে সাথেই GitHub Actions চালু হয়ে যাবে। স্ট্যাটাস দেখতে:
```bash
gh run list --limit 5
gh run watch
```
অথবা ম্যানুয়ালি ট্রিগার করতে:
```bash
gh workflow run android-apk.yml
```

### ৫. বিল্ড হওয়া Debug APK ডাউনলোড ও ইনস্টল করুন
বিল্ড `completed (success)` হলে Termux থেকেই APK ডাউনলোড করুন:
```bash
mkdir -p ~/storage/downloads/yesmin-apk
gh run download --name YESMIN-AI-debug-apk --dir ~/storage/downloads/yesmin-apk
```
*(প্রয়োজনে আগে `termux-setup-storage` চালিয়ে স্টোরেজ পারমিশন দিন।)*

এরপর আপনার ফোনের **File Manager -> Downloads -> yesmin-apk** ফোল্ডারে গিয়ে `.apk` ফাইলে ট্যাপ করে ইনস্টল করুন।

### ৬. রিলিজ APK (ঐচ্ছিক — GitHub Secrets সহ)
আপনি যদি নিজের Keystore দিয়ে Signed Release APK চান, তবে GitHub Repository -> **Settings -> Secrets and variables -> Actions**-এ নিচের ৩টি Secret যোগ করুন:
- `RELEASE_KEYSTORE_BASE64` (আপনার `.jks` ফাইলের base64 টেক্সট: `base64 -w 0 my-upload-key.jks`)
- `STORE_PASSWORD`
- `KEY_PASSWORD`

---

## Changelog
- **Stage 1–10 Complete**: Full modular architecture, custom adaptive launcher icon & avatar, Material 3 neon dark UI across 14 screens, encrypted Gemini API client with SSE streaming, multilingual NLU IntentRouter, Android Telecom `InCallService`, SpeechRecognizer + TTS + Foreground Wake-Word Service, Room Database + WorkManager + CalendarProvider, Robolectric tests, and GitHub Actions Cloud APK workflow.
