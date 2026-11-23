package com.kzeo.rgb

import android.content.Context
import android.graphics.Color
import java.util.Locale

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("kzeo_rgb", Context.MODE_PRIVATE)

    fun color(c: Context): Int = sp(c).getInt("color", Color.parseColor("#E53935"))
    fun setColor(c: Context, v: Int) = sp(c).edit().putInt("color", v).apply()

    fun ringOn(c: Context): Boolean = sp(c).getBoolean("ring_on", false)
    fun setRingOn(c: Context, v: Boolean) = sp(c).edit().putBoolean("ring_on", v).apply()

    fun alwaysOn(c: Context): Boolean = sp(c).getBoolean("always_on", false)
    fun setAlwaysOn(c: Context, v: Boolean) = sp(c).edit().putBoolean("always_on", v).apply()

    /** The name the user typed on first launch. Empty means onboarding is still needed. */
    fun name(c: Context): String = sp(c).getString("name", "") ?: ""
    fun setName(c: Context, v: String) = sp(c).edit().putString("name", v).apply()

    fun tourDone(c: Context): Boolean = sp(c).getBoolean("tour_done", false)
    fun setTourDone(c: Context, v: Boolean) = sp(c).edit().putBoolean("tour_done", v).apply()

    /** "id" or "en". Defaults to the phone's language. */
    fun lang(c: Context): String {
        val saved = sp(c).getString("lang", null)
        if (saved != null) return saved
        val l = Locale.getDefault().language
        return if (l == "in" || l == "id") "id" else "en"
    }

    fun setLang(c: Context, v: String) = sp(c).edit().putString("lang", v).apply()

    /** 0 = follow system, 1 = light, 2 = dark. */
    fun theme(c: Context): Int = sp(c).getInt("theme", 0)
    fun setTheme(c: Context, v: Int) = sp(c).edit().putInt("theme", v).apply()
}
