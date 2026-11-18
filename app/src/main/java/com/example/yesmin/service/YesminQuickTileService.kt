package com.example.yesmin.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.example.MainActivity
import com.example.yesmin.voice.WakeWordBus
import com.example.yesmin.widget.YesminHomeWidgetProvider

@RequiresApi(Build.VERSION_CODES.N)
class YesminQuickTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.let { tile ->
            tile.state = if (WakeWordBus.serviceRunning.value) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.subtitle = if (WakeWordBus.serviceRunning.value) "Always-On" else "Tap to speak"
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(
                YesminHomeWidgetProvider.EXTRA_WIDGET_ACTION,
                YesminHomeWidgetProvider.WIDGET_ACTION_VOICE
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                2001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
