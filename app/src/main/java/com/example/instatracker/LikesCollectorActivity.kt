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
import kotlinx.coroutines.*

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
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val postQueue = mutableListOf<String>()
    private val results = mutableMapOf<String, MutableList<String>>()

    private var postsProcessed = 0
    private var totalPosts = 0
    private var postSearchAttempts = 0
    private val maxPostSearchAttempts = 5

    private var jsCode = ""
    private var cookies = ""

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
                val isProfile = safeUrl.contains("instagram.com/$targetUsername")

                if (isProfile) {
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

    private fun evalJs(functionCall: String) {
        webView.evaluateJavascript("$jsCode\n$functionCall", null)
    }

    private fun tryCollectPostLinks() {
        if (!isCollecting) return
        tvProgress.text = getString(R.string.likes_collecting_posts)
        evalJs("scrollProfile();")
        handler.postDelayed({ evalJs("collectPostLinks($POSTS_TO_COLLECT);") }, 1500)
    }

    // Получаем shortcode поста из URL вида /p/ABC123/ или /p/ABC123/liked_by/
    private fun shortcodeFromUrl(url: String): String {
        val parts = url.split("/").filter { it.isNotBlank() }
        val pIndex = parts.indexOf("p")
        return if (pIndex >= 0 && pIndex + 1 < parts.size) parts[pIndex + 1] else ""
    }

    // Используем GraphQL API Instagram для получения лайков
    // Работает если пользователь залогинен (есть cookies сессии)
    private fun fetchLikersViaApi(shortcode: String, postUrl: String) {
        if (!isCollecting) return
        tvProgress.text = "Запрос лайков [$postsProcessed/$totalPosts]..."

        // Получаем cookies из WebView для авторизации запроса
        val cookieStr = CookieManager.getInstance()
            .getCookie("https://www.instagram.com") ?: ""

        scope.launch {
            try {
                val likers = withContext(Dispatchers.IO) {
                    fetchLikers(shortcode, cookieStr)
                }

                if (!isCollecting) return@launch

                val list = results.getOrPut(postUrl) { mutableListOf() }
                list.addAll(likers)

                tvProgress.text = "Пост $postsProcessed: ${likers.size} лайков"
                handler.postDelayed({ openNextPost() }, 500)

            } catch (e: Exception) {
                if (!isCollecting) return@launch
                tvProgress.text = "Ошибка поста $postsProcessed: ${e.message?.take(40)}"
                // Продолжаем со следующим постом
                handler.postDelayed({ openNextPost() }, 500)
            }
        }
    }

    private fun fetchLikers(shortcode: String, cookieStr: String): List<String> {
        val names = mutableListOf<String>()
        var endCursor: String? = null
        var hasNextPage = true
        var attempts = 0

        while (hasNextPage && isCollecting && attempts < 10) {
            attempts++

            // GraphQL запрос для получения лайков
            val variables = if (endCursor != null) {
                """{"shortcode":"$shortcode","include_reel":false,"first":24,"after":"$endCursor"}"""
            } else {
                """{"shortcode":"$shortcode","include_reel":false,"first":24}"""
            }

            val encodedVars = java.net.URLEncoder.encode(variables, "UTF-8")
            val url = "https://www.instagram.com/graphql/query/" +
                "?query_hash=d5d763b1e2acf209d62d22d184488e57" +
                "&variables=$encodedVars"

            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Cookie", cookieStr)
                setRequestProperty("X-Requested-With", "XMLHttpRequest")
                setRequestProperty("Referer", "https://www.instagram.com/")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                connectTimeout = 10000
                readTimeout = 10000
            }

            val responseCode = connection.responseCode
            if (responseCode != 200) break

            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            val json = org.json.JSONObject(response)
            val likers = json
                .optJSONObject("data")
                ?.optJSONObject("shortcode_media")
                ?.optJSONObject("edge_liked_by")
                ?: break

            val edges = likers.optJSONArray("edges") ?: break
            for (i in 0 until edges.length()) {
                val node = edges.getJSONObject(i).optJSONObject("node") ?: continue
                val username = node.optString("username")
                if (username.isNotBlank()) names.add(username)
            }

            val pageInfo = likers.optJSONObject("page_info")
            hasNextPage = pageInfo?.optBoolean("has_next_page") == true
            endCursor = pageInfo?.optString("end_cursor")?.takeIf { it.isNotBlank() }

            // Пауза между запросами чтобы не получить бан
            if (hasNextPage) Thread.sleep(800)
        }

        return names
    }

    private fun openNextPost() {
        if (!isCollecting) return
        if (postQueue.isEmpty()) {
            finishCollecting()
            return
        }
        val postUrl = postQueue.removeAt(0)
        postsProcessed++
        tvProgress.text = "[$postsProcessed/$totalPosts] Получаю лайки..."

        val shortcode = shortcodeFromUrl(postUrl)
        if (shortcode.isBlank()) {
            // Не удалось извлечь shortcode — пропускаем
            handler.postDelayed({ openNextPost() }, 300)
            return
        }

        fetchLikersViaApi(shortcode, postUrl)
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
        scope.coroutineContext.cancelChildren()
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
                        postSearchAttempts++
                        if (postSearchAttempts < maxPostSearchAttempts) {
                            tvProgress.text = "Жду постов… попытка $postSearchAttempts"
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
                    tvProgress.text = getString(R.string.status_error, e.message)
                    stopCollecting()
                }
            }
        }

        @JavascriptInterface
        fun onLikeButtonClicked(status: String) {
            // Больше не используется
        }

        @JavascriptInterface
        fun onLikersScrollResult(status: String, json: String) {
            // Больше не используется
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        handler.removeCallbacksAndMessages(null)
        isCollecting = false
    }
}
