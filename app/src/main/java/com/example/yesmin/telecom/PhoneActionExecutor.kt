package com.example.yesmin.telecom

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import com.example.yesmin.security.PermissionHelper
import com.example.yesmin.service.YesminAccessibilityService
import java.net.URLEncoder

data class BatteryTelemetry(
    val percentage: Int,
    val isCharging: Boolean,
    val temperatureCelsius: Float
)

data class ExecutionFeedback(
    val success: Boolean,
    val message: String,
    val isVerifiedDeviceResult: Boolean = true
)

class PhoneActionExecutor(private val context: Context) {

    private var isTorchOn = false

    fun toggleFlashlight(turnOn: Boolean? = null): ExecutionFeedback {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                ?: return ExecutionFeedback(false, "Camera service unavailable on this device.")
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return ExecutionFeedback(false, "No camera flashlight hardware found on this device.")

            val targetState = turnOn ?: !isTorchOn
            cameraManager.setTorchMode(cameraId, targetState)
            isTorchOn = targetState
            ExecutionFeedback(
                true,
                if (targetState) "Flashlight turned ON." else "Flashlight turned OFF."
            )
        } catch (e: Exception) {
            ExecutionFeedback(false, "Unable to control flashlight: ${e.localizedMessage ?: "Hardware busy"}")
        }
    }

    fun getBatteryTelemetry(): BatteryTelemetry {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0

        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 0
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return BatteryTelemetry(
            percentage = pct,
            isCharging = charging,
            temperatureCelsius = tempTenths / 10f
        )
    }

    fun placePhoneCall(phoneNumber: String): ExecutionFeedback {
        val sanitized = phoneNumber.trim()
        if (sanitized.isEmpty()) {
            return ExecutionFeedback(false, "Phone number is empty.")
        }
        return try {
            val canDirectCall = PermissionHelper.hasPermission(context, Manifest.permission.CALL_PHONE)
            val action = if (canDirectCall) Intent.ACTION_CALL else Intent.ACTION_DIAL
            val intent = Intent(action, Uri.parse("tel:${Uri.encode(sanitized)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ExecutionFeedback(
                true,
                if (canDirectCall) "Initiating call to $sanitized via Android Telecom."
                else "Opened dialer with $sanitized (grant CALL_PHONE permission for direct calling)."
            )
        } catch (e: Exception) {
            ExecutionFeedback(false, "Failed to start phone call: ${e.localizedMessage}")
        }
    }

    fun openSmsComposer(phoneNumber: String, messageBody: String = ""): ExecutionFeedback {
        return try {
            val uri = Uri.parse("smsto:${Uri.encode(phoneNumber.trim())}")
            val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                if (messageBody.isNotBlank()) {
                    putExtra("sms_body", messageBody)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ExecutionFeedback(true, "Opened SMS composer for $phoneNumber for your final review.")
        } catch (e: Exception) {
            ExecutionFeedback(false, "No SMS app available: ${e.localizedMessage}")
        }
    }

    fun launchInstalledAppByName(appNameQuery: String): ExecutionFeedback {
        val query = appNameQuery.trim().lowercase()
        if (query.isBlank()) return ExecutionFeedback(false, "Please specify an app name.")

        val knownPackages = mapOf(
            "youtube" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "chrome" to "com.android.chrome",
            "maps" to "com.google.android.apps.maps",
            "gmail" to "com.google.android.gm",
            "spotify" to "com.spotify.music",
            "telegram" to "org.telegram.messenger"
        )

        val pm = context.packageManager
        knownPackages.entries.firstOrNull { query.contains(it.key) }?.let { (_, pkg) ->
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return ExecutionFeedback(true, "Launched $appNameQuery.")
            }
        }

        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        val matched = resolveInfos.firstOrNull { info ->
            val label = info.loadLabel(pm).toString().lowercase()
            label == query || label.contains(query)
        }

        return if (matched != null) {
            val pkg = matched.activityInfo.packageName
            val intent = pm.getLaunchIntentForPackage(pkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent != null) {
                context.startActivity(intent)
                ExecutionFeedback(true, "Launched ${matched.loadLabel(pm)}.")
            } else {
                ExecutionFeedback(false, "App ${matched.loadLabel(pm)} cannot be launched directly.")
            }
        } else {
            ExecutionFeedback(false, "Could not find an installed app matching \"$appNameQuery\".")
        }
    }

    fun openCamera(): ExecutionFeedback {
        return try {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ExecutionFeedback(true, "Opened device Camera.")
        } catch (e: Exception) {
            ExecutionFeedback(false, "Unable to open Camera: ${e.localizedMessage}")
        }
    }

    fun openSystemSettings(section: String = ""): ExecutionFeedback {
        val action = when {
            section.contains("wifi", true) || section.contains("wi-fi", true) -> Settings.ACTION_WIFI_SETTINGS
            section.contains("bluetooth", true) -> Settings.ACTION_BLUETOOTH_SETTINGS
            section.contains("display", true) || section.contains("brightness", true) -> Settings.ACTION_DISPLAY_SETTINGS
            section.contains("sound", true) || section.contains("volume", true) -> Settings.ACTION_SOUND_SETTINGS
            section.contains("battery", true) -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return try {
            val intent = Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
            ExecutionFeedback(true, "Opened Android system settings screen.")
        } catch (e: Exception) {
            ExecutionFeedback(false, "Unable to open system settings: ${e.localizedMessage}")
        }
    }

    fun openBrowserOrSearch(query: String? = null): ExecutionFeedback {
        return try {
            val url = if (query.isNullOrBlank()) {
                "https://www.google.com"
            } else {
                "https://www.google.com/search?q=${URLEncoder.encode(query.trim(), "UTF-8")}"
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ExecutionFeedback(
                true,
                if (query.isNullOrBlank()) "Opened web browser." else "Searching Google for \"$query\"."
            )
        } catch (e: Exception) {
            ExecutionFeedback(false, "Unable to open browser: ${e.localizedMessage}")
        }
    }

    fun openNavigation(destination: String): ExecutionFeedback {
        return try {
            val encoded = URLEncoder.encode(destination.trim(), "UTF-8")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ExecutionFeedback(true, "Opened navigation for \"$destination\".")
        } catch (e: Exception) {
            ExecutionFeedback(false, "No maps/navigation app found: ${e.localizedMessage}")
        }
    }

    fun playMusicQuery(query: String): ExecutionFeedback {
        return try {
            val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                putExtra("query", query.trim())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ExecutionFeedback(true, "Sent playback request for \"$query\" to your music player.")
        } catch (_: Exception) {
            openBrowserOrSearch("listen $query")
        }
    }

    fun setSystemAlarm(hour24: Int, minute: Int, label: String = "YESMIN Alarm"): ExecutionFeedback {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour24.coerceIn(0, 23))
                putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val formatted = String.format("%02d:%02d", hour24.coerceIn(0, 23), minute.coerceIn(0, 59))
            ExecutionFeedback(true, "Opened Clock to set alarm for $formatted ($label).")
        } catch (e: Exception) {
            ExecutionFeedback(false, "Unable to launch Clock alarm app: ${e.localizedMessage}")
        }
    }

    fun performAccessibilityGlobalAction(actionName: String): ExecutionFeedback {
        val service = YesminAccessibilityService.instance
            ?: return ExecutionFeedback(
                false,
                "Accessibility Helper is not enabled. Enable it in Permissions Dashboard if you want global Back/Home/Notifications gestures."
            )
        val ok = service.performGlobalHelperAction(actionName)
        return if (ok) {
            ExecutionFeedback(true, "Executed accessibility action: $actionName.")
        } else {
            ExecutionFeedback(false, "Accessibility action $actionName is unsupported on this Android version.")
        }
    }
}
