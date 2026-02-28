package com.example.instatracker

import android.annotation.SuppressLint
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
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class LikesCollectorActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var tvTitle: TextView
    private lateinit var tvProgress: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnStart: MaterialButton
    private lateinit var btnStop: MaterialButton

    private var targetUsername = ""
    private var isCollecting = false
    private val handler = Handler(Looper.getMainLooper())

    private val postQueue = mutableListOf<String>()
    private val results = mutableMapOf<String, MutableList<String>>()

    private var currentPostUrl = ""
    private var postsProcessed = 0
    private var totalPosts = 0

    // Сколько раз пробовали найти посты на странице профиля
    private var postSearchAttempts = 0
    private val maxPostSearchAttempts = 5

    companion object {
        const val EXTRA_USERNAME = "username"
        const val EXTRA_ACCOUNT_ID = "accountId"
        const val EXTRA_RESULT = "likes_result"
        const val POSTS_TO_COLLECT = 20
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_likes_collector)

        targetUsername = intent.getStringExtra(EXTRA_USERNAME) ?: ""

        tvTitle = findViewById(R.id.tvTitle)
        tvProgress = findViewById(R.id.tvProgress)
        progressBar = findViewById(R.id.progressBar)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        webView = findViewById(R.id.webView)

        findViewById<ImageButton>(R.id.btnClose).setOnClickListener { finish() }

        tvTitle.text = getString(R.string.likes_title, targetUsername)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isCollecting) stopCollecting() else finish()
            }
        })

        setupWebView()
        webView.loadUrl("https://www.instagram.com/$targetUsername/")

        btnStart.setOnClickListener { startCollecting() }
        btnStop.setOnClickListener { stopCollecting() }
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

        webView.addJavascriptInterface(JSInterface(), "Android")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (!isCollecting) tvTitle.text = getString(R.string.browser_loading)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (!isCollecting) {
                    tvTitle.text = getString(R.string.likes_title, targetUsername)
                    tvProgress.text = getString(R.string.likes_hint_ready)
                    return
                }

                val isPost = url?.contains("/p/") == true

                if (isPost) {
                    // Страница поста — ждём подольше и собираем лайки
                    handler.postDelayed({ collectLikesFromPost() }, 3000)
                } else {
                    // Страница профиля — ждём и пробуем найти посты
                    postSearchAttempts = 0
                    handler.postDelayed({ tryCollectPostLinks() }, 3000)
                }
            }

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

    // Пробуем найти посты — если не нашли, прокручиваем и пробуем ещё раз
    private fun tryCollectPostLinks() {
        if (!isCollecting) return

        tvProgress.text = getString(R.string.likes_collecting_posts)

        // Сначала прокручиваем страницу вниз чтобы посты подгрузились
        val scrollJs = buildString {
            append("(function() {")
            append("  window.scrollTo(0, 300);")
            append("  setTimeout(function() { window.scrollTo(0, 0); }, 500);")
            append("})()")
        }
        webView.evaluateJavascript(scrollJs, null)

        // Через секунду после прокрутки ищем посты
        handler.postDelayed({ collectPostLinks() }, 1500)
    }

    private fun collectPostLinks() {
        if (!isCollecting) return

        val maxPosts = POSTS_TO_COLLECT
        val js = buildString {
            append("(function() {")
            append("  var links = document.querySelectorAll('a[href]');")
            append("  var posts = [];")
            append("  var seen = {};")
            append("  for (var i = 0; i < links.length; i++) {")
            append("    var href = links[i].getAttribute('href');")
            append("    if (!href) continue;")
            append("    if (href.indexOf('/p/') !== 0) continue;")
            append("    if (seen[href]) continue;")
            append("    seen[href] = true;")
            append("    posts.push(href);")
            append("    if (posts.length >= $maxPosts) break;")
            append("  }")
            append("  Android.onPostsFound(JSON.stringify(posts));")
            append("})()")
        }
        webView.evaluateJavascript(js, null)
    }

    private fun openNextPost() {
        if (!isCollecting) return
        if (postQueue.isEmpty()) {
            finishCollecting()
            return
        }
        currentPostUrl = postQueue.removeAt(0)
        postsProcessed++
        tvProgress.text = getString(R.string.likes_progress, postsProcessed, totalPosts)
        webView.loadUrl("https://www.instagram.com$currentPostUrl")
    }

    private fun collectLikesFromPost() {
        if (!isCollecting) return
        tvProgress.text = getString(R.string.likes_collecting_from_post, postsProcessed, totalPosts)

        val js = buildString {
            append("(function() {")
            append("  var likeBtn = null;")
            append("  var buttons = document.querySelectorAll('button, a, span');")
            append("  for (var i = 0; i < buttons.length; i++) {")
            append("    var label = (buttons[i].getAttribute('aria-label') || '').toLowerCase();")
            append("    var text = (buttons[i].textContent || '').toLowerCase();")
            append("    if (label.indexOf('like') !== -1 || label.indexOf('нравится') !== -1 ||")
            append("        text.indexOf(' likes') !== -1 || text.indexOf('нравится') !== -1) {")
            append("      likeBtn = buttons[i]; break;")
            append("    }")
            append("  }")
            append("  if (likeBtn) { likeBtn.click(); Android.onLikeButtonClicked('found'); }")
            append("  else { Android.onLikeButtonClicked('not_found'); }")
            append("})()")
        }
        webView.evaluateJavascript(js, null)
    }

    private fun scrollAndCollectLikers() {
        handler.postDelayed({
            if (!isCollecting) return@postDelayed

            val js = buildString {
                append("(function() {")
                append("  var modal = null;")
                append("  var divs = document.querySelectorAll('div[role=\"dialog\"]');")
                append("  for (var i = 0; i < divs.length; i++) {")
                append("    if (divs[i].scrollHeight > divs[i].clientHeight + 10) {")
                append("      modal = divs[i]; break;")
                append("    }")
                append("  }")
                append("  if (!modal) { Android.onLikersScrollResult('no_modal', '[]'); return; }")
                append("  var prevHeight = modal.scrollHeight;")
                append("  modal.scrollTo({ top: modal.scrollHeight, behavior: 'smooth' });")
                append("  var names = []; var seen = {};")
                append("  var links = modal.querySelectorAll('a[href]');")
                append("  for (var i = 0; i < links.length; i++) {")
                append("    var href = links[i].getAttribute('href');")
                append("    if (!href || href.indexOf('/') !== 0) continue;")
                append("    var parts = href.split('/').filter(function(p) { return p.length > 0; });")
                append("    if (parts.length !== 1) continue;")
                append("    var name = parts[0].toLowerCase();")
                append("    if (name.length < 1 || name.length > 30) continue;")
                append("    if (!seen[name]) { seen[name] = true; names.push(name); }")
                append("  }")
                append("  setTimeout(function() {")
                append("    var hasMore = modal.scrollHeight > prevHeight;")
                append("    Android.onLikersScrollResult(hasMore ? 'more' : 'end', JSON.stringify(names));")
                append("  }, 1500);")
                append("})()")
            }
            webView.evaluateJavascript(js, null)
        }, 2000)
    }
