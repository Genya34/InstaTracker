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
import androidx.activity.OnBackPressedCallback
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
        findViewById<android.widget.ImageButton>(R.id.btnClose).setOnClickListener { finish() }

        val typeText = if (listType == "followers")
            getString(R.string.browser_type_followers)
        else
            getString(R.string.browser_type_following)

        tvTitle.text = getString(R.string.browser_title_collecting, typeText, targetUsername)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    isAutoScrolling -> stopAutoScroll()
                    webView.canGoBack() -> webView.goBack()
                    else -> finish()
                }
            }
        })

        setupWebView()

        val path = if (listType == "followers") "followers" else "following"
        webView.loadUrl("https://www.instagram.com/$targetUsername/$path/")

        btnAutoScroll.setOnClickListener { if (isAutoScrolling) stopAutoScroll() else startAutoScroll() }
        btnCollect.setOnClickListener { collectUsernames() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
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

        val typeText = if (listType == "followers")
            getString(R.string.browser_type_followers)
        else
            getString(R.string.browser_type_following)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                tvTitle.text = getString(R.string.browser_loading)
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                tvTitle.text = getString(R.string.browser_title_collecting, typeText, targetUsername)
                tvHint.text = if (url?.contains("login") == true)
                    getString(R.string.browser_hint_login)
                else
                    getString(R.string.browser_hint_ready)
            }
            // Блокируем переход на сторонние сайты — защита от XSS
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: android.webkit.WebResourceRequest?
            ): Boolean {
                val host = request?.url?.host ?: return true
                return !host.endsWith("instagram.com")
            }
        }

        webView.webChromeClient = WebChromeClient()
    }

    private fun startAutoScroll() {
        isAutoScrolling = true
        noNewContentCount = 0
        lastFoundCount = 0
        btnAutoScroll.text = getString(R.string.btn_stop_scroll)
        btnAutoScroll.setBackgroundColor(getColor(R.color.colorDanger))
        progressBar.visibility = View.VISIBLE
        tvProgress.text = getString(R.string.browser_scroll_start)
        doOneScroll()
    }

    private fun stopAutoScroll() {
        isAutoScrolling = false
        scrollHandler.removeCallbacksAndMessages(null)
        btnAutoScroll.text = getString(R.string.btn_auto_scroll)
        btnAutoScroll.setBackgroundColor(getColor(R.color.colorAutoScroll))
        progressBar.visibility = View.GONE
        tvProgress.text = if (lastFoundCount > 0)
            getString(R.string.browser_scroll_done, lastFoundCount)
        else
            getString(R.string.browser_scroll_stopped)
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
                            if (diff > maxDiff) { maxDiff = diff; scrollable = el; }
                        }
                    }
                }
                if (!scrollable) scrollable = document.scrollingElement || document.documentElement;
                var prevHeight = scrollable.scrollHeight;
                scrollable.scrollTo({ top: scrollable.scrollHeight, behavior: 'smooth' });
                var count = 0;
                var seen = {};
                var links = document.querySelectorAll('a[href]');
                for (var i = 0; i < links.length; i++) {
                    var href = links[i].getAttribute('href');
                    if (!href) continue;
                    var m = href.match(/^\/([a-zA-Z0-9][a-zA-Z0-9._]{0,28})\/?$/);
                    if (m) { var name = m[1].toLowerCase(); if (!seen[name]) { seen[name] = true; count++; } }
                }
                setTimeout(function() {
                    Android.onScrollResult(scrollable.scrollHeight > prevHeight ? 'more' : 'end', count);
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
                tvProgress.text = getString(R.string.browser_scrolling, count)
                if (status == "more") {
                    noNewContentCount = 0
                    scrollHandler.postDelayed({ doOneScroll() }, 1000)
                } else {
                    noNewContentCount++
                    if (noNewContentCount >= 3) {
                        isAutoScrolling = false
                        progressBar.visibility = View.GONE
                        btnAutoScroll.text = getString(R.string.btn_auto_scroll)
                        btnAutoScroll.setBackgroundColor(getColor(R.color.colorAutoScroll))
                        tvProgress.text = getString(R.string.browser_scroll_done_collect, count)
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
                    for (i in 0 until array.length()) names.add(array.getString(i))
                    showResultDialog(names)
                } catch (e: Exception) {
                    Toast.makeText(this@BrowserActivity,
                        getString(R.string.browser_error, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun collectUsernames() {
        tvProgress.text = getString(R.string.browser_collecting)
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
            tvProgress.text = getString(R.string.browser_not_found_progress)
            AlertDialog.Builder(this)
                .setTitle(R.string.browser_not_found_title)
                .setMessage(R.string.browser_not_found_message)
                .setPositiveButton(R.string.btn_ok, null)
                .show()
            return
        }
        tvProgress.text = getString(R.string.browser_found, names.size)
        val preview = names.take(20).joinToString("\n") { "  @$it" }
        val moreText = if (names.size > 20)
            "\n\n  " + getString(R.string.browser_result_more, names.size - 20)
        else ""
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.browser_result_title, names.size))
            .setMessage("$preview$moreText")
            .setPositiveButton(R.string.btn_save) { _, _ ->
                setResult(RESULT_OK, Intent().apply {
                    putExtra(EXTRA_RESULT_NAMES, names.joinToString("\n"))
                })
                finish()
            }
            .setNeutralButton(R.string.btn_scroll_more) { _, _ ->
                tvProgress.text = getString(R.string.browser_hint_ready)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .setCancelable(false)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        scrollHandler.removeCallbacksAndMessages(null)
        isAutoScrolling = false
    }
}
