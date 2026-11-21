package com.kzeo.rgb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder

/** Foreground service that keeps re-sending the chosen color so the ring stays lit. */
class RingService : Service() {

    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    private val tick = object : Runnable {
        override fun run() {
            LightsBinder.setColor(Prefs.color(this@RingService), packageName)
            handler?.postDelayed(this, INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        if (thread == null) {
            val t = HandlerThread("valight-ring")
            t.start()
            thread = t
            val h = Handler(t.looper)
            handler = h
            h.post(tick)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler?.removeCallbacksAndMessages(null)
        thread?.quitSafely()
        thread = null
        handler = null
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            val ch = NotificationChannel(CHANNEL, "RGB always on", NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("VALight")
            .setContentText("RGB ring is on")
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "kzeo_rgb_always_on"
        private const val NOTIF_ID = 1
        private const val INTERVAL_MS = 200L
    }
}
