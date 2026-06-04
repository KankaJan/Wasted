package com.nexttimeemail.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nexttimeemail.MainActivity
import com.nexttimeemail.R
import com.nexttimeemail.ui.meeting.MeetingEngine
import com.nexttimeemail.ui.meeting.MeetingPhase
import com.nexttimeemail.ui.meeting.MeetingUiState
import com.nexttimeemail.util.buzz
import com.nexttimeemail.util.formatElapsed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the meeting alive in the background and shows a
 * live, updating notification (cost · elapsed). It also owns the reminder buzz
 * while backgrounded, and the notification's Pause/Resume and End actions drive
 * [MeetingEngine] directly. The service stops itself when the meeting ends.
 */
class MeetingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collecting: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        // Mirror engine state into the notification; vibrate on reminder steps.
        collecting = scope.launch {
            launch {
                MeetingEngine.state.collect { state ->
                    if (state.phase == MeetingPhase.ENDED) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        startForeground(NOTIFICATION_ID, buildNotification(state))
                    }
                }
            }
            launch {
                MeetingEngine.buzz.collect { buzz(this@MeetingService) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_PAUSE -> MeetingEngine.togglePause()
            ACTION_END -> MeetingEngine.end()
        }
        val state = MeetingEngine.state.value
        if (state.phase == MeetingPhase.ENDED) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            // Must promote to foreground promptly after startForegroundService().
            startForeground(NOTIFICATION_ID, buildNotification(state))
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        collecting?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(state: MeetingUiState): Notification {
        val title = getString(
            if (state.running) R.string.meeting_in_progress else R.string.notif_title_paused,
        )
        val content = "${state.costSummary()} · ${formatElapsed(state.elapsedMillis)}"

        val toggleLabel = getString(if (state.running) R.string.pause else R.string.resume)
        val toggleIcon =
            if (state.running) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent())
            .addAction(toggleIcon, toggleLabel, servicePendingIntent(ACTION_TOGGLE_PAUSE, 1))
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.end_meeting),
                servicePendingIntent(ACTION_END, 2),
            )
            .build()
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(this, 0, intent, immutableFlags())
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, MeetingService::class.java).setAction(action)
        return PendingIntent.getService(this, requestCode, intent, immutableFlags())
    }

    private fun immutableFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notif_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "meeting_live"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_TOGGLE_PAUSE = "com.nexttimeemail.action.TOGGLE_PAUSE"
        const val ACTION_END = "com.nexttimeemail.action.END"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, MeetingService::class.java),
            )
        }
    }
}
