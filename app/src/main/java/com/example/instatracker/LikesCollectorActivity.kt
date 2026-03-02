package com.example.instatracker

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
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
        const val TAG = "LikesCollector"
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
                Log.d(TAG, "onPageStarted: $url")
                if (!isCollecting) tvTitle.text = getString(R.string.browser_loading)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                Log.d(TAG, "onPageFinished: $url")

                if (!isCollecting) {
                    tvTitle.text = getString(R.string.likes_title, targetUsername)
                    tvProgress.text = getString(R.string.likes_hint_ready)
                    return
                }

                val safeUrl = url ?: ""
                val isLikedBy = safeUrl.contains("liked_by")
                val isPost = safeUrl.contains("/p/") && !isLikedBy
                val isProfile = safeUrl.contains("instagram.com/$targetUsername") && !isPost && !isLikedBy

                Log.d(TAG, "isLikedBy=$isLikedBy isPost=$isPost isProfile=$isProfile")

                when {
                    isLikedBy -> {
                        // Страница лайков — ждём и собираем
                        tvProgress.text = getString(R.string.likes_collecting_from_post, postsProcessed, totalPosts)
                        handler.postDelayed({ scrollAndCollectLikers() }, 2500)
                    }
                    isPost -> {
                        // Открылся пост вместо liked_by — добавляем /liked_by/ и грузим
                        Log.d(TAG, "Got post page, redirecting to liked_by")
                        val likedByUrl = safeUrl.trimEnd('/') + "/liked_by/"
                        handler.postDelayed({ webView.loadUrl(likedByUrl) }, 1000)
                    }
                    isProfile -> {
                        postSearchAttempts = 0
                        handler.postDelayed({ tryCollectPostLinks() }, 3000)
                    }
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: android.webkit.WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return true
                val host = request.url?.host ?: return true
                Log.d(TAG, "shouldOverrideUrlLoading: $url")
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
        tvProgress.text = getString(R.string.likes_progress, postsProcessed, totalPosts)
        val fullUrl = "https://www.instagram.com$currentPostUrl"
        Log.d(TAG, "Opening: $fullUrl")
        webView.loadUrl(fullUrl)
    }

    private fun scrollAndCollectLikers() {
        if (!isCollecting) return
        Log.d(TAG, "scrollAndCollectLikers, url=${webView.url}")
        evalJs("scrollAndCollectLikers();")
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
        postSearchAttempts = 0

        btnStart.visibility = View.GONE
        btnStop.visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE
        tvProgress.text = getString(R.string.likes_collecting_posts)

        val currentUrl = webView.url ?: ""
        if (currentUrl.contains("instagram.com/$targetUsername")) {
            tryCollectPostLinks()
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
                    Log.d(TAG, "onPostsFound: $json")
                    val array = org.json.JSONArray(json)
                    postQueue.clear()
                    for (i in 0 until array.length()) postQueue.add(array.getString(i))
                    totalPosts = postQueue.size
                    Log.d(TAG, "Found $totalPosts posts")

                    if (totalPosts == 0) {
                        postSearchAttempts++
                        if (postSearchAttempts < maxPostSearchAttempts) {
                            tvProgress.text = "⏳ Жду загрузки постов… (попытка $postSearchAttempts)"
                            handler.postDelayed({ tryCollectPostLinks() }, 2000)
                        } else {
                            tvProgress.text = getString(R.string.likes_no_posts)
                            stopCollecting()
                        }
                        return@runOnUiThread
                    }

                    tvProgress.text = getString(R.string.likes_found_posts, totalPosts)
                    handler.postDelayed({ openNextPost() }, 1000)
                } catch (e: Exception) {
                    Log.e(TAG, "onPostsFound error", e)
                    tvProgress.text = getString(R.string.status_error, e.message)
                    stopCollecting()
                }
            }
        }

        @JavascriptInterface
        fun onLikeButtonClicked(status: String) {
            runOnUiThread {
                if (!isCollecting) return@runOnUiThread
                Log.d(TAG, "onLikeButtonClicked: $status")
                scrollAndCollectLikers()
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
                    Log.d(TAG, "onLikersScrollResult: status=$status collected=${likers.size}")

                    when (status) {
                        "more" -> {
                            tvProgress.text = getString(
                                R.string.likes_collecting_likers,
                                postsProcessed, totalPosts, likers.size
                            )
                            handler.postDelayed({ scrollAndCollectLikers() }, 1000)
                        }
                        else -> {
                            tvProgress.text = getString(
                                R.string.likes_post_done,
                                postsProcessed, totalPosts, likers.size
                            )
                            handler.postDelayed({ openNextPost() }, 800)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "onLikersScrollResult error", e)
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
