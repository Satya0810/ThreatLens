package com.safeqr.scanner.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.safeqr.scanner.MainActivity

class ScannerTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile
        if (tile != null) {
            tile.state = Tile.STATE_INACTIVE
            tile.updateTile()
        }
    }

    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("open_scanner", true)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ specific launch logic for Tile Services if needed
            // The modern way to launch activities from TileService is via PendingIntent in A14,
            // but startActivityAndCollapse still works with the intent if configured correctly.
            val pendingIntent = android.app.PendingIntent.getActivity(
                this, 
                0, 
                intent, 
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            startActivityAndCollapse(intent)
        }
    }
}
