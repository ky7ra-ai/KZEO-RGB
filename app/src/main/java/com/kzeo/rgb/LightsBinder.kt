package com.kzeo.rgb

import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Parcel
import android.provider.Settings
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * Talks to the HyperOS ring-light service through Shizuku.
 *
 * Protocol (taken from the working HaloLight build):
 *   service    : miui.lights.ILightsManager
 *   transaction: 2
 *   parcel     : interface token, int ARGB color, String package, int 3, int 0
 *   color 0    : turns the ring off
 */
object LightsBinder {
    private const val DESCRIPTOR = "miui.lights.ILightsManager"
    private const val TX_SET_COLOR = 2
    private const val MASTER_KEY = "settings_strip_light_enable"

    @Volatile
    private var binder: IBinder? = null

    fun shizukuRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        false
    }

    fun hasPermission(): Boolean = try {
        shizukuRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    private fun obtain(): IBinder? {
        val cached = binder
        if (cached != null && cached.isBinderAlive) return cached
        val raw = SystemServiceHelper.getSystemService(DESCRIPTOR) ?: return null
        val wrapped: IBinder = ShizukuBinderWrapper(raw)
        binder = wrapped
        return wrapped
    }

    /** Sends an ARGB color to the whole ring. Pass 0 to switch it off. */
    fun setColor(color: Int, pkg: String): Boolean {
        val b = obtain() ?: return false
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeInt(color)
            data.writeString(pkg)
            data.writeInt(3)
            data.writeInt(0)
            b.transact(TX_SET_COLOR, data, reply, 0)
            reply.readException()
            true
        } catch (t: Throwable) {
            binder = null
            false
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    /** Master switch of the HyperOS ring light (Settings.Global). */
    fun setMaster(ctx: Context, on: Boolean): Boolean {
        val v = if (on) 1 else 0
        try {
            if (Settings.Global.putInt(ctx.contentResolver, MASTER_KEY, v)) return true
        } catch (t: Throwable) {
            // WRITE_SECURE_SETTINGS not granted: fall back to a shell command via Shizuku.
        }
        return shell("settings", "put", "global", MASTER_KEY, v.toString())
    }

    /** Runs a command with shell privileges through Shizuku. */
    private fun shell(vararg args: String): Boolean = try {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        m.isAccessible = true
        val p = m.invoke(null, arrayOf(*args), null, null) as Process
        p.waitFor() == 0
    } catch (t: Throwable) {
        false
    }
}
