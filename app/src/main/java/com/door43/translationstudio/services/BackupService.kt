package com.door43.translationstudio.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.ui.SettingsActivity
import com.door43.translationstudio.ui.home.HomeActivity
import com.door43.usecases.BackupRC
import com.door43.util.RepoUtils
import dagger.hilt.android.AndroidEntryPoint
import org.eclipse.jgit.api.errors.JGitInternalException
import org.unfoldingword.tools.foreground.Foreground
import org.unfoldingword.tools.logger.Logger
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject

/**
 * This services runs in the background to provide automatic backups for translations.
 * For now this service is backup the translations to two locations for added peace of mind.
 */
@AndroidEntryPoint
class BackupService : Service(), Foreground.Listener {
    @Inject lateinit var translator: Translator
    @Inject lateinit var backupRC: BackupRC
    @Inject lateinit var prefRepository: IPreferenceRepository

    private val sTimer = Timer()
    private var isPaused = false
    private var executingBackup = false
    private var foreground: Foreground? = null
    private lateinit var handler: Handler
    private var runner: Runnable? = null
    private lateinit var handlerThread: HandlerThread

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Logger.i(this.javaClass.name, "starting backup service")
        handlerThread = HandlerThread("BackupServiceHandler").apply {
            start()
            handler = Handler(looper)
        }

        try {
            foreground = Foreground.get().apply {
                addListener(this@BackupService)
            }
        } catch (e: IllegalStateException) {
            Logger.i(TAG, "Foreground was not initialized")
        }
    }

    override fun onDestroy() {
        foreground?.removeListener(this)
        stopService()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startid: Int): Int {
        val backupIntervalMinutes = prefRepository.getDefaultPref(
            SettingsActivity.KEY_PREF_BACKUP_INTERVAL,
            resources.getString(R.string.pref_default_backup_interval)
        ).toInt()

        if (backupIntervalMinutes > 0) {
            val backupInterval = backupIntervalMinutes * 1000 * 60
            Logger.i(this.javaClass.name, "Backups running every $backupIntervalMinutes minute/s")
            isRunning = true
            sTimer.schedule(object : TimerTask() {
                override fun run() {
                    try {
                        runBackup(isPaused)
                    } catch (e: Exception) {
                        // recover from exceptions
                        executingBackup = false
                        e.printStackTrace()
                    }
                }
            }, backupInterval.toLong(), backupInterval.toLong())
        } else {
            Logger.i(this.javaClass.name, "Backups are disabled")
            isRunning = true
        }
        return START_STICKY
    }

    /**
     * Stops the service
     */
    private fun stopService() {
        sTimer.cancel()
        isRunning = false
        Logger.i(TAG, "stopping backup service")
    }

    /**
     * Performs the backup if necessary
     */
    private fun runBackup(paused: Boolean) {
        if (paused || executingBackup) return

        executingBackup = true
        var backupPerformed = false

        Logger.i(TAG, "Checking for changes")

        val targetTranslations = translator.targetTranslationFileNames

        for (filename in targetTranslations) {
            try {
                // add delay to ease background processing and also slow the memory thrashing in background
                Thread.sleep(1000)
            } catch (e: Exception) {
                Logger.e(TAG, "sleep problem", e)
            }

            val t = translator.getTargetTranslation(filename)
            if (t == null) { // skip if not valid
                Logger.i(TAG, "Skipping invalid translation: $filename")
                continue
            }

            // commit pending changes
            try {
                t.commitSync(".", false)
            } catch (e: Exception) {
                if (e is JGitInternalException) {
                    Logger.w(TAG, "History corrupt in " + t.id + ". Repairing...", e)
                    RepoUtils.recover(t)
                } else {
                    Logger.w(TAG, "Could not commit changes to " + t.id, e)
                }
            }

            // run backup if there are translations
            if (t.numTranslated() > 0) {
                try {
                    val success = backupRC.backupTargetTranslation(t, false)
                    if (success) {
                        Logger.i(TAG, t.id + " backed up")
                        backupPerformed = true
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, "Could not backup " + t.id, e)
                }
            }
        }

        Logger.i(TAG, "Finished backup check.")

        if (backupPerformed) {
            onBackupComplete()
        }
        executingBackup = false
    }

    /**
     * Notifies the user that a backup was made
     */
    private fun onBackupComplete() {
        val noticeText: CharSequence = "Translations backed up"

        // activity to open when clicked
        // TODO: instead of the home activity we need a backup activity where the user can view their backups.
        val notificationIntent = Intent(applicationContext, HomeActivity::class.java)
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val intent = PendingIntent.getActivity(
            applicationContext,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        // build notification
        val channelId = "backup_notification"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // For Android 8.0 and above, create a notification channel
            val channelName = "Backup Notification"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val notificationChannel = NotificationChannel(channelId, channelName, importance)

            notificationManager.createNotificationChannel(notificationChannel)
        }

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_notify_msg)
            .setContentTitle(noticeText)
            .setAutoCancel(true)
            .setContentIntent(intent)
            .setNumber(0)

        // build big notification
        val inboxStyle = NotificationCompat.InboxStyle()
        inboxStyle.setBigContentTitle(noticeText)
        notificationBuilder.setStyle(inboxStyle)

        // issue notification
        notificationManager.notify(0, notificationBuilder.build())
    }

    override fun onBecameForeground() {
        this.isPaused = false
        Log.i(TAG, "backups resumed")
        runner?.let { handler.removeCallbacks(it) }
    }

    override fun onBecameBackground() {
        runner?.let { handler.removeCallbacks(it) }
        Log.i(TAG, "backups paused")
        isPaused = true
        handler.postDelayed(Runnable {
            Log.i(TAG, "performing single run before pause")
            runBackup(false)
        }.also { runner = it }, 1000)
    }

    companion object {
        val TAG: String = BackupService::class.java.name

        /**
         * Checks if the service is running
         * @return
         */
        var isRunning: Boolean = false
            private set
    }
}
