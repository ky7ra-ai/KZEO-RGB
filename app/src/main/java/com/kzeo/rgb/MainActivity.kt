package com.kzeo.rgb

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private val red = Color.parseColor("#E53935")
    private val gray = Color.parseColor("#8A8A8A")
    private val dark = Color.parseColor("#1A1A1A")
    private val io = Executors.newSingleThreadExecutor()

    private lateinit var status: TextView
    private lateinit var connectBtn: Button
    private lateinit var batteryBtn: Button
    private lateinit var ringSwitch: Switch
    private lateinit var alwaysSwitch: Switch
    private lateinit var preview: View
    private lateinit var hexText: TextView
    private lateinit var seekR: SeekBar
    private lateinit var seekG: SeekBar
    private lateinit var seekB: SeekBar

    private var guard = false

    private val permListener =
        Shizuku.OnRequestPermissionResultListener { _, _ -> runOnUiThread { refreshStatus() } }
    private val binderListener = Shizuku.OnBinderReceivedListener { runOnUiThread { refreshStatus() } }
    private val deadListener = Shizuku.OnBinderDeadListener { runOnUiThread { refreshStatus() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        buildUi()

        Shizuku.addRequestPermissionResultListener(permListener)
        Shizuku.addBinderReceivedListenerSticky(binderListener)
        Shizuku.addBinderDeadListener(deadListener)

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permListener)
        Shizuku.removeBinderReceivedListener(binderListener)
        Shizuku.removeBinderDeadListener(deadListener)
        super.onDestroy()
    }

    // ---------------------------------------------------------------- UI

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.BLACK)
        root.setPadding(dp(24), dp(40), dp(24), dp(24))

        val scroll = android.widget.ScrollView(this)
        scroll.setBackgroundColor(Color.BLACK)
        scroll.isFillViewport = true
        scroll.addView(root, ViewGroup.LayoutParams(-1, -2))
        setContentView(scroll)

        // Header
        root.addView(text("KZEO RGB", 30f, Color.WHITE, bold = true))
        status = text("", 14f, gray)
        status.setPadding(0, dp(4), 0, dp(20))
        root.addView(status)

        connectBtn = button("Grant Shizuku permission") { requestShizuku() }
        connectBtn.visibility = View.GONE
        root.addView(connectBtn, lp(bottom = 16))

        // Ring on / off
        ringSwitch = switchRow(root, "RGB ring on / off")
        ringSwitch.setOnCheckedChangeListener { _, checked ->
            if (!guard) onRingToggled(checked)
        }

        // Preview
        preview = View(this)
        root.addView(preview, LinearLayout.LayoutParams(-1, dp(72)).also {
            it.topMargin = dp(20)
            it.bottomMargin = dp(8)
        })
        hexText = text("", 14f, gray)
        hexText.gravity = Gravity.CENTER
        root.addView(hexText)

        // Sliders
        root.addView(text("Color", 16f, Color.WHITE, bold = true).also { it.setPadding(0, dp(16), 0, dp(4)) })
        val c = Prefs.color(this)
        seekR = slider(root, "R", Color.red(c))
        seekG = slider(root, "G", Color.green(c))
        seekB = slider(root, "B", Color.blue(c))

        // Presets
        val presets = LinearLayout(this)
        presets.orientation = LinearLayout.HORIZONTAL
        presets.gravity = Gravity.CENTER
        val colors = listOf(
            "#E53935", "#FF7A00", "#FFD600", "#00E676",
            "#00E5FF", "#2979FF", "#B040FF", "#FFFFFF"
        )
        for (hex in colors) {
            val dot = View(this)
            val d = GradientDrawable()
            d.shape = GradientDrawable.OVAL
            d.setColor(Color.parseColor(hex))
            dot.background = d
            dot.setOnClickListener { setSliders(Color.parseColor(hex)) }
            val p = LinearLayout.LayoutParams(dp(32), dp(32))
            p.setMargins(dp(5), dp(12), dp(5), dp(12))
            presets.addView(dot, p)
        }
        root.addView(presets)

        // Always on
        alwaysSwitch = switchRow(root, "RGB always on (Shizuku)")
        alwaysSwitch.setOnCheckedChangeListener { _, checked ->
            if (!guard) onAlwaysToggled(checked)
        }

        // Battery optimization
        batteryBtn = button("Disable battery optimization") { openBatterySettings() }
        root.addView(batteryBtn, lp(top = 20))

        // Initial state
        guard = true
        ringSwitch.isChecked = Prefs.ringOn(this)
        alwaysSwitch.isChecked = Prefs.alwaysOn(this)
        guard = false
        updatePreview(c)
    }

    private fun lp(top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(-1, dp(52))
        p.topMargin = dp(top)
        p.bottomMargin = dp(bottom)
        return p
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, android.graphics.Typeface.BOLD)
        return t
    }

    private fun button(label: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.textSize = 15f
        b.setTextColor(Color.WHITE)
        val bg = GradientDrawable()
        bg.cornerRadius = dp(14).toFloat()
        bg.setColor(red)
        b.background = bg
        b.setOnClickListener { onClick() }
        return b
    }

    private fun switchRow(parent: LinearLayout, label: String): Switch {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val bg = GradientDrawable()
        bg.cornerRadius = dp(14).toFloat()
        bg.setColor(dark)
        row.background = bg
        row.setPadding(dp(16), dp(8), dp(16), dp(8))

        val t = text(label, 16f, Color.WHITE)
        row.addView(t, LinearLayout.LayoutParams(0, -2, 1f))

        val sw = Switch(this)
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        sw.thumbTintList = ColorStateList(states, intArrayOf(red, gray))
        sw.trackTintList = ColorStateList(states, intArrayOf(Color.parseColor("#5A1513"), Color.parseColor("#333333")))
        row.addView(sw)

        val p = LinearLayout.LayoutParams(-1, dp(60))
        p.topMargin = dp(8)
        parent.addView(row, p)
        return sw
    }

    private fun slider(parent: LinearLayout, label: String, value: Int): SeekBar {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.addView(text(label, 15f, Color.WHITE), LinearLayout.LayoutParams(dp(24), -2))

        val sb = SeekBar(this)
        sb.max = 255
        sb.progress = value
        sb.progressTintList = ColorStateList.valueOf(red)
        sb.thumbTintList = ColorStateList.valueOf(red)
        sb.progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#444444"))
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) onColorChanged()
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        row.addView(sb, LinearLayout.LayoutParams(0, -2, 1f))
        parent.addView(row)
        return sb
    }

    private fun setSliders(color: Int) {
        seekR.progress = Color.red(color)
        seekG.progress = Color.green(color)
        seekB.progress = Color.blue(color)
        onColorChanged()
    }

    private fun updatePreview(color: Int) {
        val d = GradientDrawable()
        d.cornerRadius = dp(16).toFloat()
        d.setColor(color)
        preview.background = d
        hexText.text = String.format("#%02X%02X%02X", Color.red(color), Color.green(color), Color.blue(color))
    }

    // ---------------------------------------------------------------- actions

    private fun currentColor(): Int = Color.rgb(seekR.progress, seekG.progress, seekB.progress)

    private fun onColorChanged() {
        val c = currentColor()
        Prefs.setColor(this, c)
        updatePreview(c)
        if (Prefs.ringOn(this) && LightsBinder.hasPermission()) {
            io.execute { LightsBinder.setColor(c, packageName) }
        }
    }

    private fun needShizuku(): Boolean {
        if (LightsBinder.hasPermission()) return false
        toast(
            if (!LightsBinder.shizukuRunning()) "Start Shizuku first"
            else "Grant Shizuku permission first"
        )
        refreshStatus()
        return true
    }

    private fun setSwitch(sw: Switch, v: Boolean) {
        guard = true
        sw.isChecked = v
        guard = false
    }

    private fun onRingToggled(on: Boolean) {
        if (needShizuku()) {
            setSwitch(ringSwitch, false)
            return
        }
        Prefs.setRingOn(this, on)
        if (on) {
            val c = currentColor()
            io.execute {
                LightsBinder.setMaster(this, true)
                val ok = LightsBinder.setColor(c, packageName)
                if (!ok) runOnUiThread { toast("Could not reach the light service") }
            }
        } else {
            stopAlways()
            io.execute {
                Thread.sleep(250)
                LightsBinder.setColor(0, packageName)
                LightsBinder.setMaster(this, false)
            }
        }
    }

    private fun onAlwaysToggled(on: Boolean) {
        if (on) {
            if (needShizuku()) {
                setSwitch(alwaysSwitch, false)
                return
            }
            Prefs.setAlwaysOn(this, true)
            if (!Prefs.ringOn(this)) {
                Prefs.setRingOn(this, true)
                setSwitch(ringSwitch, true)
                io.execute { LightsBinder.setMaster(this, true) }
            }
            startForegroundService(Intent(this, RingService::class.java))
        } else {
            stopAlways()
            if (Prefs.ringOn(this)) {
                val c = currentColor()
                io.execute { LightsBinder.setColor(c, packageName) }
            }
        }
    }

    private fun stopAlways() {
        Prefs.setAlwaysOn(this, false)
        setSwitch(alwaysSwitch, false)
        stopService(Intent(this, RingService::class.java))
    }

    private fun requestShizuku() {
        try {
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                toast("Permission was denied. Allow it in the Shizuku app.")
            } else {
                Shizuku.requestPermission(1)
            }
        } catch (t: Throwable) {
            toast("Shizuku is not available")
        }
    }

    private fun openBatterySettings() {
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            toast("Battery optimization is already disabled")
            return
        }
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
            )
        } catch (t: Throwable) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (t2: Throwable) {
                toast("Could not open battery settings")
            }
        }
    }

    private fun refreshStatus() {
        val running = LightsBinder.shizukuRunning()
        val perm = LightsBinder.hasPermission()
        status.text = when {
            !running -> "Shizuku is not running"
            !perm -> "Shizuku permission needed"
            else -> "Shizuku connected"
        }
        status.setTextColor(if (perm) gray else red)
        connectBtn.visibility = if (running && !perm) View.VISIBLE else View.GONE

        val pm = getSystemService(PowerManager::class.java)
        batteryBtn.text = if (pm.isIgnoringBatteryOptimizations(packageName))
            "Battery optimization: disabled" else "Disable battery optimization"
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
