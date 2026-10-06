package com.agcodespace.terminal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import com.agcodespace.runtime.ProotRuntime
import com.termux.terminal.TerminalSession
import java.util.concurrent.Executors

class TerminalService : Service() {
    inner class LocalBinder : Binder() { fun session(): TerminalSession? = session }
    private val binder = LocalBinder()
    @Volatile private var session: TerminalSession? = null
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel("terminal", "Linux terminal", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        startForeground(
            1001,
            Notification.Builder(this, "terminal")
                .setContentTitle("AGCodespace")
                .setContentText("Linux terminal session running")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .build()
        )
        worker.execute {
            val runtime = ProotRuntime(this)
            if (!runtime.prepare()) return@execute
            val created = TerminalSession(
                "/system/bin/sh", null, arrayOf("-c", runtime.interactiveShell()),
                arrayOf(
                    "TERM=xterm-256color",
                    "COLORTERM=truecolor",
                    "LANG=C.UTF-8",
                    "HOME=${runtime.home.absolutePath}",
                    "LC_ALL=C.UTF-8"
                ),
                10000,
                AgTerminalClient()
            )
            main.post { session = created }
        }
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
