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

                handler.postDelayed({
                    when {
                        url?.contains("instagram.com/$targetUsername") == true
                        && url.contains("/p/") == false -> {
                            if (postQueue.isEmpty()) collectPostLinks()
                            else openNextPost()
                        }
                        url?.contains("/p/") == true -> {
                            collectLikesFromPost()
                        }
                    }
                }, 2500)
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

    private fun collectPostLinks() {
        tvProgress.text = getString(R.string.likes_collecting_posts)

        val js = """
            (function() {
                var links = document.querySelectorAll('a[href*="/p/"]');
                var posts = [];
                var seen = {};
                for (var i = 0; i < links.length; i++) {
                    var href = links[i].getAttribute('href');
                    if (href && href.match(/^\/p\/[^\/]+\/?${'$'}/) && !seen[href]) {
                        seen[href] = true;
                        posts.push(href);
                        if (posts.length >= $POSTS_TO_COLLECT) break;
                    }
                }
                Android.onPostsFound(JSON.stringify(posts));
            })()
        """.trimIndent()

        webView.evaluateJavascript(js, null)
    }

    private fun openNextPost() {
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
        tvProgress.text = getString(R.string.likes_collecting_from_post, postsProcessed, totalPosts)

        val js = """
            (function() {
                var likeBtn = null;
                var buttons = document.querySelectorAll('button, a');
                for (var i = 0; i < buttons.length; i++) {
                    var label = buttons[i].getAttribute('aria-label') || '';
                    var text = buttons[i].textContent || '';
                    if (label.toLowerCase().includes('like') ||
                        label.toLowerCase().includes('нравится') ||
                        text.toLowerCase().includes('likes') ||
                        text.toLowerCase().includes('нравится')) {
                        likeBtn = buttons[i];
                        break;
                    }
                }
                if (likeBtn) {
                    likeBtn.click();
                    Android.onLikeButtonClicked('found');
                } else {
                    Android.onLikeButtonClicked('not_found');
                }
            })()
        """.trimIndent()

        webView.evaluateJavascript(js, null)
    }

    private fun scrollAndCollectLikers() {
        handler.postDelayed({
            val js = """
                (function() {
                    var modal = null;
                    var divs = document.querySelectorAll('div[role="dialog"]');
                    for (var i = 0; i < divs.length; i++) {
                        if (divs[i].scrollHeight > divs[i].clientHeight + 10) {
                            modal = divs[i];
                            break;
                        }
                    }

                    if (!modal) {
                        Android.onLikersScroll
