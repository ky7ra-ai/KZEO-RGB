package com.kzeo.rgb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the always-on service after a reboot when the user had it enabled. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        if (Prefs.ringOn(context) && Prefs.alwaysOn(context)) {
            try {
                context.startForegroundService(Intent(context, RingService::class.java))
            } catch (t: Throwable) {
                // Not allowed to start right now; the user can start it from the app.
            }
        }
    }
}
