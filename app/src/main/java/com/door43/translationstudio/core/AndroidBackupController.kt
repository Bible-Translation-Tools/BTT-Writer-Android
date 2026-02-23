package com.door43.translationstudio.core

import android.content.Context
import android.content.Intent
import com.door43.translationstudio.services.BackupService
import org.unfoldingword.tools.logger.Logger

class AndroidBackupController(private val context: Context) : BackupController {
    override fun restartServiceIfRunning() {
        if (BackupService.isRunning) {
            Logger.i("Settings", "Re-loading backup settings")
            
            val backupIntent = Intent(context, BackupService::class.java)
            context.stopService(backupIntent)
            context.startService(backupIntent)
        }
    }
}