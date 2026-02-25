package com.example.instatracker

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class BrowserActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var tvTitle: TextView
    private lateinit var tvHint: TextView
    private lateinit var tvProgress: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnAutoScroll: MaterialButton
    private lateinit var btnCollect: MaterialButton

    private var targetUsername = ""
    private var listType = ""

    private var isAutoScrolling = false
    private var noNewContentCount = 0
    private var lastFoundCount = 0
    private val scrollHandler = Handler(Looper.getMainLooper())

    companion object {
        const val EXTRA_USERNAME = "username"
        const val EXTRA_LIST_TYPE = "listType"
        const val EXTRA_RESULT_NAMES = "resultNames"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)

        targetUsername = intent.getStringExtra(EXTRA_USERNAME) ?: ""
        listType = intent.getStringExtra(EXTRA_LIST_TYPE) ?: "followers"

        tvTitle = findViewById(R.id.tvTitle)
        tvHint = findViewById(R.id.tvHint)
        tvProgress = findViewById(R.id.tvProgress)
        progressBar = findViewById(R.id.progressBar)
        btnAutoScroll = findViewById(R.id.btnAutoScroll)
        btnCollect = findViewById(R.id.btnCollect)
        webView = findViewById(R.id.webView)

        val typeText = if (listType == "followers") "подписчиков" else "подписок"
        tvTitle.text = "Сбор $typeText @$targetUsername"

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Mobile Safari/537.36"
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.addJavascriptInterface(WebAppInterface(), "Android")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                tvTitle.text = "Загрузка..."
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                val t = if (listType == "followers") "подписчиков" else "подписок"
                tvTitle.text = "Сбор $t @$targetUsername"

                if (url?.contains("login") == true) {
                    tvHint.text = "⚠️ Войдите в свой аккаунт Instagram"
                } else {
                    tvHint.text = "Нажмите «Автопрокрутка» или прокрутите вручную"
                }
            }
        }

        webView.webChromeClient = WebChromeClient()

        val path = if (listType == "followers") "followers" else "following"
        webView.loadUrl("https://www.instagram.com/$targetUsername/$path/")

        btnAutoScroll.setOnClickListener {
            if (isAutoScrolling) {
                stopAutoScroll()
            } else {
                startAutoScroll()
            }
        }

        btnCollect.setOnClickListener {
            collectUsernames()
        }
    }

    // ══════════════════════════════════════
    // АВТОПРОКРУТКА
    // ══════════════════════════════════════

    private fun startAutoScroll() {
        isAutoScrolling = true
        noNewContentCount = 0
        lastFoundCount = 0

        btnAutoScroll.text = "⏹ Остановить"
        btnAutoScroll.setBackgroundColor(0xFFEF4444.toInt())
        progressBar.visibility = View.VISIBLE
        tvProgress.text = "⏳ Начинаю прокрутку..."

        doOneScroll()
    }

    private fun stopAutoScroll() {
        isAutoScrolling = false
        scrollHandler.removeCallbacksAndMessages(null)

        btnAutoScroll.text = "🔄 Автопрокрутка"
        btnAutoScroll.setBackgroundColor(0xFF6366F1.toInt())
        progressBar.visibility = View.GONE

        if (lastFoundCount > 0) {
            tvProgress.text = "✅ Найдено ~$lastFoundCount пользователей. Нажмите «Собрать»"
        } else {
            tvProgress.text = "Прокрутка остановлена"
        }
    }

    private fun doOneScroll() {
        if (!isAutoScrolling) return

        val js = """
            (function() {
                var scrollable = null;
                var maxDiff = 0;

                var allDivs = document.querySelectorAll('div');
                for (var i = 0; i < allDivs.length; i++) {
                    var el = allDivs[i];
                    var diff = el.scrollHeight - el.clientHeight;
                    if (diff > 50) {
                        var style = window.getComputedStyle(el);
                        var ov = style.overflowY;
                        if (ov === 'auto' || ov === 'scroll' || ov === 'hidden') {
                            if (diff > maxDiff) {
                                maxDiff = diff;
                                scrollable = el;
                            }
                        }
                    }
                }

                if (!scrollable) {
                    scrollable = document.scrollingElement || document.documentElement;
                }

                var prevHeight = scrollable.scrollHeight;
                scrollable.scrollTo({
                    top: scrollable.scrollHeight,
                    behavior: 'smooth'
                });

                var count = 0;
                var seen = {};
                var links = document.querySelectorAll('a[href]');
                for (var i = 0; i < links.length; i++) {
                    var href = links[i].getAttribute('href');
                    if (!href) continue;
                    var m = href.match(/^\/([a-zA-Z0-9][a-zA-Z0-9._]{0,28})\/?$/);
                    if (m) {
                        var name = m[1].toLowerCase();
                        if (!seen[name]) {
                            seen[name] = true;
                            count++;
                        }
                    }
                }

                setTimeout(function() {
                    var newHeight = scrollable.scrollHeight;
                    var hasMore = newHeight > prevHeight;
                    Android.onScrollResult(hasMore ? 'more' : 'end', count);
                }, 2000);
            })()
        """.trimIndent()

        webView.evaluateJavascript(js, null)
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun onScrollResult(status: String, count: Int) {
            runOnUiThread {
                if (!isAutoScrolling) return@runOnUiThread

                lastFoundCount = count
                tvProgress.text = "⏳ Прокрутка... найдено ~$count пользователей"

                if (status == "more") {
                    noNewContentCount = 0
                    scrollHandler.postDelayed({ doOneScroll() }, 1000)
                } else {
                    noNewContentCount++
                    if (noNewContentCount >= 3) {
                        isAutoScrolling = false
                        progressBar.visibility = View.GONE
                        btnAutoScroll.text = "🔄 Автопрокрутка"
                        btnAutoScroll.setBackgroundColor(0xFF6366F1.toInt())
                        tvProgress.text = "✅ Прокрутка завершена! Найдено ~$count. Собираю..."
                        scrollHandler.postDelayed({ collectUsernames() }, 500)
                    } else {
                        scrollHandler.postDelayed({ doOneScroll() }, 2000)
                    }
                }
            }
        }

        @JavascriptInterface
        fun receiveUsernames(json: String) {
            runOnUiThread {
                try {
                    val array = org.json.JSONArray(json)
                    val names = mutableListOf<String>()
                    for (i in 0 until array.length()) {
                        names.add(array.getString(i))
                    }
                    showResultDialog(names)
                } catch (e: Exception) {
                    Toast.makeText(
                        this@BrowserActivity,
                        "Ошибка: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // ══════════════════════════════════════
    // СБОР ИМЁН
    // ══════════════════════════════════════

    private fun collectUsernames() {
        tvProgress.text = "⏳ Собираю имена..."
        progressBar.visibility = View.VISIBLE

        val skipList = listOf(
            "explore", "reels", "direct", "accounts", "p",
            "stories", "about", "privacy", "terms", "help",
            "press", "api", "jobs", "nametag", "session",
            "login", "emails", "newsroom", "download", "contact",
            "lite", "directory", "legal", "locations", "tags",
            "tv", "reel", "web", "developer", "embed",
            targetUsername.lowercase()
        )
        val skipJson = skipList.joinToString(",") { "'$it'" }

        val js = """
            (function() {
                var names = [];
                var seen = {};
                var skip = [$skipJson];

                var links = document.querySelectorAll('a');
                for (var i = 0; i < links.length; i++) {
                    var href = links[i].getAttribute('href');
                    if (!href) continue;
                    var match = href.match(/^\/([a-zA-Z0-9][a-zA-Z0-9._]{0,28})\/?$/);
                    if (!match) continue;
                    var name = match[1].toLowerCase();
                    if (seen[name] || skip.indexOf(name) !== -1) continue;

                    var hasContent = false;
                    var spans = links[i].querySelectorAll('span, img');
                    if (spans.length > 0) hasContent = true;
                    if (!hasContent && links[i].textContent.trim().length > 0) hasContent = true;
                    if (!hasContent) continue;

                    seen[name] = true;
                    names.push(name);
                }

                Android.receiveUsernames(JSON.stringify(names));
            })()
        """.trimIndent()

        webView.evaluateJavascript(js, null)
    }

    private fun showResultDialog(names: List<String>) {
        progressBar.visibility = View.GONE

        if (names.isEmpty()) {
            tvProgress.text = "❌ Не удалось найти имена"
            AlertDialog.Builder(this)
                .setTitle("Ничего не найдено")
                .setMessage(
                    "Возможные причины:\n\n" +
                    "• Вы не вошли в Instagram\n" +
                    "• Список ещё не загрузился\n" +
                    "• Профиль закрытый\n\n" +
                    "Попробуйте войти в аккаунт и нажать «Автопрокрутка» снова"
                )
                .setPositiveButton("OK", null)
                .show()
            return
        }

        tvProgress.text = "✅ Найдено: ${names.size} пользователей"

        val preview = names.take(20).joinToString("\n") { "  @$it" }
        val moreText = if (names.size > 20) "\n\n  ... и ещё ${names.size - 20}" else ""

        AlertDialog.Builder(this)
            .setTitle("✅ Найдено: ${names.size}")
            .setMessage("$preview$moreText")
            .setPositiveButton("💾 Сохранить") { _, _ ->
                val intent = Intent()
                intent.putExtra(EXTRA_RESULT_NAMES, names.joinToString("\n"))
                setResult(RESULT_OK, intent)
                finish()
            }
            .setNeutralButton("🔄 Прокрутить ещё") { _, _ ->
                tvProgress.text = "Прокрутите список дальше или нажмите «Автопрокрутка»"
            }
            .setNegativeButton("Отмена", null)
            .setCancelable(false)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        scrollHandler.removeCallbacksAndMessages(null)
        isAutoScrolling = false
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isAutoScrolling) {
            stopAutoScroll()
        } else if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
