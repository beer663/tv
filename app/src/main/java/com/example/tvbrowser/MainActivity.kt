package com.example.tvbrowser

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.mozilla.geckoview.*

/** On-screen pointer driven by the remote's D-pad. */
class CursorView(c: Context) : View(c) {
    var cx = 400f; var cy = 300f
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 4f }
    override fun onDraw(c: Canvas) {
        val p = Path().apply { moveTo(cx, cy); lineTo(cx, cy + 44f); lineTo(cx + 12f, cy + 34f); lineTo(cx + 32f, cy + 32f); close() }
        c.drawPath(p, fill); c.drawPath(p, edge)
    }
}

class MainActivity : AppCompatActivity() {
    companion object { var runtime: GeckoRuntime? = null }

    private lateinit var gv: GeckoView
    private lateinit var session: GeckoSession
    private lateinit var bar: EditText
    private lateinit var toolbar: View
    private lateinit var stage: FrameLayout
    private lateinit var cursor: CursorView
    private var canBack = false
    private var fullscreen = false
    private var pageUrl = ""
    private var pageTitle = ""
    private var downTime = 0L
    private val prefs by lazy { getSharedPreferences("bookmarks", MODE_PRIVATE) }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_main)
        gv = findViewById(R.id.gecko); bar = findViewById(R.id.url)
        toolbar = findViewById(R.id.toolbar); stage = findViewById(R.id.stage)
        cursor = CursorView(this).apply { visibility = View.GONE; isFocusable = false }
        stage.addView(cursor, FrameLayout.LayoutParams(-1, -1))

        val rt = runtime ?: GeckoRuntime.create(applicationContext, runtimeSettings())
            .also { runtime = it; installUBlock(it) }

        // Desktop-class pages and player, like a PC browser
        session = GeckoSession(GeckoSessionSettings.Builder()
            .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_DESKTOP)
            .viewportMode(GeckoSessionSettings.VIEWPORT_MODE_DESKTOP).build())

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onCanGoBack(s: GeckoSession, v: Boolean) { canBack = v }
            // POPUP BLOCK: every script/ad-opened window or tab is denied.
            override fun onNewSession(s: GeckoSession, uri: String): GeckoResult<GeckoSession>? = null
            // Block app-launching and odd schemes (intent://, market://, ...)
            override fun onLoadRequest(s: GeckoSession, r: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? {
                val sc = Uri.parse(r.uri).scheme
                val ok = sc in listOf("http", "https", "about", "blob", "data", "moz-extension")
                return GeckoResult.fromValue(if (ok) AllowOrDeny.ALLOW else AllowOrDeny.DENY)
            }
        }
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(s: GeckoSession, url: String) { pageUrl = url; bar.setText(url) }
        }
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(s: GeckoSession, t: String?) { pageTitle = t ?: "" }
            override fun onFullScreen(s: GeckoSession, f: Boolean) {
                fullscreen = f
                toolbar.visibility = if (f) View.GONE else View.VISIBLE
                window.decorView.systemUiVisibility = if (f) (View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) else 0
                updateCursor()
            }
        }
        session.open(rt); gv.setSession(session)

        bar.setOnEditorActionListener { _, id, e ->
            if (id == EditorInfo.IME_ACTION_GO || e?.keyCode == KeyEvent.KEYCODE_ENTER) { go(bar.text.toString()); true } else false
        }
        findViewById<Button>(R.id.btnBack).setOnClickListener { if (canBack) session.goBack() }
        findViewById<Button>(R.id.btnReload).setOnClickListener { session.reload() }
        findViewById<Button>(R.id.btnStar).setOnClickListener { addBookmark() }
        findViewById<Button>(R.id.btnMarks).setOnClickListener { showBookmarks() }
        go("https://www.google.com")
        toolbar.visibility = View.VISIBLE; bar.requestFocus()
    }

    // Firefox's built-in blocking, layered under uBlock Origin. Ads/analytics/social trackers on,
    // but NOT "strict" content blocking, which can break video players on some streaming sites.
    private fun runtimeSettings(): GeckoRuntimeSettings {
        val cb = ContentBlocking.Settings.Builder()
            .antiTracking(ContentBlocking.AntiTracking.AD or ContentBlocking.AntiTracking.ANALYTIC or ContentBlocking.AntiTracking.SOCIAL)
            .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
            .build()
        return GeckoRuntimeSettings.Builder().contentBlocking(cb).build()
    }

    // ---- uBlock Origin (real extension) installed from addons.mozilla.org on first run ----
    private fun installUBlock(rt: GeckoRuntime) {
        val c = rt.webExtensionController
        // NOTE: newer GeckoView versions may rename this callback (onInstallPrompt). Adjust if the compiler says so.
        c.setPromptDelegate(object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(ext: WebExtension, perms: Array<String>, origins: Array<String>): GeckoResult<AllowOrDeny>? =
                GeckoResult.fromValue(AllowOrDeny.ALLOW)
        })
        c.list().accept { list ->
            if (list == null || list.none { it.id == "uBlock0@raymondhill.net" })
                c.install("https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi")
        }
    }

    // ---- navigation / search ----
    private fun go(input: String) {
        val t = input.trim(); if (t.isEmpty()) return
        session.loadUri(when {
            t.contains("://") -> t
            t.contains(".") && !t.contains(" ") -> "https://$t"
            else -> "https://www.google.com/search?q=" + Uri.encode(t)
        })
        toolbar.visibility = View.GONE; updateCursor(); gv.requestFocus()
    }

    // ---- bookmarks ----
    private fun marks() = prefs.getStringSet("b", emptySet())!!.toList().sorted()
    private fun addBookmark() {
        if (pageUrl.isEmpty()) return
        val e = (pageTitle.ifEmpty { Uri.parse(pageUrl).host ?: pageUrl }).replace('\t', ' ') + "\t" + pageUrl
        prefs.edit().putStringSet("b", marks().toSet() + e).apply()
        Toast.makeText(this, "Bookmarked", Toast.LENGTH_SHORT).show()
    }
    private fun pick(title: String, onPick: (String) -> Unit, neutral: String? = null, onNeutral: () -> Unit = {}) {
        val m = marks()
        val d = AlertDialog.Builder(this).setTitle(title)
            .setItems(m.map { it.substringBefore('\t') }.toTypedArray()) { _, i -> onPick(m[i]) }
        if (neutral != null) d.setNeutralButton(neutral) { _, _ -> onNeutral() }
        d.setNegativeButton("Close", null).show()
    }
    private fun showBookmarks() = pick("Bookmarks", { go(it.substringAfter('\t')) }, "Delete...") {
        pick("Select one to delete", { e -> prefs.edit().putStringSet("b", marks().toSet() - e).apply() })
    }

    // ---- D-pad pointer + remote keys ----
    private fun cursorMode() = toolbar.visibility != View.VISIBLE
    private fun updateCursor() { cursor.visibility = if (cursorMode()) View.VISIBLE else View.GONE; cursor.invalidate() }

    private fun toggleToolbar() {
        if (fullscreen) return
        if (cursorMode()) { toolbar.visibility = View.VISIBLE; bar.requestFocus() }
        else { toolbar.visibility = View.GONE; gv.requestFocus() }
        updateCursor()
    }

    private fun handleBack() = when {
        fullscreen -> session.exitFullScreen()
        !cursorMode() -> { toolbar.visibility = View.GONE; updateCursor() }
        canBack -> session.goBack()
        else -> finish()
    }

    private fun tap(action: Int) {
        val now = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) downTime = now
        val ev = MotionEvent.obtain(downTime, now, action, cursor.cx, cursor.cy, 0)
        ev.source = InputDevice.SOURCE_TOUCHSCREEN
        gv.dispatchTouchEvent(ev); ev.recycle()
    }

    private fun move(dx: Float, dy: Float) {
        val w = stage.width.toFloat(); val h = stage.height.toFloat()
        val nx = (cursor.cx + dx).coerceIn(0f, w - 2); val ny = (cursor.cy + dy).coerceIn(0f, h - 2)
        // Pushing against the top/bottom edge scrolls the page
        if (dy != 0f && ny == cursor.cy)
            session.panZoomController.scrollBy(ScreenLength.fromPixels(0.0), ScreenLength.fromPixels(dy * 4.0))
        cursor.cx = nx; cursor.cy = ny; cursor.invalidate()
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val down = e.action == KeyEvent.ACTION_DOWN
        when (e.keyCode) {
            KeyEvent.KEYCODE_MENU -> { if (down && e.repeatCount == 0) toggleToolbar(); return true }
            KeyEvent.KEYCODE_BACK -> { if (down && e.repeatCount == 0) handleBack(); return true }
        }
        if (cursorMode()) {
            val step = 14f + minOf(e.repeatCount * 4, 50)
            when (e.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (down) {
                        cursor.visibility = View.VISIBLE
                        when (e.keyCode) {
                            KeyEvent.KEYCODE_DPAD_UP -> move(0f, -step); KeyEvent.KEYCODE_DPAD_DOWN -> move(0f, step)
                            KeyEvent.KEYCODE_DPAD_LEFT -> move(-step, 0f); else -> move(step, 0f)
                        }
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (e.repeatCount == 0) tap(if (down) MotionEvent.ACTION_DOWN else MotionEvent.ACTION_UP)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(e)
    }

    // A real USB/Bluetooth mouse works natively; hide the virtual pointer when one is used
    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (ev.isFromSource(InputDevice.SOURCE_MOUSE) && cursor.visibility == View.VISIBLE) cursor.visibility = View.GONE
        return super.dispatchGenericMotionEvent(ev)
    }
}
