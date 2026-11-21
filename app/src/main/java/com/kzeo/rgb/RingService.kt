package com.kzeo.rgb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock

/**
 * Foreground service that keeps re-sending the chosen color so the ring stays lit.
 *
 * While a camera is in use (and "keep on in camera" is enabled) it works harder:
 * it resends the color every 60 ms and re-asserts the ring master switch, so the
 * system camera app cannot switch the ring off.
 */
class RingService : Service() {

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var cameraCallback: CameraManager.AvailabilityCallback? = null

    // Touched only from the service's handler thread.
    private val busyCameras = HashSet<String>()
    private var boostUntil = 0L
    private var lastAssert = 0L

    private val tick = object : Runnable {
        override fun run() {
            val ctx = this@RingService
            val now = SystemClock.elapsedRealtime()
            val boost = Prefs.cameraKeep(ctx) && (busyCameras.isNotEmpty() || now < boostUntil)
            cameraInUse = busyCameras.isNotEmpty()

            if (boost && now - lastAssert > 400) {
                lastAssert = now
                if (LightsBinder.masterValue(ctx) != 2) LightsBinder.setMaster(ctx, true)
            }
            LightsBinder.setColor(Prefs.color(ctx), packageName)
            handler?.postDelayed(this, if (boost) BOOST_INTERVAL_MS else INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        if (thread == null) {
            val t = HandlerThread("kzeo-ring")
            t.start()
            thread = t
            val h = Handler(t.looper)
            handler = h
            running = true
            registerCamera(h)
            h.post(tick)
        }
        return START_STICKY
    }

    private fun registerCamera(h: Handler) {
        try {
            val cm = getSystemService(CameraManager::class.java)
            val cb = object : CameraManager.AvailabilityCallback() {
                override fun onCameraUnavailable(cameraId: String) {
                    busyCameras.add(cameraId)
                    cameraInUse = true
                }

                override fun onCameraAvailable(cameraId: String) {
                    busyCameras.remove(cameraId)
                    if (busyCameras.isEmpty()) {
                        cameraInUse = false
                        // The camera app may reset the ring while closing; keep fighting for a moment.
                        boostUntil = SystemClock.elapsedRealtime() + 3000
                    }
                }
            }
            cm.registerAvailabilityCallback(cb, h)
            cameraCallback = cb
        } catch (t: Throwable) {
            // Camera state unavailable: the normal refresh loop still runs.
        }
    }

    override fun onDestroy() {
        try {
            val cb = cameraCallback
            if (cb != null) getSystemService(CameraManager::class.java).unregisterAvailabilityCallback(cb)
        } catch (t: Throwable) {
            // ignore
        }
        cameraCallback = null
        handler?.removeCallbacksAndMessages(null)
        thread?.quitSafely()
        thread = null
        handler = null
        running = false
        cameraInUse = false
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
            .setContentTitle("KZEO RGB")
            .setContentText("RGB ring is on")
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "kzeo_rgb_always_on"
        private const val NOTIF_ID = 1
        private const val INTERVAL_MS = 200L
        private const val BOOST_INTERVAL_MS = 60L

        /** True while the service loop is active. */
        @Volatile
        var running = false

        /** True while any camera is held open by an app. */
        @Volatile
        var cameraInUse = false
    }
}
