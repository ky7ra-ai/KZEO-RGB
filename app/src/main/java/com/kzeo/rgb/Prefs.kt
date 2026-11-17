package com.kzeo.rgb

import android.content.Context
import android.graphics.Color

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("kzeo_rgb", Context.MODE_PRIVATE)

    fun color(c: Context): Int = sp(c).getInt("color", Color.parseColor("#E53935"))
    fun setColor(c: Context, v: Int) = sp(c).edit().putInt("color", v).apply()

    fun ringOn(c: Context): Boolean = sp(c).getBoolean("ring_on", false)
    fun setRingOn(c: Context, v: Boolean) = sp(c).edit().putBoolean("ring_on", v).apply()

    fun alwaysOn(c: Context): Boolean = sp(c).getBoolean("always_on", false)
    fun setAlwaysOn(c: Context, v: Boolean) = sp(c).edit().putBoolean("always_on", v).apply()
}
