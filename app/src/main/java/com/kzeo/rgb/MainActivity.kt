package com.kzeo.rgb

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
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
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
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

    private val accent: Int get() = Color.parseColor(if (dark) "#FF3B3B" else "#D71921")
    private val ink: Int get() = Color.parseColor(if (dark) "#FFFFFF" else "#0B0B0B")
    private val mute: Int get() = Color.parseColor(if (dark) "#9A9A9A" else "#707070")
    private val lav: Int get() = Color.parseColor(if (dark) "#3A1517" else "#FFE5E6")
    private val blue: Int get() = Color.parseColor(if (dark) "#FFFFFF" else "#0B0B0B")
    private val blueBg: Int get() = Color.parseColor(if (dark) "#262626" else "#EBEBEB")
    private val orange: Int get() = Color.parseColor(if (dark) "#FF3B3B" else "#D71921")
    private val orangeBg: Int get() = Color.parseColor(if (dark) "#3A1517" else "#FFE5E6")
    private val trackOff: Int get() = Color.parseColor(if (dark) "#3A3A3A" else "#CFCFCF")
    private val seekBg: Int get() = Color.parseColor(if (dark) "#3A3A3A" else "#DADADA")
    private val dotBorder: Int get() = Color.parseColor(if (dark) "#444444" else "#D9D9D9")
    private val segTrack: Int get() = Color.parseColor(if (dark) "#1E1E1E" else "#EBEBEB")
    private val bgTop: Int get() = Color.parseColor(if (dark) "#000000" else "#FFFFFF")
    private val bgBottom: Int get() = Color.parseColor(if (dark) "#000000" else "#FFFFFF")
    private val navBar: Int get() = Color.parseColor(if (dark) "#161616" else "#FFFFFF")
    private val navStroke: Int get() = Color.parseColor(if (dark) "#2A2A2A" else "#E8E8E8")
    private val danger: Int get() = Color.parseColor("#E5484D")

    // ---------------------------------------------------------------- state

    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() {
            if (homeReady) updateStats()
            if (deviceReady) updateDevice()
            ui.postDelayed(this, 700)
        }
    }

    /** 0 = Home, 1 = Settings. */
    private var tab = 0

    /** True while the Home views below exist and are on screen. */
    private var homeReady = false

    /** True while the Device info views exist and are on screen. */
    private var deviceReady = false

    private lateinit var tileRing: TextView
    private lateinit var tileColor: TextView
    private lateinit var tileShizuku: TextView
    private lateinit var heroCard: LinearLayout
    private lateinit var heroState: TextView
    private lateinit var heroTitle: TextView
    private lateinit var heroSub: TextView
    private lateinit var avatarOuter: View
    private lateinit var connectBtn: Button
    private lateinit var batterySub: TextView
    private lateinit var ringSwitch: Switch
    private lateinit var alwaysSwitch: Switch
    private lateinit var devRing: TextView
    private lateinit var devLight: TextView
    private lateinit var devShizuku: TextView
    private lateinit var seekR: SeekBar
    private lateinit var seekG: SeekBar
    private lateinit var seekB: SeekBar
    private lateinit var valR: TextView
    private lateinit var valG: TextView
    private lateinit var valB: TextView
    private val presets = ArrayList<Pair<View, Int>>()

    private var guard = false
    private var tourActive = false
    private var tourPage = 0

    private class NavItem(val icon: ImageView, val label: TextView, val labelW: Int)

    /** Rounded highlight that slides between tabs; drawn directly so moving it never triggers a layout. */
    private inner class PillView(ctx: Context) : View(ctx) {
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val rect = android.graphics.RectF()
        var l = 0f
        var r = 0f

        fun setColor(c: Int) {
            paint.color = c
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            if (r <= l) return
            rect.set(l, 0f, r, height.toFloat())
            canvas.drawRoundRect(rect, dp(22).toFloat(), dp(22).toFloat(), paint)
        }
    }

    private lateinit var navBarView: FrameLayout
    private lateinit var navPill: PillView
    private val navSel = FloatArray(3)

    private val navItems = ArrayList<NavItem>()
    private var navAnim: ValueAnimator? = null
    private lateinit var contentRoot: LinearLayout
    private lateinit var contentScroll: ScrollView

    private val permListener =
        Shizuku.OnRequestPermissionResultListener { _, _ -> runOnUiThread { refreshStatus() } }
    private val binderListener = Shizuku.OnBinderReceivedListener { runOnUiThread { refreshStatus() } }
    private val deadListener = Shizuku.OnBinderDeadListener { runOnUiThread { refreshStatus() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tab = savedInstanceState?.getInt("tab") ?: 0
        if (savedInstanceState == null) showSplash() else render()

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
        ui.removeCallbacksAndMessages(null)
        navAnim?.cancel()
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
        d.setColor(Color.parseColor(if (dark) "#161616" else "#F4F4F4"))
        d.setStroke(dp(1), Color.parseColor(if (dark) "#262626" else "#E8E8E8"))
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
            val label = tv(options[i], 13f, if (on) bgBottom else ink, on)
            label.gravity = Gravity.CENTER
            label.maxLines = 1
            label.setPadding(dp(4), dp(10), dp(4), dp(10))
            if (on) label.background = pill(ink, 12)
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
        deviceReady = false
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = bgBottom
        var flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        if (!dark) {
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        window.decorView.systemUiVisibility = flags

        if (Prefs.name(this).isBlank()) buildOnboarding()
        else if (tourActive || !Prefs.tourDone(this)) buildTour()
        else buildShell()
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
        badge.setImageResource(R.drawable.ic_logo_mono)
        badge.background = circle(accent)
        badge.setPadding(dp(6), dp(6), dp(6), dp(6))
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
            tourPage = 0
            render()
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
        contentScroll = scroll
        contentRoot = root

        val nav = buildNav()
        val np = lp(-1, dp(64), top = 4, bottom = 10)
        np.marginStart = dp(16)
        np.marginEnd = dp(16)
        shell.addView(nav, np)

        setContentView(shell)
        shell.setOnApplyWindowInsetsListener { _, ins ->
            shell.setPadding(0, ins.systemWindowInsetTop, 0, ins.systemWindowInsetBottom)
            ins
        }

        fillContent()
    }

    /** Builds the current tab's content into the scroll area. */
    private fun fillContent() {
        homeReady = false
        deviceReady = false
        contentRoot.removeAllViews()
        when (tab) {
            0 -> buildHome(contentRoot)
            1 -> buildDevice(contentRoot)
            else -> buildSettings(contentRoot)
        }
    }

    private fun buildNav(): View {
        navItems.clear()
        navHits.clear()
        for (i in 0..2) navSel[i] = if (i == tab) 1f else 0f
        val bar = FrameLayout(this)
        val bg = GradientDrawable()
        bg.cornerRadius = dp(30).toFloat()
        bg.setColor(navBar)
        bg.setStroke(dp(1), navStroke)
        bar.background = bg
        bar.elevation = dp(10).toFloat()
        navBarView = bar

        val pill = PillView(this)
        pill.setColor(lav)
        navPill = pill
        val pp = FrameLayout.LayoutParams(-1, -1)
        pp.topMargin = dp(10)
        pp.bottomMargin = dp(10)
        bar.addView(pill, pp)

        addNavItem(bar, R.drawable.ic_home, t("Home", "Beranda"), 0)
        addNavItem(bar, R.drawable.ic_info, t("Device", "Perangkat"), 1)
        addNavItem(bar, R.drawable.ic_settings, t("Settings", "Pengaturan"), 2)

        bar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> layoutNav() }
        return bar
    }

    private fun addNavItem(bar: FrameLayout, icon: Int, label: String, index: Int) {
        val img = ImageView(this)
        img.setImageResource(icon)
        val ip = FrameLayout.LayoutParams(dp(22), dp(22), Gravity.START or Gravity.CENTER_VERTICAL)
        bar.addView(img, ip)

        val lab = tv(label, 13f, accent, true)
        lab.maxLines = 1
        lab.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val lw = lab.measuredWidth
        bar.addView(lab, FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.CENTER_VERTICAL))

        val hit = View(this)
        hit.setOnClickListener { selectTab(index) }
        hit.tag = index
        bar.addView(hit, FrameLayout.LayoutParams(0, -1, Gravity.START))
        navHits.add(hit)
        navItems.add(NavItem(img, lab, lw))
    }

    private val navHits = ArrayList<View>()

    /** Positions every nav element from navSel using translations only: no layout pass per frame. */
    private fun layoutNav() {
        if (navItems.size < 3 || navBarView.width == 0) return
        val side = dp(10).toFloat()
        val gap = dp(4).toFloat()
        val unit = navBarView.width - 2 * side - 2 * gap
        val extra = 1.2f
        val total = 3f + extra
        var x = side
        var pl = 0f
        var pr = 0f
        val ev = ArgbEvaluator()
        val ink0 = ink
        val acc = accent
        for (i in 0..2) {
            val s = navSel[i].coerceIn(0f, 1f)
            val w = unit * (1f + extra * navSel[i]) / total
            val n = navItems[i]
            val groupW = dp(22) + s * (dp(6) + n.labelW)
            val gx = x + (w - groupW) / 2f
            n.icon.translationX = gx
            n.icon.setColorFilter(ev.evaluate(s, ink0, acc) as Int)
            n.label.translationX = gx + dp(22) + dp(6) * s
            n.label.alpha = s
            val h = navHits[i]
            val third = (navBarView.width / 3f).toInt()
            if (h.layoutParams.width != third) {
                h.layoutParams.width = third
                h.requestLayout()
            }
            h.translationX = third * i.toFloat()
            pl += s * x
            pr += s * (x + w)
            x += w + gap
        }
        navPill.l = pl
        navPill.r = pr
        navPill.invalidate()
    }

    private fun selectTab(idx: Int) {
        if (idx == tab) return
        navAnim?.cancel()
        tab = idx

        val start = navSel.copyOf()
        val anim = ValueAnimator.ofFloat(0f, 1f)
        anim.duration = 300
        anim.interpolator = android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f)
        anim.addUpdateListener { a ->
            val f = a.animatedValue as Float
            for (i in 0..2) {
                val target = if (i == idx) 1f else 0f
                navSel[i] = start[i] + (target - start[i]) * f
            }
            layoutNav()
        }
        navAnim = anim

        // Build the new page first (while hidden) so the heavy work lands before the animation clock starts.
        contentRoot.animate().cancel()
        contentRoot.alpha = 0f
        fillContent()
        contentScroll.scrollTo(0, 0)
        contentRoot.translationY = dp(10).toFloat()
        contentRoot.post {
            if (navAnim === anim) anim.start()
            contentRoot.animate().alpha(1f).translationY(0f).setDuration(220)
                .setInterpolator(DecelerateInterpolator()).start()
        }
    }

    // ---------------------------------------------------------------- Guide tour

    private class TourPage(val icon: Int, val en: String, val id: String, val bodyEn: String, val bodyId: String)

    private val tourPages = listOf(
        TourPage(R.drawable.ic_logo_mono, "Welcome to VALight", "Selamat datang di VALight",
            "Control the RGB ring on the back of your POCO X8 Pro: turn it on or off and pick any color.",
            "Atur ring RGB di belakang POCO X8 Pro: nyalakan atau matikan dan pilih warna sesukamu."),
        TourPage(R.drawable.ic_bolt, "Shizuku is required", "Butuh Shizuku",
            "VALight talks to the ring through Shizuku. Install Shizuku, start it, then tap \"Grant Shizuku permission\" on the Home tab.",
            "VALight mengontrol ring lewat Shizuku. Pasang Shizuku, jalankan, lalu ketuk \"Beri izin Shizuku\" di tab Beranda."),
        TourPage(R.drawable.ic_power, "Ring on / off", "Ring nyala / mati",
            "Use the big power button on the card, or the RGB ring switch under Controls.",
            "Pakai tombol power besar di kartu, atau saklar RGB ring di bagian Kontrol."),
        TourPage(R.drawable.ic_palette, "Pick your color", "Pilih warnamu",
            "Drag the R, G and B sliders or tap a preset. The ring updates instantly.",
            "Geser slider R, G, B atau ketuk warna preset. Ring langsung berubah."),
        TourPage(R.drawable.ic_sync, "RGB always on", "RGB selalu nyala",
            "Keeps the ring lit in the background and after a reboot. Also turn off battery optimization so the system doesn't stop it.",
            "Menjaga ring tetap nyala di latar belakang dan setelah restart. Matikan juga optimasi baterai agar tidak dihentikan sistem."),
        TourPage(R.drawable.ic_info, "Device info", "Info perangkat",
            "The middle tab shows your device, ring support, HyperOS light and Shizuku status.",
            "Tab tengah menampilkan perangkat, dukungan ring, lampu HyperOS, dan status Shizuku."),
        TourPage(R.drawable.ic_settings, "Make it yours", "Sesuaikan",
            "In Settings change your name, language and theme. You can replay this guide there anytime.",
            "Di Pengaturan ubah nama, bahasa, dan tema. Kamu bisa memutar ulang panduan ini kapan saja.")
    )

    private fun finishTour() {
        val first = !Prefs.tourDone(this)
        Prefs.setTourDone(this, true)
        tourActive = false
        render()
        if (first) askNotifications()
    }

    private fun buildTour() {
        val pg = tourPages[tourPage]
        val last = tourPage == tourPages.size - 1

        val frame = FrameLayout(this)
        frame.background = background()
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        frame.addView(col, FrameLayout.LayoutParams(-1, -1))
        frame.setOnApplyWindowInsetsListener { _, ins ->
            col.setPadding(dp(28), ins.systemWindowInsetTop + dp(16), dp(28), ins.systemWindowInsetBottom + dp(24))
            ins
        }

        val skip = tv(t("Skip", "Lewati"), 15f, mute, true)
        skip.setPadding(dp(12), dp(10), dp(12), dp(10))
        skip.visibility = if (last) View.INVISIBLE else View.VISIBLE
        skip.setOnClickListener { finishTour() }
        val sp = lp(-2, -2)
        sp.gravity = Gravity.END
        col.addView(skip, sp)

        val mid = LinearLayout(this)
        mid.orientation = LinearLayout.VERTICAL
        mid.gravity = Gravity.CENTER
        col.addView(mid, lp(-1, 0, weight = 1f))

        val badge = ImageView(this)
        badge.setImageResource(pg.icon)
        badge.background = circle(accent)
        badge.setPadding(dp(8), dp(8), dp(8), dp(8))
        badge.setColorFilter(Color.WHITE)
        mid.addView(badge, LinearLayout.LayoutParams(dp(96), dp(96)))

        val title = tv(t(pg.en, pg.id), 26f, ink, true)
        title.gravity = Gravity.CENTER
        mid.addView(title, lp(-2, -2, top = 32))
        val body = tv(t(pg.bodyEn, pg.bodyId), 16f, mute)
        body.gravity = Gravity.CENTER
        body.setLineSpacing(0f, 1.15f)
        mid.addView(body, lp(-2, -2, top = 14))

        mid.alpha = 0f
        mid.translationX = dp(24).toFloat()
        mid.animate().alpha(1f).translationX(0f).setDuration(260)
            .setInterpolator(DecelerateInterpolator()).start()

        val dots = LinearLayout(this)
        dots.gravity = Gravity.CENTER
        for (i in tourPages.indices) {
            val d = View(this)
            val on = i == tourPage
            d.background = pill(if (on) accent else dotBorder, 4)
            dots.addView(d, lp(dp(if (on) 22 else 8), dp(8)).also {
                it.marginStart = dp(3)
                it.marginEnd = dp(3)
            })
        }
        col.addView(dots, lp(-2, -2, bottom = 22))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        if (tourPage > 0) {
            val back = Button(this)
            back.text = t("Back", "Kembali")
            back.isAllCaps = false
            back.textSize = 16f
            back.setTextColor(ink)
            back.background = pill(blueBg, 22)
            back.setOnClickListener {
                tourPage--
                render()
            }
            row.addView(back, lp(0, dp(54), weight = 1f).also { it.marginEnd = dp(10) })
        }
        val next = Button(this)
        next.text = if (last) t("Get started", "Mulai") else t("Next", "Lanjut")
        next.isAllCaps = false
        next.textSize = 16f
        next.setTextColor(Color.WHITE)
        next.background = pill(accent, 22)
        next.setOnClickListener {
            if (last) finishTour() else {
                tourPage++
                render()
            }
        }
        row.addView(next, lp(0, dp(54), weight = 1f))
        col.addView(row, lp(-1, -2))

        setContentView(frame)
    }

    // ---------------------------------------------------------------- Splash

    /** Shown on every cold start: the logo scales in on white, then fades into the app. */
    private fun showSplash() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val frame = FrameLayout(this)
        frame.setBackgroundColor(Color.WHITE)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER

        val logo = ImageView(this)
        logo.setImageResource(R.drawable.ic_logo)
        col.addView(logo, LinearLayout.LayoutParams(dp(112), dp(112)))
        val name = tv("VALight", 22f, Color.parseColor("#0B0B0B"), true)
        name.letterSpacing = 0.12f
        col.addView(name, lp(-2, -2, top = 12))
        frame.addView(col, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        setContentView(frame)

        logo.alpha = 0f
        logo.scaleX = 0.6f
        logo.scaleY = 0.6f
        name.alpha = 0f
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(520)
            .setInterpolator(OvershootInterpolator(1.2f)).start()
        name.animate().alpha(1f).setStartDelay(250).setDuration(400).start()

        ui.postDelayed({
            frame.animate().alpha(0f).setDuration(220).withEndAction {
                render()
                val content = findViewById<View>(android.R.id.content)
                content.alpha = 0f
                content.animate().alpha(1f).setDuration(240).start()
            }.start()
        }, 1300)
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
        inner.background = circle(bgTop)
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

    // ---------------------------------------------------------------- Device info

    private fun buildDevice(root: LinearLayout) {
        root.addView(tv(t("Device info", "Info perangkat"), 30f, ink, true), lp(-2, -2, top = 8))
        root.addView(
            tv(t("What this phone supports.", "Apa saja yang didukung HP ini."), 14f, mute),
            lp(-2, -2, top = 6, bottom = 20)
        )

        val devCard = LinearLayout(this)
        devCard.orientation = LinearLayout.VERTICAL
        devCard.background = glass(22)
        devCard.setPadding(dp(16), dp(4), dp(16), dp(4))
        devRing = TextView(this)
        devLight = TextView(this)
        devShizuku = TextView(this)
        val devName = TextView(this)
        devName.text = deviceName()
        val devModel = TextView(this)
        devModel.text = Build.MANUFACTURER.replaceFirstChar { ch -> ch.uppercase() } + " " + Build.MODEL
        val devAndroid = TextView(this)
        devAndroid.text = Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"
        infoRow(devCard, t("Device name", "Nama perangkat"), devName, true)
        infoRow(devCard, "Model", devModel, false)
        infoRow(devCard, "RGB ring", devRing, false)
        infoRow(devCard, t("HyperOS light", "Lampu HyperOS"), devLight, false)
        infoRow(devCard, "Shizuku", devShizuku, false)
        infoRow(devCard, t("Android version", "Versi Android"), devAndroid, false)
        root.addView(devCard)

        deviceReady = true
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

        section(root, t("Help", "Bantuan"))
        val guideRow = controlRow(
            R.drawable.ic_info, t("Guide tour", "Panduan aplikasi"), tv("", 12f, mute), chevron()
        )
        guideRow.setOnClickListener {
            tourActive = true
            tourPage = 0
            render()
        }
        root.addView(guideRow)

        val ver = tv(versionName(), 12f, mute)
        ver.gravity = Gravity.CENTER
        root.addView(ver, lp(-1, -2, top = 28))
    }

    private fun versionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
    } catch (e: Throwable) {
        "1.0"
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
                if (p.second == color) ink else dotBorder
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

    // ---------------------------------------------------------------- Device info

    private fun deviceName(): String {
        val n = try {
            Settings.Global.getString(contentResolver, "device_name")
        } catch (e: Throwable) {
            null
        }
        return if (!n.isNullOrBlank()) n else Build.MODEL
    }

    private fun infoRow(card: LinearLayout, label: String, value: TextView, first: Boolean) {
        if (!first) {
            val line = View(this)
            line.setBackgroundColor(dotBorder)
            card.addView(line, LinearLayout.LayoutParams(-1, dp(1)))
        }
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(12), 0, dp(12))
        row.addView(tv(label, 14f, mute), lp(0, -2, end = 12, weight = 1f))
        value.textSize = 14f
        value.setTextColor(ink)
        value.setTypeface(value.typeface, Typeface.BOLD)
        value.gravity = Gravity.END
        row.addView(value, LinearLayout.LayoutParams(-2, -2))
        card.addView(row)
    }

    /** kind: 0 = neutral, 1 = good, 2 = bad. */
    private fun status(view: TextView, text: String, kind: Int) {
        view.text = text
        view.setTextColor(
            when (kind) {
                1 -> Color.parseColor("#1FA971")
                2 -> danger
                else -> ink
            }
        )
    }

    private fun shizukuVersion(): String = try {
        " (v" + Shizuku.getVersion() + ")"
    } catch (e: Throwable) {
        ""
    }

    private fun updateDevice() {
        val svc = LightsBinder.serviceState
        when {
            svc == 1 || LightsBinder.hasMasterKey(this) -> status(devRing, t("Supported", "Didukung"), 1)
            svc == 2 -> status(devRing, t("Not supported", "Tidak didukung"), 2)
            else -> status(devRing, t("Unknown", "Belum diketahui"), 0)
        }
        when (svc) {
            1 -> status(devLight, t("Available", "Tersedia"), 1)
            2 -> status(devLight, t("Unavailable", "Tidak tersedia"), 2)
            else -> status(devLight, t("Needs Shizuku", "Perlu Shizuku"), 0)
        }
        val running = LightsBinder.shizukuRunning()
        when {
            LightsBinder.hasPermission() ->
                status(devShizuku, t("Connected", "Terhubung") + shizukuVersion(), 1)
            running -> status(devShizuku, t("Permission needed", "Perlu izin"), 2)
            else -> status(devShizuku, t("Not running", "Tidak berjalan"), 2)
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
        val running = LightsBinder.shizukuRunning()
        val perm = LightsBinder.hasPermission()
        if (perm) io.execute { LightsBinder.probe() } else LightsBinder.serviceState = 0
        if (deviceReady) updateDevice()
        if (!homeReady) return
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
