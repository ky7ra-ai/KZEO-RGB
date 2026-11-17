package com.kzeo.rgb

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku
import java.util.Calendar
import java.util.concurrent.Executors

class MainActivity : Activity() {

    // Palette: soft lavender glass
    private val accent = Color.parseColor("#6A5CF5")
    private val ink = Color.parseColor("#15162E")
    private val mute = Color.parseColor("#6C7090")
    private val lav = Color.parseColor("#E7E4FF")
    private val trackOff = Color.parseColor("#CDD0E4")
    private val bgTop = Color.parseColor("#ECEFFF")
    private val bgBottom = Color.parseColor("#F9F6FF")
    private val danger = Color.parseColor("#E5484D")

    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() {
            updateDiag()
            updateStats()
            ui.postDelayed(this, 700)
        }
    }

    private lateinit var tileRing: TextView
    private lateinit var tileColor: TextView
    private lateinit var tileShizuku: TextView
    private lateinit var heroCard: LinearLayout
    private lateinit var heroState: TextView
    private lateinit var heroTitle: TextView
    private lateinit var heroSub: TextView
    private lateinit var avatarOuter: View
    private lateinit var diag: TextView
    private lateinit var connectBtn: Button
    private lateinit var batterySub: TextView
    private lateinit var ringSwitch: Switch
    private lateinit var alwaysSwitch: Switch
    private lateinit var seekR: SeekBar
    private lateinit var seekG: SeekBar
    private lateinit var seekB: SeekBar
    private lateinit var valR: TextView
    private lateinit var valG: TextView
    private lateinit var valB: TextView
    private val presets = ArrayList<Pair<View, Int>>()

    private var guard = false

    private val permListener =
        Shizuku.OnRequestPermissionResultListener { _, _ -> runOnUiThread { refreshStatus() } }
    private val binderListener = Shizuku.OnBinderReceivedListener { runOnUiThread { refreshStatus() } }
    private val deadListener = Shizuku.OnBinderDeadListener { runOnUiThread { refreshStatus() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = bgBottom
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
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
        ui.post(poll)
    }

    override fun onPause() {
        ui.removeCallbacks(poll)
        super.onPause()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permListener)
        Shizuku.removeBinderReceivedListener(binderListener)
        Shizuku.removeBinderDeadListener(deadListener)
        super.onDestroy()
    }

    // ---------------------------------------------------------------- UI helpers

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun tv(s: String, size: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        return t
    }

    private fun glass(radius: Int = 22): GradientDrawable {
        val d = GradientDrawable()
        d.cornerRadius = dp(radius).toFloat()
        d.setColor(Color.parseColor("#B8FFFFFF"))
        d.setStroke(dp(1), Color.parseColor("#E6FFFFFF"))
        return d
    }

    private fun circle(color: Int): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.OVAL
        d.setColor(color)
        return d
    }

    private fun iconCircle(res: Int, tint: Int, bg: Int, size: Int): ImageView {
        val v = ImageView(this)
        v.setImageResource(res)
        v.setColorFilter(tint)
        v.background = circle(bg)
        val pad = dp(size) / 4
        v.setPadding(pad, pad, pad, pad)
        v.layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
        return v
    }

    private fun newSwitch(): Switch {
        val sw = Switch(this)
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        sw.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        sw.trackTintList = ColorStateList(states, intArrayOf(accent, trackOff))
        return sw
    }

    private fun lp(w: Int, h: Int, top: Int = 0, bottom: Int = 0, end: Int = 0, weight: Float = 0f):
            LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(w, h, weight)
        p.topMargin = dp(top)
        p.bottomMargin = dp(bottom)
        p.marginEnd = dp(end)
        return p
    }

    private fun section(root: LinearLayout, title: String) {
        root.addView(tv(title, 18f, ink, true), lp(-2, -2, top = 24, bottom = 10))
    }

    private fun greeting(): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            h < 5 -> "Good Night"
            h < 12 -> "Good Morning"
            h < 18 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

    private fun statTile(icon: Int, tint: Int, bg: Int, value: TextView, label: String, end: Int): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.background = glass(22)
        box.setPadding(dp(14), dp(14), dp(14), dp(14))
        box.addView(iconCircle(icon, tint, bg, 36))
        value.textSize = 16f
        value.setTextColor(ink)
        value.setTypeface(value.typeface, Typeface.BOLD)
        value.maxLines = 1
        box.addView(value, lp(-2, -2, top = 12))
        box.addView(tv(label, 12f, mute))
        box.layoutParams = lp(0, -2, end = end, weight = 1f)
        return box
    }

    private fun controlRow(icon: Int, title: String, sub: TextView, trailing: View): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.background = glass(22)
        row.setPadding(dp(14), dp(14), dp(14), dp(14))
        row.addView(iconCircle(icon, accent, lav, 40))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(tv(title, 15f, ink, true))
        sub.textSize = 12f
        sub.setTextColor(mute)
        col.addView(sub)
        val cp = lp(0, -2, weight = 1f)
        cp.marginStart = dp(12)
        cp.marginEnd = dp(8)
        row.addView(col, cp)
        row.addView(trailing)
        return row
    }

    private fun slider(parent: LinearLayout, label: String, value: Int, tint: Int, valueView: TextView): SeekBar {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.addView(tv(label, 14f, ink, true), LinearLayout.LayoutParams(dp(22), -2))

        val sb = SeekBar(this)
        sb.max = 255
        sb.progress = value
        sb.progressTintList = ColorStateList.valueOf(tint)
        sb.thumbTintList = ColorStateList.valueOf(tint)
        sb.progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#D8DAEC"))
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) onColorChanged()
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        row.addView(sb, LinearLayout.LayoutParams(0, dp(40), 1f))

        valueView.textSize = 13f
        valueView.setTextColor(mute)
        valueView.gravity = Gravity.END
        row.addView(valueView, LinearLayout.LayoutParams(dp(34), -2))
        parent.addView(row)
        return sb
    }

    // ---------------------------------------------------------------- UI

    private fun buildUi() {
        val c = Prefs.color(this)

        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        scroll.isVerticalScrollBarEnabled = false
        scroll.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(bgTop, bgBottom))
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(20), dp(52), dp(20), dp(32))
        scroll.addView(root, ViewGroup.LayoutParams(-1, -2))
        setContentView(scroll)
        scroll.setOnApplyWindowInsetsListener { _, ins ->
            root.setPadding(
                dp(20), ins.systemWindowInsetTop + dp(20),
                dp(20), ins.systemWindowInsetBottom + dp(32)
            )
            ins
        }

        // Header
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        val left = LinearLayout(this)
        left.orientation = LinearLayout.VERTICAL
        left.addView(tv(greeting(), 14f, mute))
        left.addView(tv("KZEO RGB", 32f, ink, true), lp(-2, -2, top = 2))
        left.addView(tv("Control your camera ring light.", 14f, mute), lp(-2, -2, top = 6))
        header.addView(left, lp(0, -2, weight = 1f))

        val avatar = FrameLayout(this)
        avatarOuter = View(this)
        val inner = View(this)
        inner.background = circle(Color.parseColor("#F4F2FF"))
        avatar.addView(avatarOuter, FrameLayout.LayoutParams(dp(84), dp(84)))
        avatar.addView(inner, FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER))
        header.addView(avatar, LinearLayout.LayoutParams(dp(84), dp(84)))
        root.addView(header)

        // Stat tiles
        tileRing = TextView(this)
        tileColor = TextView(this)
        tileShizuku = TextView(this)
        val stats = LinearLayout(this)
        stats.orientation = LinearLayout.HORIZONTAL
        stats.addView(statTile(R.drawable.ic_power, accent, lav, tileRing, "Ring", 10))
        stats.addView(statTile(R.drawable.ic_palette, Color.parseColor("#2F7BFF"), Color.parseColor("#DDEBFF"), tileColor, "Color", 10))
        stats.addView(statTile(R.drawable.ic_bolt, Color.parseColor("#F26B3A"), Color.parseColor("#FFE6DC"), tileShizuku, "Shizuku", 0))
        root.addView(stats, lp(-1, -2, top = 24))

        // Hero card
        heroCard = LinearLayout(this)
        heroCard.orientation = LinearLayout.HORIZONTAL
        heroCard.gravity = Gravity.CENTER_VERTICAL
        heroCard.setPadding(dp(20), dp(20), dp(20), dp(20))
        val heroText = LinearLayout(this)
        heroText.orientation = LinearLayout.VERTICAL
        heroState = tv("", 13f, Color.WHITE)
        heroTitle = tv("", 28f, Color.WHITE, true)
        heroSub = tv("Tap the button to turn the ring on or off.", 13f, Color.WHITE)
        heroText.addView(heroState)
        heroText.addView(heroTitle, lp(-2, -2, top = 8))
        heroText.addView(heroSub, lp(-2, -2, top = 4))
        heroCard.addView(heroText, lp(0, -2, weight = 1f))
        val power = ImageView(this)
        power.setImageResource(R.drawable.ic_power)
        power.setColorFilter(accent)
        power.background = circle(Color.WHITE)
        power.setPadding(dp(16), dp(16), dp(16), dp(16))
        power.setOnClickListener { ringSwitch.isChecked = !ringSwitch.isChecked }
        heroCard.addView(power, LinearLayout.LayoutParams(dp(56), dp(56)))
        root.addView(heroCard, lp(-1, -2, top = 16))

        // Color
        section(root, "Color")
        val colorCard = LinearLayout(this)
        colorCard.orientation = LinearLayout.VERTICAL
        colorCard.background = glass(22)
        colorCard.setPadding(dp(16), dp(10), dp(16), dp(10))
        valR = TextView(this)
        valG = TextView(this)
        valB = TextView(this)
        seekR = slider(colorCard, "R", Color.red(c), Color.parseColor("#FF5A5F"), valR)
        seekG = slider(colorCard, "G", Color.green(c), Color.parseColor("#22C55E"), valG)
        seekB = slider(colorCard, "B", Color.blue(c), Color.parseColor("#3B82F6"), valB)

        val dots = LinearLayout(this)
        dots.orientation = LinearLayout.HORIZONTAL
        val hexes = listOf(
            "#E53935", "#FF7A00", "#FFD600", "#00E676",
            "#00E5FF", "#2979FF", "#B040FF", "#FFFFFF"
        )
        for (hex in hexes) {
            val col = Color.parseColor(hex)
            val wrap = FrameLayout(this)
            val dot = View(this)
            wrap.addView(dot, FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER))
            wrap.setOnClickListener { setSliders(col) }
            dots.addView(wrap, LinearLayout.LayoutParams(0, dp(40), 1f))
            presets.add(Pair(dot, col))
        }
        colorCard.addView(dots, lp(-1, -2, top = 6, bottom = 4))
        root.addView(colorCard)

        // Controls
        section(root, "Controls")

        connectBtn = Button(this)
        connectBtn.text = "Grant Shizuku permission"
        connectBtn.isAllCaps = false
        connectBtn.setTextColor(Color.WHITE)
        val cb = GradientDrawable()
        cb.cornerRadius = dp(22).toFloat()
        cb.setColor(accent)
        connectBtn.background = cb
        connectBtn.setOnClickListener { requestShizuku() }
        connectBtn.visibility = View.GONE
        root.addView(connectBtn, lp(-1, dp(52), bottom = 10))

        ringSwitch = newSwitch()
        ringSwitch.setOnCheckedChangeListener { _, checked -> if (!guard) onRingToggled(checked) }
        root.addView(
            controlRow(R.drawable.ic_power, "RGB ring", tv("Turn the ring light on or off", 12f, mute), ringSwitch),
            lp(-1, -2, bottom = 10)
        )

        alwaysSwitch = newSwitch()
        alwaysSwitch.setOnCheckedChangeListener { _, checked -> if (!guard) onAlwaysToggled(checked) }
        root.addView(
            controlRow(R.drawable.ic_sync, "RGB always on", tv("Restarts after reboot (Shizuku)", 12f, mute), alwaysSwitch),
            lp(-1, -2, bottom = 10)
        )

        batterySub = tv("", 12f, mute)
        val chevron = ImageView(this)
        chevron.setImageResource(R.drawable.ic_chevron)
        chevron.setColorFilter(mute)
        val batteryRow = controlRow(R.drawable.ic_battery, "Battery optimization", batterySub, chevron)
        batteryRow.setOnClickListener { openBatterySettings() }
        root.addView(batteryRow, lp(-1, -2, bottom = 10))

        diag = tv("", 12f, mute)
        diag.gravity = Gravity.CENTER
        root.addView(diag, lp(-1, -2, top = 6))

        // Initial state
        guard = true
        ringSwitch.isChecked = Prefs.ringOn(this)
        alwaysSwitch.isChecked = Prefs.alwaysOn(this)
        guard = false
        updatePreview(c)
        updateStats()
    }

    private fun lum(color: Int): Float =
        (0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color)) / 255f

    private fun shade(color: Int, f: Float): Int =
        Color.rgb(
            (Color.red(color) * f).toInt(),
            (Color.green(color) * f).toInt(),
            (Color.blue(color) * f).toInt()
        )

    private fun hex(color: Int): String =
        String.format("#%02X%02X%02X", Color.red(color), Color.green(color), Color.blue(color))

    private fun updatePreview(color: Int) {
        val bg = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(color, shade(color, 0.62f)))
        bg.cornerRadius = dp(28).toFloat()
        heroCard.background = bg
        val fg = if (lum(color) > 0.62f) ink else Color.WHITE
        heroTitle.text = hex(color)
        heroTitle.setTextColor(fg)
        heroSub.setTextColor(fg)
        heroState.setTextColor(fg)

        avatarOuter.background = circle(color)
        tileColor.text = hex(color)
        valR.text = Color.red(color).toString()
        valG.text = Color.green(color).toString()
        valB.text = Color.blue(color).toString()

        for (p in presets) {
            val d = circle(p.second)
            d.setStroke(
                dp(if (p.second == color) 3 else 1),
                if (p.second == color) accent else Color.parseColor("#D5D8EA")
            )
            p.first.background = d
        }
    }

    private fun updateStats() {
        val on = Prefs.ringOn(this)
        tileRing.text = if (on) "On" else "Off"
        heroState.text = if (on) "● Ring is on" else "○ Ring is off"
    }

    private fun updateDiag() {
        val err = LightsBinder.lastError ?: LightsBinder.masterError
        if (err != null) {
            diag.text = "Error: $err"
            diag.setTextColor(danger)
        } else if (LightsBinder.lastOkAt > 0) {
            diag.text = "Last command sent OK"
            diag.setTextColor(mute)
        } else {
            diag.text = ""
        }
    }

    // ---------------------------------------------------------------- actions

    private fun setSliders(color: Int) {
        seekR.progress = Color.red(color)
        seekG.progress = Color.green(color)
        seekB.progress = Color.blue(color)
        onColorChanged()
    }

    private fun currentColor(): Int = Color.rgb(seekR.progress, seekG.progress, seekB.progress)

    private fun onColorChanged() {
        val c = currentColor()
        Prefs.setColor(this, c)
        updatePreview(c)
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

    private fun startRing() {
        startForegroundService(Intent(this, RingService::class.java))
    }

    private fun onRingToggled(on: Boolean) {
        if (needShizuku()) {
            setSwitch(ringSwitch, false)
            return
        }
        Prefs.setRingOn(this, on)
        if (on) {
            io.execute { LightsBinder.setMaster(this, true) }
            startRing()
        } else {
            Prefs.setAlwaysOn(this, false)
            setSwitch(alwaysSwitch, false)
            stopService(Intent(this, RingService::class.java))
            io.execute {
                Thread.sleep(350)
                LightsBinder.setColor(0, packageName)
                LightsBinder.setMaster(this, false)
            }
        }
        updateStats()
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
                startRing()
                updateStats()
            }
        } else {
            Prefs.setAlwaysOn(this, false)
        }
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
        tileShizuku.text = when {
            !running -> "Offline"
            !perm -> "No access"
            else -> "Ready"
        }
        connectBtn.visibility = if (running && !perm) View.VISIBLE else View.GONE

        val pm = getSystemService(PowerManager::class.java)
        batterySub.text = if (pm.isIgnoringBatteryOptimizations(packageName))
            "Unrestricted" else "Tap to disable battery optimization"
        updateStats()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
