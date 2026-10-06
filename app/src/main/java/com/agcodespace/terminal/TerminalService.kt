package com.agcodespace.terminal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.agcodespace.runtime.ProotRuntime
import com.termux.terminal.TerminalSession
import java.util.concurrent.Executors

class TerminalService : Service() {
    companion object {
        private const val TAG = "TerminalService"
        private const val CHANNEL_ID = "agcodespace_terminal"
        private const val NOTIFICATION_ID = 1001
    }

    inner class LocalBinder : Binder() {
        fun session(): TerminalSession? = session
        fun restart() { startOrRestartSession() }
    }

    private val binder = LocalBinder()
    @Volatile private var session: TerminalSession? = null
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        startOrRestartSession()
    }

    fun startOrRestartSession() {
        worker.execute {
            try {
                val runtime = ProotRuntime(applicationContext)
                if (!runtime.isInstalled()) {
                    Log.i(TAG, "Runtime not installed, preparing…")
                    val ok = runtime.prepare()
                    if (!ok) {
                        Log.e(TAG, "Failed to prepare Linux runtime for terminal session.")
                        return@execute
                    }
                }

                session?.finishIfRunning()

                val created = TerminalSession(
                    "/system/bin/sh",
                    runtime.root.absolutePath,
                    arrayOf(runtime.launchScript.absolutePath),
                    arrayOf(
                        "TERM=xterm-256color",
                        "COLORTERM=truecolor",
                        "LANG=C.UTF-8",
                        "LC_ALL=C.UTF-8",
                        "HOME=/home/agcodespace",
                        "USER=agcodespace"
                    ),
                    10000,
                    AgTerminalClient()
                )

                main.post {
                    session = created
                    Log.i(TAG, "Linux terminal session started successfully.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting terminal session", e)
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Linux Terminal",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active AGCodespace Linux terminal session"
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildForegroundNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("AGCodespace")
            .setContentText("Real Ubuntu Linux terminal session active")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        session?.finishIfRunning()
        session = null
        worker.shutdownNow()
        super.onDestroy()
    }
}
