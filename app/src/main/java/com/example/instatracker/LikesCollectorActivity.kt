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
                    val isProfile = url?.contains("instagram.com/$targetUsername") == true
                        && url.contains("/p/") == false
                    val isPost = url?.contains("/p/") == true
                    when {
                        isProfile -> if (postQueue.isEmpty()) collectPostLinks() else openNextPost()
                        isPost -> collectLikesFromPost()
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
        val maxPosts = POSTS_TO_COLLECT
        val js = buildString {
            append("(function() {")
            append("var links = document.querySelectorAll('a[href]');")
            append("var posts = [];")
            append("var seen = {};")
            append("for (var i = 0; i < links.length; i++) {")
            append("  var href = links[i].getAttribute('href');")
            append("  if (!href) continue;")
            append("  if (href.indexOf('/p/') !== 0) continue;")
            append("  if (seen[href]) continue;")
            append("  seen[href] = true;")
            append("  posts.push(href);")
            append("  if (posts.length >= $maxPosts) break;")
            append("}")
            append("Android.onPostsFound(JSON.stringify(posts));")
            append("})()")
        }
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
        val js = buildString {
            append("(function() {")
            append("var likeBtn = null;")
            append("var buttons = document.querySelectorAll('button, a');")
            append("for (var i = 0; i < buttons.length; i++) {")
            append("  var label = (buttons[i].getAttribute('aria-label') || '').toLowerCase();")
            append("  var text = (buttons[i].textContent || '').toLowerCase();")
            append("  if (label.indexOf('like') !== -1 || label.indexOf('нравится') !== -1 ||")
            append("      text.indexOf('likes') !== -1 || text.indexOf('нравится') !== -1) {")
            append("    likeBtn = buttons[i]; break;")
            append("  }")
            append("}")
            append("if (likeBtn) { likeBtn.click(); Android.onLikeButtonClicked('found'); }")
            append("else { Android.onLikeButtonClicked('not_found'); }")
            append("})()")
        }
        webView.evaluateJavascript(js, null)
    }

    private fun scrollAndCollectLikers() {
        handler.postDelayed({
            val js = buildString {
                append("(function() {")
                append("var modal = null;")
                append("var divs = document.querySelectorAll('div[role=\"dialog\"]');")
                append("for (var i = 0; i < divs.length; i++) {")
                append("  if (divs[i].scrollHeight > divs[i].clientHeight + 10) {")
                append("    modal = divs[i]; break;")
                append("  }")
                append("}")
                append("if (!modal) { Android.onLikersScrollResult('no_modal', '[]'); return; }")
                append("var prevHeight = modal.scrollHeight;")
                append("modal.scrollTo({ top: modal.scrollHeight, behavior: 'smooth' });")
                append("var names = []; var seen = {};")
                append("var links = modal.querySelectorAll('a[href]');")
                append("for (var i = 0; i < links.length; i++) {")
                append("  var href = links[i].getAttribute('href');")
                append("  if (!href || href.indexOf('/') !== 0) continue;")
                append("  var parts = href.split('/').filter(function(p) { return p.length > 0; });")
                append("  if (parts.length !== 1) continue;")
                append("  var name = parts[0].toLowerCase();")
                append("  if (name.length < 1 || name.length > 30) continue;")
                append("  if (!seen[name]) { seen[name] = true; names.push(name); }")
                append("}")
                append("setTimeout(function() {")
                append("  var hasMore = modal.scrollHeight > prevHeight;")
                append("  Android.onLikersScrollResult(hasMore ? 'more' : 'end', JSON.stringify(names));")
                append("}, 1500);")
                append("})()")
            }
            webView.evaluateJavascript(js, null)
        }, 1500)
    }

    private fun finishCollecting() {
        isCollecting = false
        handler.removeCallbacksAndMessages(null)

        btnStart.visibility = View.VISIBLE
        btnStop.visibility = View.GONE
        progressBar.visibility = View.GONE

        val totalLikers = results.values.sumOf { it.size }
        tvProgress.text = getString(R.string.likes_done, postsProcessed, totalLikers)
        tvTitle.text = getString(R.string.likes_title, targetUsername)

        val encoded = results.entries.joinToString("\n") { (url, likers) ->
            "$url|${likers.joinToString(",")}"
        }
        setResult(RESULT_OK, Intent().apply {
            putExtra(EXTRA_RESULT, encoded)
            putExtra(EXTRA_ACCOUNT_ID, intent.getLongExtra(EXTRA_ACCOUNT_ID, 0))
        })

        android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.likes_done_title, postsProcessed))
            .setMessage(getString(R.string.likes_done_message, totalLikers))
            .setPositiveButton(getString(R.string.btn_save)) { _, _ -> finish() }
            .setNegativeButton(getString(R.string.btn_cancel)) { _, _ -> }
            .setCancelable(false)
            .show()
    }

    private fun startCollecting() {
        isCollecting = true
        postQueue.clear()
        results.clear()
        postsProcessed = 0
        totalPosts = 0

        btnStart.visibility = View.GONE
        btnStop.visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE

        tvProgress.text = getString(R.string.likes_collecting_posts)

        val currentUrl = webView.url ?: ""
        if (currentUrl.contains("instagram.com/$targetUsername")) {
            collectPostLinks()
        } else {
            webView.loadUrl("https://www.instagram.com/$targetUsername/")
        }
    }

    private fun stopCollecting() {
        isCollecting = false
        handler.removeCallbacksAndMessages(null)

        btnStart.visibility = View.VISIBLE
        btnStop.visibility = View.GONE
        progressBar.visibility = View.GONE

        tvProgress.text = getString(R.string.likes_stopped, postsProcessed)
    }

    inner class JSInterface {

        @JavascriptInterface
        fun onPostsFound(json: String) {
            runOnUiThread {
                if (!isCollecting) return@runOnUiThread
                try {
                    val array = org.json.JSONArray(json)
                    postQueue.clear()
                    for (i in 0 until array.length()) postQueue.add(array.getString(i))
                    totalPosts = postQueue.size

                    if (totalPosts == 0) {
                        tvProgress.text = getString(R.string.likes_no_posts)
                        stopCollecting()
                        return@runOnUiThread
                    }

                    tvProgress.text = getString(R.string.likes_found_posts, totalPosts)
                    handler.postDelayed({ openNextPost() }, 1000)
                } catch (e: Exception) {
                    tvProgress.text = getString(R.string.status_error, e.message)
                    stopCollecting()
                }
            }
        }

        @JavascriptInterface
        fun onLikeButtonClicked(status: String) {
            runOnUiThread {
                if (!isCollecting) return@runOnUiThread
                if (status == "found") {
                    scrollAndCollectLikers()
                } else {
                    tvProgress.text = getString(R.string.likes_skip_post, postsProcessed, totalPosts)
                    results[currentPostUrl] = mutableListOf()
                    handler.postDelayed({ openNextPost() }, 1000)
                }
            }
        }

        @JavascriptInterface
        fun onLikersScrollResult(status: String, json: String) {
            runOnUiThread {
                if (!isCollecting) return@runOnUiThread
                try {
                    val array = org.json.JSONArray(json)
                    val likers = results.getOrPut(currentPostUrl) { mutableListOf() }
                    for (i in 0 until array.length()) {
                        val name = array.getString(i)
                        if (!likers.contains(name)) likers.add(name)
                    }

                    when (status) {
                        "more" -> {
                            tvProgress.text = getString(
                                R.string.likes_collecting_likers,
                                postsProcessed, totalPosts, likers.size
                            )
                            scrollAndCollectLikers()
                        }
                        "no_modal" -> {
                            handler.postDelayed({ scrollAndCollectLikers() }, 1000)
                        }
                        else -> {
                            tvProgress.text = getString(
                                R.string.likes_post_done,
                                postsProcessed, totalPosts, likers.size
                            )
                            handler.postDelayed({
                                webView.loadUrl("https://www.instagram.com/$targetUsername/")
                            }, 800)
                        }
                    }
                } catch (e: Exception) {
                    handler.postDelayed({ openNextPost() }, 1000)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        isCollecting = false
    }
}
