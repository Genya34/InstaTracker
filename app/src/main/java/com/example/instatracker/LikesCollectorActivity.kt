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
    private var postSearchAttempts = 0
    private val maxPostSearchAttempts = 5

    private var jsCode = ""

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
        jsCode = assets.open("likes_collector.js").bufferedReader().readText()

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

                val safeUrl = url ?: ""
                val isLikedBy = safeUrl.contains("liked_by")
                val isPost = safeUrl.contains("/p/") && !isLikedBy
                val isProfile = safeUrl.contains("instagram.com/$targetUsername")
                    && !isPost && !isLikedBy

                // Отладочная информация прямо на экране
                tvProgress.text = "URL: ...${safeUrl.takeLast(40)}\n" +
                    "liked=$isLikedBy post=$isPost profile=$isProfile"

                when {
                    isLikedBy -> {
                        handler.postDelayed({ scrollAndCollectLikers() }, 2500)
                    }
                    isPost -> {
                        // Открылся пост — добавляем /liked_by/ и перегружаем
                        val likedByUrl = safeUrl.trimEnd('/') + "/liked_by/"
                        tvProgress.text = "→ liked_by: ...${likedByUrl.takeLast(40)}"
                        handler.postDelayed({ webView.loadUrl(likedByUrl) }, 1000)
                    }
                    isProfile -> {
                        postSearchAttempts = 0
                        handler.postDelayed({ tryCollectPostLinks() }, 3000)
                    }
                    else -> {
                        tvProgress.text = "⚠️ Неизвестный URL:\n${safeUrl.takeLast(60)}"
                    }
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

    private fun evalJs(functionCall: String) {
        webView.evaluateJavascript("$jsCode\n$functionCall", null)
    }

    private fun tryCollectPostLinks() {
        if (!isCollecting) return
        tvProgress.text = getString(R.string.likes_collecting_posts)
        evalJs("scrollProfile();")
        handler.postDelayed({ evalJs("collectPostLinks($POSTS_TO_COLLECT);") }, 1500)
    }

    private fun openNextPost() {
        if (!isCollecting) return
        if (postQueue.isEmpty()) { finishCollecting(); return }
        currentPostUrl = postQueue.removeAt(0)
        postsProcessed++
        val fullUrl = "https://www.instagram.com$currentPostUrl"
        tvProgress.text = "[$postsProcessed/$totalPosts] Открываю:\n$currentPostUrl"
        webView.loadUrl(fullUrl)
    }

    private fun scrollAndCollectLikers() {
        if (!isCollecting) return
        tvProgress.text = "Собираю лайки [$postsProcessed/$totalPosts]..."
        evalJs("scrollAndCollectLikers();")
    }

    priva
