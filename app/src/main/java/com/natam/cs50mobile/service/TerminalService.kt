package com.natam.cs50mobile.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.natam.cs50mobile.CS50App
import com.natam.cs50mobile.MainActivity
import com.natam.cs50mobile.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Serviço em primeiro plano. Não contém lógica de terminal: os processos vivem no AppContainer.
 * O papel dele é só avisar o Android que o app está fazendo trabalho de verdade, para que o
 * sistema não mate o processo (e o PRoot junto) quando a tela apagar ou o app sair de vista.
 * Ele se encerra sozinho quando não há mais nada rodando.
 */
class TerminalService : Service() {

    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = (application as CS50App).container

        if (intent?.action == ACTION_STOP) {
            container.stopAll()
            stopSelf()
            return START_NOT_STICKY
        }

        // O Android exige startForeground logo depois do startForegroundService.
        promote(buildNotification(getString(R.string.notif_starting)))

        if (job == null) {
            job = container.appScope.launch {
                container.keepAlive.collectLatest { reason ->
                    if (reason == null) {
                        // Pequena folga: a UI inicia o serviço um instante antes da sessão começar.
                        delay(IDLE_GRACE_MS)
                        releaseWakeLock()
                        stopSelf()
                    } else {
                        acquireWakeLock()
                        getSystemService(NotificationManager::class.java)
                            .notify(NOTIFICATION_ID, buildNotification(reason))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        job = null
        releaseWakeLock()
        super.onDestroy()
    }

    private fun promote(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        } catch (_: Exception) {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, TerminalService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.notif_stop), stop)
            .build()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "cs50mobile:terminal").apply {
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    companion object {
        private const val CHANNEL_ID = "terminal"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.natam.cs50mobile.STOP"
        private const val IDLE_GRACE_MS = 4_000L
        private const val WAKE_LOCK_TIMEOUT_MS = 6L * 60 * 60 * 1000

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, TerminalService::class.java))
        }
    }
}
