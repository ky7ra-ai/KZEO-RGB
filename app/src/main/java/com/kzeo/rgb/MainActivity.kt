package com.kzeo.rgb

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
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
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
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

    // ---------------------------------------------------------------- theme + language

    /** True when the dark palette is active (user choice, or the system setting). */
    private val dark: Boolean
        get() = when (Prefs.theme(this)) {
            1 -> false
            2 -> true
            else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
        }

    private fun t(en: String, id: String): String = if (Prefs.lang(this) == "id") id else en

    private val accent: Int get() = Color.parseColor(if (dark) "#8F82FF" else "#6A5CF5")
    private val ink: Int get() = Color.parseColor(if (dark) "#F1F1FF" else "#15162E")
    private val mute: Int get() = Color.parseColor(if (dark) "#9B9EC4" else "#6C7090")
    private val lav: Int get() = Color.parseColor(if (dark) "#2B2858" else "#E7E4FF")
    private val blue: Int get() = Color.parseColor("#2F7BFF")
    private val blueBg: Int get() = Color.parseColor(if (dark) "#1C2C57" else "#DDEBFF")
    private val orange: Int get() = Color.parseColor("#F26B3A")
    private val orangeBg: Int get() = Color.parseColor(if (dark) "#4A2A20" else "#FFE6DC")
    private val trackOff: Int get() = Color.parseColor(if (dark) "#3C3F63" else "#CDD0E4")
    private val seekBg: Int get() = Color.parseColor(if (dark) "#3C3F63" else "#D8DAEC")
    private val dotBorder: Int get() = Color.parseColor(if (dark) "#4A4D70" else "#D5D8EA")
    private val segTrack: Int get() = Color.parseColor(if (dark) "#262846" else "#E9E8F8")
    private val bgTop: Int get() = Color.parseColor(if (dark) "#0E0F22" else "#ECEFFF")
    private val bgBottom: Int get() = Color.parseColor(if (dark) "#171433" else "#F9F6FF")
    private val danger: Int get() = Color.parseColor("#E5484D")

    // ---------------------------------------------------------------- state

    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() {
            if (homeReady) {
                updateDiag()
                updateStats()
            }
            ui.postDelayed(this, 700)
        }
    }

    /** 0 = Home, 1 = Settings. */
    private var tab = 0

    /** True while the Home views below exist and are on screen. */
    private var homeReady = false

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
        tab = savedInstanceState?.getInt("tab") ?: 0
        render()

        Shizuku.addRequestPermissionResultListener(permListener)
        Shizuku.addBinderReceivedListenerSticky(binderListener)
        Shizuku.addBinderDeadListener(deadListener)

        if (Prefs.name(this).isNotBlank()) askNotifications()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("tab", tab)
        super.onSaveInstanceState(outState)
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

    private fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
        }
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
        d.setColor(Color.parseColor(if (dark) "#26FFFFFF" else "#B8FFFFFF"))
        d.setStroke(dp(1), Color.parseColor(if (dark) "#33FFFFFF" else "#E6FFFFFF"))
        return d
    }

    private fun pill(color: Int, radius: Int): GradientDrawable {
        val d = GradientDrawable()
        d.cornerRadius = dp(radius).toFloat()
        d.setColor(color)
        return d
    }

    private fun circle(color: Int): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.OVAL
        d.setColor(color)
        return d
    }

    private fun background(): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(bgTop, bgBottom))

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
        return if (Prefs.lang(this) == "id") {
            when {
                h < 5 -> "Selamat malam"
                h < 11 -> "Selamat pagi"
                h < 15 -> "Selamat siang"
                h < 18 -> "Selamat sore"
                else -> "Selamat malam"
            }
        } else {
            when {
                h < 5 -> "Good Night"
                h < 12 -> "Good Morning"
                h < 18 -> "Good Afternoon"
                else -> "Good Evening"
            }
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

    private fun chevron(): ImageView {
        val c = ImageView(this)
        c.setImageResource(R.drawable.ic_chevron)
        c.setColorFilter(mute)
        return c
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
        sb.progressBackgroundTintList = ColorStateList.valueOf(seekBg)
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

    private fun segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit): LinearLayout {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.HORIZONTAL
        box.background = pill(segTrack, 16)
        box.setPadding(dp(4), dp(4), dp(4), dp(4))
        for (i in options.indices) {
            val on = i == selected
            val label = tv(options[i], 13f, if (on) Color.WHITE else ink, on)
            label.gravity = Gravity.CENTER
            label.maxLines = 1
            label.setPadding(dp(4), dp(10), dp(4), dp(10))
            if (on) label.background = pill(accent, 12)
            label.setOnClickListener { if (!on) onSelect(i) }
            box.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
        }
        return box
    }

    private fun settingCard(icon: Int, title: String, sub: String, control: View): LinearLayout {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = glass(22)
        card.setPadding(dp(14), dp(14), dp(14), dp(14))

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(iconCircle(icon, accent, lav, 40))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(tv(title, 15f, ink, true))
        col.addView(tv(sub, 12f, mute))
        val cp = lp(0, -2, weight = 1f)
        cp.marginStart = dp(12)
        head.addView(col, cp)
        card.addView(head)
        card.addView(control, lp(-1, -2, top = 14))
        return card
    }

    // ---------------------------------------------------------------- screens

    /** Draws whatever screen is current: onboarding, or the shell with Home / Settings. */
    private fun render() {
        homeReady = false
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = bgBottom
        var flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        if (!dark) {
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        window.decorView.systemUiVisibility = flags

        if (Prefs.name(this).isBlank()) buildOnboarding() else buildShell()
    }

    private fun buildOnboarding() {
        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        scroll.isVerticalScrollBarEnabled = false
        scroll.background = background()

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER
        col.setPadding(dp(28), dp(24), dp(28), dp(24))
        scroll.addView(col, ViewGroup.LayoutParams(-1, -2))
        scroll.setOnApplyWindowInsetsListener { _, ins ->
            col.setPadding(dp(28), ins.systemWindowInsetTop + dp(24), dp(28), ins.systemWindowInsetBottom + dp(24))
            ins
        }

        val badge = ImageView(this)
        badge.setImageResource(R.drawable.ic_power)
        badge.setColorFilter(Color.WHITE)
        badge.background = circle(accent)
        badge.setPadding(dp(24), dp(24), dp(24), dp(24))
        col.addView(badge, LinearLayout.LayoutParams(dp(88), dp(88)))

        val title = tv(t("Welcome", "Selamat datang"), 30f, ink, true)
        title.gravity = Gravity.CENTER
        col.addView(title, lp(-2, -2, top = 28))
        val sub = tv(t("What should we call you?", "Siapa nama kamu?"), 16f, mute)
        sub.gravity = Gravity.CENTER
        col.addView(sub, lp(-2, -2, top = 8, bottom = 28))

        val et = EditText(this)
        et.hint = t("Your name", "Nama kamu")
        et.setHintTextColor(mute)
        et.setTextColor(ink)
        et.textSize = 18f
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        et.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(20))
        et.gravity = Gravity.CENTER
        et.background = glass(22)
        et.setPadding(dp(18), dp(16), dp(18), dp(16))
        et.imeOptions = EditorInfo.IME_ACTION_DONE
        col.addView(et, lp(-1, -2))

        fun submit() {
            val n = et.text.toString().trim()
            if (n.isEmpty()) {
                toast(t("Please enter your name", "Isi namamu dulu ya"))
                return
            }
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(et.windowToken, 0)
            Prefs.setName(this, n)
            tab = 0
            render()
            askNotifications()
        }

        et.setOnEditorActionListener { _, _, _ ->
            submit()
            true
        }

        val go = Button(this)
        go.text = t("Continue", "Lanjut")
        go.isAllCaps = false
        go.textSize = 16f
        go.setTextColor(Color.WHITE)
        go.background = pill(accent, 22)
        go.setOnClickListener { submit() }
        col.addView(go, lp(-1, dp(54), top = 16))

        setContentView(scroll)
    }

    private fun buildShell() {
        val shell = LinearLayout(this)
        shell.orientation = LinearLayout.VERTICAL
        shell.background = background()

        val scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(20), dp(16), dp(20), dp(16))
        scroll.addView(root, ViewGroup.LayoutParams(-1, -2))
        shell.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val nav = buildNav()
        val np = lp(-1, -2, top = 4, bottom = 10)
        np.marginStart = dp(16)
        np.marginEnd = dp(16)
        shell.addView(nav, np)

        setContentView(shell)
        shell.setOnApplyWindowInsetsListener { _, ins ->
            shell.setPadding(0, ins.systemWindowInsetTop, 0, ins.systemWindowInsetBottom)
            ins
        }

        if (tab == 0) buildHome(root) else buildSettings(root)
    }

    private fun buildNav(): View {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER
        bar.background = glass(30)
        bar.setPadding(dp(8), dp(8), dp(8), dp(8))
        bar.addView(navItem(R.drawable.ic_home, t("Home", "Beranda"), 0), lp(0, -2, end = 8, weight = 1f))
        bar.addView(navItem(R.drawable.ic_settings, t("Settings", "Pengaturan"), 1), lp(0, -2, weight = 1f))
        return bar
    }

    private fun navItem(icon: Int, label: String, index: Int): View {
        val selected = tab == index
        val item = LinearLayout(this)
        item.orientation = LinearLayout.VERTICAL
        item.gravity = Gravity.CENTER_HORIZONTAL
        item.setPadding(0, dp(8), 0, dp(8))
        if (selected) item.background = pill(lav, 22)

        val img = ImageView(this)
        img.setImageResource(icon)
        img.setColorFilter(if (selected) accent else mute)
        item.addView(img, LinearLayout.LayoutParams(dp(24), dp(24)))
        item.addView(tv(label, 12f, if (selected) accent else mute, selected), lp(-2, -2, top = 2))

        item.setOnClickListener {
            if (tab != index) {
                tab = index
                render()
            }
        }
        return item
    }

    // ---------------------------------------------------------------- Home

    private fun buildHome(root: LinearLayout) {
        val c = Prefs.color(this)

        // Header
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        val left = LinearLayout(this)
        left.orientation = LinearLayout.VERTICAL
        left.addView(tv(greeting(), 14f, mute))
        val hi = tv("Hi " + Prefs.name(this) + " 👋", 30f, ink, true)
        hi.maxLines = 1
        hi.ellipsize = TextUtils.TruncateAt.END
        left.addView(hi, lp(-2, -2, top = 2))
        left.addView(
            tv(t("Control your camera ring light.", "Atur lampu ring kameramu."), 14f, mute),
            lp(-2, -2, top = 6)
        )
        header.addView(left, lp(0, -2, end = 8, weight = 1f))

        val avatar = FrameLayout(this)
        avatarOuter = View(this)
        val inner = View(this)
        inner.background = circle(Color.parseColor(if (dark) "#1A1838" else "#F4F2FF"))
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
        stats.addView(statTile(R.drawable.ic_palette, blue, blueBg, tileColor, t("Color", "Warna"), 10))
        stats.addView(statTile(R.drawable.ic_bolt, orange, orangeBg, tileShizuku, "Shizuku", 0))
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
        heroSub = tv(
            t("Tap the button to turn the ring on or off.", "Ketuk tombol untuk menyalakan atau mematikan ring."),
            13f, Color.WHITE
        )
        heroText.addView(heroState)
        heroText.addView(heroTitle, lp(-2, -2, top = 8))
        heroText.addView(heroSub, lp(-2, -2, top = 4))
        heroCard.addView(heroText, lp(0, -2, end = 12, weight = 1f))
        val power = ImageView(this)
        power.setImageResource(R.drawable.ic_power)
        power.setColorFilter(accent)
        power.background = circle(Color.WHITE)
        power.setPadding(dp(16), dp(16), dp(16), dp(16))
        power.setOnClickListener { ringSwitch.isChecked = !ringSwitch.isChecked }
        heroCard.addView(power, LinearLayout.LayoutParams(dp(56), dp(56)))
        root.addView(heroCard, lp(-1, -2, top = 16))

        // Color
        section(root, t("Color", "Warna"))
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

        presets.clear()
        val dots = LinearLayout(this)
        dots.orientation = LinearLayout.HORIZONTAL
        val hexes = listOf(
            "#E53935", "#FF7A00", "#FFD600", "#00E676",
            "#00E5FF", "#2979FF", "#B040FF", "#FFFFFF"
        )
        for (h in hexes) {
            val col = Color.parseColor(h)
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
        section(root, t("Controls", "Kontrol"))

        connectBtn = Button(this)
        connectBtn.text = t("Grant Shizuku permission", "Beri izin Shizuku")
        connectBtn.isAllCaps = false
        connectBtn.setTextColor(Color.WHITE)
        connectBtn.background = pill(accent, 22)
        connectBtn.setOnClickListener { requestShizuku() }
        connectBtn.visibility = View.GONE
        root.addView(connectBtn, lp(-1, dp(52), bottom = 10))

        ringSwitch = newSwitch()
        ringSwitch.setOnCheckedChangeListener { _, checked -> if (!guard) onRingToggled(checked) }
        root.addView(
            controlRow(
                R.drawable.ic_power, "RGB ring",
                tv(t("Turn the ring light on or off", "Nyalakan atau matikan lampu ring"), 12f, mute),
                ringSwitch
            ),
            lp(-1, -2, bottom = 10)
        )

        alwaysSwitch = newSwitch()
        alwaysSwitch.setOnCheckedChangeListener { _, checked -> if (!guard) onAlwaysToggled(checked) }
        root.addView(
            controlRow(
                R.drawable.ic_sync, t("RGB always on", "RGB selalu nyala"),
                tv(t("Restarts after reboot (Shizuku)", "Nyala lagi setelah restart (Shizuku)"), 12f, mute),
                alwaysSwitch
            ),
            lp(-1, -2, bottom = 10)
        )

        batterySub = tv("", 12f, mute)
        val batteryRow = controlRow(
            R.drawable.ic_battery, t("Battery optimization", "Optimasi baterai"), batterySub, chevron()
        )
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
        homeReady = true
        updatePreview(c)
        updateStats()
        refreshStatus()
    }

    // ---------------------------------------------------------------- Settings

    private fun buildSettings(root: LinearLayout) {
        root.addView(tv(t("Settings", "Pengaturan"), 30f, ink, true), lp(-2, -2, top = 8))
        root.addView(
            tv(t("Make the app yours.", "Sesuaikan aplikasimu."), 14f, mute),
            lp(-2, -2, top = 6)
        )

        // Profile
        section(root, t("Profile", "Profil"))
        val nameRow = controlRow(
            R.drawable.ic_person, t("Name", "Nama"), tv(Prefs.name(this), 12f, mute), chevron()
        )
        nameRow.setOnClickListener { editName() }
        root.addView(nameRow)

        // Language
        section(root, t("Language", "Bahasa"))
        val langIndex = if (Prefs.lang(this) == "id") 0 else 1
        val langControl = segmented(listOf("Bahasa Indonesia", "English"), langIndex) { i ->
            Prefs.setLang(this, if (i == 0) "id" else "en")
            render()
        }
        root.addView(
            settingCard(
                R.drawable.ic_language, t("App language", "Bahasa aplikasi"),
                t("Choose the language used in the app", "Pilih bahasa yang dipakai di aplikasi"),
                langControl
            )
        )

        // Theme
        section(root, t("Theme", "Tema"))
        val themeOrder = listOf(1, 2, 0)
        val themeControl = segmented(
            listOf(t("Light", "Terang"), t("Dark", "Gelap"), t("System", "Ikuti sistem")),
            themeOrder.indexOf(Prefs.theme(this))
        ) { i ->
            Prefs.setTheme(this, themeOrder[i])
            render()
        }
        root.addView(
            settingCard(
                R.drawable.ic_palette, t("App theme", "Tema aplikasi"),
                t("Light, dark, or follow your phone", "Terang, gelap, atau ikuti HP"),
                themeControl
            )
        )

        val ver = tv("KZEO RGB · v1.0", 12f, mute)
        ver.gravity = Gravity.CENTER
        root.addView(ver, lp(-1, -2, top = 28))
    }

    private fun editName() {
        val et = EditText(this)
        et.setText(Prefs.name(this))
        et.setSelection(et.text.length)
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        et.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(20))
        val box = FrameLayout(this)
        box.setPadding(dp(20), dp(8), dp(20), 0)
        box.addView(et, FrameLayout.LayoutParams(-1, -2))

        val style = if (dark) android.R.style.Theme_Material_Dialog_Alert
        else android.R.style.Theme_Material_Light_Dialog_Alert
        AlertDialog.Builder(this, style)
            .setTitle(t("Your name", "Nama kamu"))
            .setView(box)
            .setPositiveButton(t("Save", "Simpan")) { _, _ ->
                val n = et.text.toString().trim()
                if (n.isNotEmpty()) {
                    Prefs.setName(this, n)
                    render()
                }
            }
            .setNegativeButton(t("Cancel", "Batal"), null)
            .show()
    }

    // ---------------------------------------------------------------- Home state

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
        val fg = if (lum(color) > 0.62f) Color.parseColor("#15162E") else Color.WHITE
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
                if (p.second == color) accent else dotBorder
            )
            p.first.background = d
        }
    }

    private fun updateStats() {
        if (!homeReady) return
        val on = Prefs.ringOn(this)
        tileRing.text = if (on) t("On", "Nyala") else t("Off", "Mati")
        heroState.text = if (on) t("● Ring is on", "● Ring menyala")
        else t("○ Ring is off", "○ Ring mati")
    }

    private fun updateDiag() {
        val err = LightsBinder.lastError ?: LightsBinder.masterError
        if (err != null) {
            diag.text = "Error: $err"
            diag.setTextColor(danger)
        } else if (LightsBinder.lastOkAt > 0) {
            diag.text = t("Last command sent OK", "Perintah terakhir berhasil dikirim")
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
            if (!LightsBinder.shizukuRunning()) t("Start Shizuku first", "Jalankan Shizuku dulu")
            else t("Grant Shizuku permission first", "Beri izin Shizuku dulu")
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
                toast(t("Permission was denied. Allow it in the Shizuku app.", "Izin ditolak. Izinkan lewat aplikasi Shizuku."))
            } else {
                Shizuku.requestPermission(1)
            }
        } catch (e: Throwable) {
            toast(t("Shizuku is not available", "Shizuku tidak tersedia"))
        }
    }

    private fun openBatterySettings() {
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            toast(t("Battery optimization is already disabled", "Optimasi baterai sudah nonaktif"))
            return
        }
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
            )
        } catch (e1: Throwable) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Throwable) {
                toast(t("Could not open battery settings", "Tidak bisa membuka pengaturan baterai"))
            }
        }
    }

    private fun refreshStatus() {
        if (!homeReady) return
        val running = LightsBinder.shizukuRunning()
        val perm = LightsBinder.hasPermission()
        tileShizuku.text = when {
            !running -> "Offline"
            !perm -> t("No access", "Tanpa izin")
            else -> t("Ready", "Siap")
        }
        connectBtn.visibility = if (running && !perm) View.VISIBLE else View.GONE

        val pm = getSystemService(PowerManager::class.java)
        batterySub.text = if (pm.isIgnoringBatteryOptimizations(packageName))
            t("Unrestricted", "Tanpa batasan")
        else t("Tap to disable battery optimization", "Ketuk untuk menonaktifkan optimasi baterai")
        updateStats()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
