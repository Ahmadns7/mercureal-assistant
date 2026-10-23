package com.ahmadns7.mercureal.assistant.tile

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.ahmadns7.mercureal.assistant.ListenIntents
import com.ahmadns7.mercureal.assistant.R

/**
 * Quick settings tile "start listening". Tapping it opens the app and starts one listening turn
 * (voice activity detection ends it on silence). It does not keep the mic open in the background.
 */
class ListenTileService : TileService() {

    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(R.string.tile_label)
            updateTile()
        }
    }

    override fun onClick() {
        val intent = ListenIntents.launchIntent(this)
        if (Build.VERSION.SDK_INT >= 34) {
            val pi = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
