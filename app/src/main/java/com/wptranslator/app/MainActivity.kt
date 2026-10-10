package com.wptranslator.app

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONArray
import org.json.JSONObject

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

private val BLACK = Color.BLACK
private val WHITE = Color.WHITE
private val GRAY_TEXT = 0xFF9A9A9A.toInt()
private val DARK = 0xFF1F1F1F.toInt()
private val BAR = 0xFF1B1B1B.toInt()
private val BLUE = 0xFF263459.toInt()
private val BLUE_SEL = 0xFF4A66B0.toInt()
private val GRAY = 0xFF333333.toInt()
private val GRAY_SEL = 0xFF6B6B6B.toInt()
private val INPUT_BG = 0xFFD0D0D0.toInt()
private val GREEN = 0xFF008A17.toInt()

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var root: FrameLayout
    private lateinit var pageMain: View
    private lateinit var pageLang: FrameLayout
    private lateinit var pageSettings: FrameLayout
    private lateinit var pageIntro: FrameLayout
    private lateinit var drawer: FrameLayout

    private lateinit var input: EditText
    private lateinit var btnSrc: TextView
    private lateinit var btnDst: TextView
    private lateinit var globe: GlobeView
    private lateinit var tvLets: TextView
    private lateinit var resultScroll: ScrollView
    private lateinit var tvSrcText: TextView
    private lateinit var tvDstText: TextView
    private lateinit var tvInfo: TextView

    private var lastTranslation = ""
    private var requestId = 0
    private var current = 0 // 0 main, 1 languages, 2 settings, 3 intro
    private var introStep = 0

    private val micLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val list = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        if (res.resultCode == RESULT_OK && !list.isNullOrEmpty()) {
            input.setText(list[0])
            doTranslate()
        }
    }

    private val camLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) runOcr(bmp)
    }

    // ------------------------------------------------------------------ lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("wpt", MODE_PRIVATE)
        root = FrameLayout(this).apply { setBackgroundColor(BLACK) }
        pageMain = buildMain()
        pageLang = FrameLayout(this)
        pageSettings = FrameLayout(this)
        pageIntro = FrameLayout(this)
        drawer = buildDrawer()
        for (v in listOf(pageMain, pageLang, pageSettings, pageIntro, drawer)) {
            root.addView(v, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        setContentView(root)
        updateLangButtons()
        show(if (prefs.getBoolean("introSeen", false)) 0 else 3)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        when {
            drawer.visibility == View.VISIBLE -> drawer.visibility = View.GONE
            current != 0 -> show(0)
            else -> super.onBackPressed()
        }
    }

    private fun show(page: Int) {
        current = page
        pageMain.visibility = if (page == 0) View.VISIBLE else View.GONE
        pageLang.visibility = if (page == 1) View.VISIBLE else View.GONE
        pageSettings.visibility = if (page == 2) View.VISIBLE else View.GONE
        pageIntro.visibility = if (page == 3) View.VISIBLE else View.GONE
        drawer.visibility = View.GONE
        when (page) {
            1 -> refreshLanguages()
            2 -> refreshSettings()
            3 -> { introStep = 0; refreshIntro() }
        }
        hideKeyboard()
    }

    // ------------------------------------------------------------------ small UI helpers

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

    private fun lpm(w: Int, h: Int, weight: Float, l: Int, t: Int, r: Int, b: Int) =
        LinearLayout.LayoutParams(w, h, weight).apply { setMargins(dp(l), dp(t), dp(r), dp(b)) }

    private fun lightFace() = Typeface.create("sans-serif-light", Typeface.NORMAL)

    private fun pressable(v: View) {
        v.isClickable = true
        v.setOnTouchListener { x, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> x.alpha = 0.6f
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> x.alpha = 1f
            }
            false
        }
    }

    private fun iconView(res: Int, bg: Int, tint: Int = WHITE, onClick: () -> Unit): ImageView =
        ImageView(this).apply {
            setImageResource(res)
            setColorFilter(tint)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(bg)
            pressable(this)
            setOnClickListener { onClick() }
        }

    private fun tile(label: String, bg: Int, sizeSp: Float = 15f, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            setTextColor(WHITE)
            textSize = sizeSp
            gravity = Gravity.CENTER
            setSingleLine(true)
            setBackgroundColor(bg)
            setPadding(dp(8), 0, dp(8), 0)
            pressable(this)
            setOnClickListener { onClick() }
        }

    private fun label(text: String, sizeSp: Float, color: Int = WHITE, light: Boolean = true): TextView =
        TextView(this).apply {
            this.text = text
            textSize = sizeSp
            setTextColor(color)
            if (light) typeface = lightFace()
        }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(root.windowToken, 0)
    }

    private fun showKeyboard() {
        input.requestFocus()
        input.setSelection(input.text.length)
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(input, 0)
    }

    private fun header(title: String, logo: Boolean, search: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(iconView(R.drawable.ic_menu, 0xFF222222.toInt()) { toggleDrawer() }, lp(dp(48), dp(48)))
        if (logo) {
            row.addView(
                iconView(R.drawable.ic_translate, Color.TRANSPARENT) {},
                lpm(dp(40), dp(40), 0f, 8, 0, 0, 0)
            )
        }
        row.addView(label(title, 18f, 0xFFDDDDDD.toInt()), lpm(0, WRAP, 1f, 16, 0, 0, 0))
        if (search) {
            row.addView(
                iconView(R.drawable.ic_search, Color.TRANSPARENT) { showHistory() },
                lpm(dp(48), dp(48), 0f, 0, 0, 8, 0)
            )
        }
        return row
    }

    // ------------------------------------------------------------------ prefs

    private fun srcCode() = prefs.getString("src", "en") ?: "en"
    private fun dstCode() = prefs.getString("dst", "es") ?: "es"
    private fun srcLang(): Lang? = if (srcCode() == "auto") null else Languages.find(srcCode())
    private fun dstLang(): Lang = Languages.find(dstCode()) ?: Languages.all.first()
    private fun engine(): Engine =
        if (prefs.getString("engine", "GOOGLE") == "BING") Engine.BING else Engine.GOOGLE
    private fun engineName() = if (engine() == Engine.BING) "Bing" else "Google"

    private fun nameOf(code: String) =
        if (code == "auto") "AUTO" else (Languages.find(code)?.name ?: code).uppercase()

    private fun recents(key: String): List<String> =
        (prefs.getString(key, "") ?: "").split(",").filter { it.isNotBlank() }

    private fun pushRecent(key: String, code: String) {
        val l = recents(key).toMutableList()
        l.remove(code)
        l.add(0, code)
        prefs.edit().putString(key, l.take(3).joinToString(",")).apply()
    }

    private fun setSrc(code: String) {
        prefs.edit().putString("src", code).apply()
        pushRecent("rSrc", code)
        updateLangButtons()
        refreshLanguages()
    }

    private fun setDst(code: String) {
        prefs.edit().putString("dst", code).apply()
        pushRecent("rDst", code)
        updateLangButtons()
        refreshLanguages()
    }

    private fun setEngine(e: Engine) {
        prefs.edit().putString("engine", e.name).apply()
    }

    private fun updateLangButtons() {
        btnSrc.text = nameOf(srcCode())
        btnDst.text = nameOf(dstCode())
    }

    private fun swapLanguages() {
        val s = srcCode()
        if (s == "auto") {
            toast("Choose a source language first")
            return
        }
        val d = dstCode()
        prefs.edit().putString("src", d).putString("dst", s).apply()
        updateLangButtons()
        if (input.text.isNotBlank()) doTranslate()
    }

    // ------------------------------------------------------------------ main page

    private fun buildMain(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(header("Translator", true, true), lp(MATCH, dp(48)))

        val mid = FrameLayout(this)
        globe = GlobeView(this)
        mid.addView(globe, FrameLayout.LayoutParams(MATCH, MATCH).apply { setMargins(dp(8), dp(8), dp(8), dp(8)) })
        tvLets = label("Let's translate!", 36f).apply { gravity = Gravity.CENTER }
        mid.addView(tvLets, FrameLayout.LayoutParams(MATCH, MATCH))

        resultScroll = ScrollView(this).apply { visibility = View.GONE }
        val rc = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        tvSrcText = label("", 16f, GRAY_TEXT)
        tvDstText = label("", 32f).apply {
            setTextIsSelectable(true)
        }
        tvInfo = label("", 13f, GRAY_TEXT)
        rc.addView(tvSrcText, lp(MATCH, WRAP))
        rc.addView(tvDstText, lpm(MATCH, WRAP, 0f, 0, 20, 0, 0))
        rc.addView(tvInfo, lpm(MATCH, WRAP, 0f, 0, 16, 0, 0))
        resultScroll.addView(rc)
        mid.addView(resultScroll, FrameLayout.LayoutParams(MATCH, MATCH))
        col.addView(mid, lp(MATCH, 0, 1f))

        // input row
        val inRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        input = EditText(this).apply {
            hint = "Tap to enter text"
            setHintTextColor(0xFF666666.toInt())
            setTextColor(Color.BLACK)
            setBackgroundColor(INPUT_BG)
            textSize = 18f
            setPadding(dp(16), 0, dp(8), 0)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    doTranslate()
                    true
                } else false
            }
        }
        inRow.addView(input, lp(0, MATCH, 1f))
        inRow.addView(iconView(R.drawable.ic_mic, INPUT_BG, 0xFF222222.toInt()) { startMic() }, lpm(dp(48), MATCH, 0f, 1, 0, 0, 0))
        inRow.addView(iconView(R.drawable.ic_camera, INPUT_BG, 0xFF222222.toInt()) { camLauncher.launch(null) }, lpm(dp(48), MATCH, 0f, 1, 0, 0, 0))
        col.addView(inRow, lpm(MATCH, dp(48), 0f, 8, 8, 8, 0))

        // language row
        val langRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btnSrc = tile("", BLUE) { show(1) }
        btnDst = tile("", GRAY) { show(1) }
        langRow.addView(btnSrc, lp(0, MATCH, 1f))
        langRow.addView(iconView(R.drawable.ic_swap, 0xFF1F1F1F.toInt()) { swapLanguages() }, lpm(dp(56), MATCH, 0f, 8, 0, 8, 0))
        langRow.addView(btnDst, lp(0, MATCH, 1f))
        col.addView(langRow, lpm(MATCH, dp(48), 0f, 8, 8, 8, 8))

        // bottom app bar
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(BAR)
        }
        val icons = listOf<Pair<Int, () -> Unit>>(
            R.drawable.ic_share to { shareResult() },
            R.drawable.ic_copy to { copyResult() },
            R.drawable.ic_edit to { showKeyboard() },
            R.drawable.ic_delete to { clearAll() },
            R.drawable.ic_more to { }
        )
        for ((i, p) in icons.withIndex()) {
            val v = iconView(p.first, Color.TRANSPARENT, 0xFFB0B0B0.toInt()) {
                if (i == 4) showMore(bar.getChildAt(4)) else p.second()
            }
            bar.addView(v, lp(0, dp(56), 1f))
        }
        col.addView(bar, lp(MATCH, dp(56)))
        return col
    }

    private fun showMore(anchor: View) {
        val pm = PopupMenu(this, anchor)
        pm.menu.add(0, 1, 0, "Languages")
        pm.menu.add(0, 2, 1, "Settings")
        pm.menu.add(0, 3, 2, "Introduction")
        pm.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> show(1)
                2 -> show(2)
                3 -> show(3)
            }
            true
        }
        pm.show()
    }

    private fun showResult(src: String, dst: String, info: String) {
        globe.visibility = View.GONE
        tvLets.visibility = View.GONE
        resultScroll.visibility = View.VISIBLE
        tvSrcText.text = src
        tvDstText.text = dst
        tvInfo.text = info
    }

    private fun clearAll() {
        requestId++
        input.setText("")
        lastTranslation = ""
        resultScroll.visibility = View.GONE
        globe.visibility = View.VISIBLE
        tvLets.visibility = View.VISIBLE
    }

    private fun doTranslate() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        hideKeyboard()
        val eng = engine()
        val from = srcLang()
        val to = dstLang()
        val ticket = ++requestId
        showResult(text, "…", eng.name.lowercase().replaceFirstChar { it.uppercase() })
        Thread {
            try {
                val r = Translator.translate(eng, text.take(2000), from, to)
                runOnUiThread {
                    if (ticket != requestId) return@runOnUiThread
                    lastTranslation = r.text
                    val det = Languages.nameFor(r.detected)
                    val info = engineName() + (if (det != null) " · detected: $det" else "")
                    showResult(text, r.text, info)
                    addHistory(text, r.text)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (ticket != requestId) return@runOnUiThread
                    lastTranslation = ""
                    showResult(text, "Translation failed", "${engineName()} · ${e.message ?: "network error"}")
                }
            }
        }.start()
    }

    private fun copyResult() {
        if (lastTranslation.isEmpty()) return toast("Nothing to copy")
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("translation", lastTranslation))
        toast("Copied")
    }

    private fun shareResult() {
        if (lastTranslation.isEmpty()) return toast("Nothing to share")
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, lastTranslation)
        }
        startActivity(Intent.createChooser(i, "Share translation"))
    }

    // ------------------------------------------------------------------ voice / camera

    private fun startMic() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            srcLang()?.let { putExtra(RecognizerIntent.EXTRA_LANGUAGE, it.google) }
        }
        try {
            micLauncher.launch(i)
        } catch (e: ActivityNotFoundException) {
            toast("Speech recognition not available")
        }
    }

    private fun runOcr(bmp: Bitmap) {
        val rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        rec.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { t ->
                val s = t.text.replace("\n", " ").trim()
                if (s.isEmpty()) {
                    toast("No text found")
                } else {
                    input.setText(s)
                    doTranslate()
                }
            }
            .addOnFailureListener { toast("Could not read text") }
    }

    // ------------------------------------------------------------------ history

    private fun addHistory(s: String, t: String) {
        val old = JSONArray(prefs.getString("history", "[]"))
        val n = JSONArray()
        n.put(JSONObject().put("s", s).put("t", t))
        for (i in 0 until minOf(old.length(), 49)) n.put(old.getJSONObject(i))
        prefs.edit().putString("history", n.toString()).apply()
    }

    private fun showHistory() {
        val arr = JSONArray(prefs.getString("history", "[]"))
        if (arr.length() == 0) return toast("No history yet")
        val items = Array(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            o.getString("s") + "  →  " + o.getString("t")
        }
        AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
            .setTitle("History")
            .setItems(items) { _, i ->
                val o = arr.getJSONObject(i)
                input.setText(o.getString("s"))
                lastTranslation = o.getString("t")
                showResult(o.getString("s"), o.getString("t"), "History")
                show(0)
            }
            .setNegativeButton("Close", null)
            .setNeutralButton("Clear") { _, _ -> prefs.edit().remove("history").apply() }
            .show()
    }

    // ------------------------------------------------------------------ drawer

    private fun buildDrawer(): FrameLayout {
        val d = FrameLayout(this).apply { visibility = View.GONE }
        val scrim = View(this).apply {
            setBackgroundColor(0x99000000.toInt())
            setOnClickListener { d.visibility = View.GONE }
        }
        d.addView(scrim, FrameLayout.LayoutParams(MATCH, MATCH))
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(DARK)
            isClickable = true
        }
        panel.addView(iconView(R.drawable.ic_menu, 0xFF2B2B2B.toInt()) { d.visibility = View.GONE }, lp(dp(48), dp(48)))
        val entries = listOf("Translator", "Languages", "Settings", "Introduction")
        for ((i, name) in entries.withIndex()) {
            val t = label(name, 24f).apply {
                setPadding(dp(20), dp(14), dp(20), dp(14))
                pressable(this)
                setOnClickListener { show(i) }
            }
            panel.addView(t, lp(MATCH, WRAP))
        }
        d.addView(panel, FrameLayout.LayoutParams(dp(280), MATCH))
        return d
    }

    private fun toggleDrawer() {
        drawer.visibility = if (drawer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    // ------------------------------------------------------------------ languages page

    private fun cellsColumn(codes: List<String>, isSrc: Boolean): LinearLayout {
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (code in codes) {
            val sel = if (isSrc) code == srcCode() else code == dstCode()
            val bg = if (isSrc) (if (sel) BLUE_SEL else BLUE) else (if (sel) GRAY_SEL else GRAY)
            c.addView(
                tile(nameOf(code), bg) { if (isSrc) setSrc(code) else setDst(code) },
                lpm(MATCH, dp(48), 0f, 4, 4, 4, 4)
            )
        }
        return c
    }

    private fun refreshLanguages() {
        pageLang.removeAllViews()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(header("Languages", false, false), lp(MATCH, dp(48)))

        // recents panel
        val rec = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(DARK)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val rs = recents("rSrc").ifEmpty { listOf(srcCode()) }
        val rd = recents("rDst").ifEmpty { listOf(dstCode()) }
        rec.addView(cellsColumn(rs, true), lp(0, WRAP, 1f))
        rec.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.ic_history)
                setColorFilter(0xFF555555.toInt())
            },
            lp(dp(56), dp(32))
        )
        rec.addView(cellsColumn(rd, false), lp(0, WRAP, 1f))
        col.addView(rec, lp(MATCH, WRAP))

        // full lists
        val mid = FrameLayout(this)
        val scroll = ScrollView(this)
        val rows = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val srcCodes = listOf("auto") + Languages.all.map { it.google }
        val dstCodes = Languages.all.map { it.google }
        for (i in 0 until maxOf(srcCodes.size, dstCodes.size)) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val sc = srcCodes.getOrNull(i)
            val dc = dstCodes.getOrNull(i)
            if (sc != null) {
                val sel = sc == srcCode()
                row.addView(tile(nameOf(sc), if (sel) BLUE_SEL else BLUE) { setSrc(sc) }, lpm(0, dp(48), 1f, 4, 4, 4, 4))
            } else row.addView(View(this), lp(0, dp(48), 1f))
            row.addView(View(this), lp(dp(48), dp(48)))
            if (dc != null) {
                val sel = dc == dstCode()
                row.addView(tile(nameOf(dc), if (sel) GRAY_SEL else GRAY) { setDst(dc) }, lpm(0, dp(48), 1f, 4, 4, 4, 4))
            } else row.addView(View(this), lp(0, dp(48), 1f))
            rows.addView(row, lp(MATCH, WRAP))
        }
        scroll.addView(rows)
        mid.addView(scroll, FrameLayout.LayoutParams(MATCH, MATCH))
        mid.addView(
            iconView(R.drawable.ic_swap, 0xFF3A3A3A.toInt()) {
                if (srcCode() == "auto") toast("Choose a source language first")
                else {
                    val s = srcCode(); val d = dstCode()
                    prefs.edit().putString("src", d).putString("dst", s).apply()
                    updateLangButtons()
                    refreshLanguages()
                }
            },
            FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER)
        )
        col.addView(mid, lp(MATCH, 0, 1f))

        // bottom: input method + engine
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        left.addView(label("TRANSLATE USING", 14f, GRAY_TEXT, false), lp(WRAP, WRAP))
        val btns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btns.addView(iconView(R.drawable.ic_keyboard, DARK) { show(0); showKeyboard() }, lpm(dp(48), dp(48), 0f, 0, 6, 4, 0))
        btns.addView(iconView(R.drawable.ic_mic, DARK) { show(0); startMic() }, lpm(dp(48), dp(48), 0f, 0, 6, 4, 0))
        btns.addView(iconView(R.drawable.ic_camera, DARK) { show(0); camLauncher.launch(null) }, lpm(dp(48), dp(48), 0f, 0, 6, 4, 0))
        left.addView(btns, lp(WRAP, WRAP))
        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        right.addView(label("ENGINE", 14f, GRAY_TEXT, false).apply { gravity = Gravity.END }, lp(dp(104), WRAP))
        right.addView(
            tile(engineName().uppercase(), BLUE_SEL) {
                setEngine(if (engine() == Engine.BING) Engine.GOOGLE else Engine.BING)
                refreshLanguages()
            },
            lpm(dp(104), dp(48), 0f, 0, 6, 0, 0)
        )
        bottom.addView(left, lp(0, WRAP, 1f))
        bottom.addView(right, lp(WRAP, WRAP))
        col.addView(bottom, lp(MATCH, WRAP))

        pageLang.addView(col, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    // ------------------------------------------------------------------ settings page

    private fun refreshSettings() {
        pageSettings.removeAllViews()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(header("Settings", false, false), lp(MATCH, dp(48)))
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        body.addView(label("TRANSLATION ENGINE", 14f, GRAY_TEXT, false), lp(WRAP, WRAP))
        fun engineTile(e: Engine, title: String, sub: String) {
            val sel = engine() == e
            val t = TextView(this).apply {
                text = "$title\n$sub"
                setTextColor(WHITE)
                textSize = 18f
                typeface = lightFace()
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), 0, dp(16), 0)
                setBackgroundColor(if (sel) BLUE_SEL else BLUE)
                pressable(this)
                setOnClickListener { setEngine(e); refreshSettings() }
            }
            body.addView(t, lpm(MATCH, dp(80), 0f, 0, 8, 0, 0))
        }
        engineTile(Engine.BING, "Bing", "Microsoft Translator")
        engineTile(Engine.GOOGLE, "Google", "Google Translate")
        body.addView(
            label(
                "Both engines use public web endpoints (no API key). They are unofficial and may change or be rate limited.",
                13f, GRAY_TEXT, false
            ),
            lpm(MATCH, WRAP, 0f, 0, 16, 0, 0)
        )
        body.addView(
            tile("CLEAR HISTORY", GRAY) {
                prefs.edit().remove("history").apply()
                toast("History cleared")
            },
            lpm(MATCH, dp(48), 0f, 0, 24, 0, 0)
        )
        col.addView(body, lp(MATCH, WRAP))
        pageSettings.addView(col, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    // ------------------------------------------------------------------ intro page

    private fun refreshIntro() {
        pageIntro.removeAllViews()
        val steps = listOf(
            Pair("Welcome to WpTranslator", "Translate text, speech and photos in a clean Metro-style interface."),
            Pair("Bing or Google", "Pick your translation engine at any time from the Languages screen or Settings."),
            Pair("Ready to go", "Choose your languages, then type, speak or snap a photo to translate.")
        )
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(header("", false, false), lp(MATCH, dp(48)))
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF101010.toInt())
            setPadding(dp(8), dp(16), dp(8), dp(16))
        }
        card.addView(GlobeView(this), lp(MATCH, 0, 1f))
        card.addView(
            label(steps[introStep].first, 30f).apply { gravity = Gravity.CENTER },
            lpm(MATCH, WRAP, 0f, 8, 16, 8, 0)
        )
        card.addView(
            label(steps[introStep].second, 18f, GRAY_TEXT).apply { gravity = Gravity.CENTER },
            lpm(MATCH, WRAP, 0f, 8, 8, 8, 0)
        )
        val btns = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val prev = tile("Previous", 0xFF444444.toInt(), 22f) {
            if (introStep > 0) { introStep--; refreshIntro() }
        }
        if (introStep == 0) {
            prev.setTextColor(GRAY_TEXT)
            prev.alpha = 0.6f
        }
        val last = introStep == steps.size - 1
        val next = tile(if (last) "Start" else "Next", GREEN, 22f) {
            if (last) {
                prefs.edit().putBoolean("introSeen", true).apply()
                show(0)
            } else {
                introStep++
                refreshIntro()
            }
        }
        btns.addView(prev, lpm(dp(140), dp(64), 0f, 0, 0, 6, 0))
        btns.addView(next, lpm(dp(140), dp(64), 0f, 6, 0, 0, 0))
        card.addView(btns, lpm(MATCH, WRAP, 0f, 0, 24, 0, 0))
        col.addView(card, lpm(MATCH, 0, 1f, 16, 8, 16, 16))
        pageIntro.addView(col, FrameLayout.LayoutParams(MATCH, MATCH))
    }
}
