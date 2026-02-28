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

    // Очередь постов которые нужно обработать
    private val postQueue = mutableListOf<String>()

    // Результаты: postUrl -> список лайкнувших
    private val results = mutableMapOf<String, MutableList<String>>()

    private var currentPostUrl = ""
    private var postsProcessed = 0
    private var totalPosts = 0

    companion object {
        const val EXTRA_USERNAME = "username"
        const val EXTRA_ACCOUNT_ID = "accountId"
        // Сколько последних постов собираем
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

        // Сначала открываем профиль
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

                // Страница загрузилась — запускаем нужный скрипт
                handler.postDelayed({
                    when {
                        // Мы на странице профиля — собираем ссылки на посты
                        url?.contains("instagram.com/$targetUsername") == true
                        && !url.contains("/p/") -> {
                            if (postQueue.isEmpty()) {
                                collectPostLinks()
                            } else {
                                openNextPost()
                            }
                        }
                        // Мы на странице поста — собираем лайки
                        url?.contains("/p/") == true -> {
                            collectLikesFromPost()
                        }
                    }
                }, 2500) // ждём 2.5 секунды чтобы страница успела отрисоваться
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

    // ── Сбор ссылок на посты со страницы профиля ─────────────────────────

    private fun collectPostLinks() {
        tvProgress.text = getString(R.string.likes_collecting_posts)

        val js = """
            (function() {
                var links = document.querySelectorAll('a[href*="/p/"]');
                var posts = [];
                var seen = {};
                for (var i = 0; i < links.length; i++) {
                    var href = links[i].getAttribute('href');
                    if (href && href.match(/^\/p\/[^\/]+\/?$/) && !seen[href]) {
                        seen[href] = true;
                        posts.push(href);
                        if (posts.length >= ${POSTS_TO_COLLECT}) break;
                    }
                }
                Android.onPostsFound(JSON.stringify(posts));
            })()
        """.trimIndent()

        webView.evaluateJavascript(js, null)
    }

    // ── Открываем следующий пост из очереди ───────────────────────────────

    private fun openNextPost() {
        if (postQueue.isEmpty()) {
            finishCollecting()
            return
        }

        currentPostUrl = postQueue.removeAt(0)
        postsProcessed++

        tvProgress.text = getString(
            R.string.likes_progress,
            postsProcessed,
            totalPosts
        )

        webView.loadUrl("https://www.instagram.com$currentPostUrl")
    }

    // ── Собираем лайки на странице поста ──────────────────────────────────

    private fun collectLikesFromPost() {
        tvProgress.text = getString(
            R.string.likes_collecting_from_post,
            postsProcessed,
            totalPosts
        )

        // Ищем кнопку с количеством лайков и кликаем на неё
        val js = """
            (function() {
                // Ищем элемент со счётчиком лайков — обычно это кнопка или ссылка
                // содержащая слова "like", "нравится" или просто число рядом с сердечком
                var likeBtn = null;

                // Способ 1: ищем по aria-label
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

    // ── Прокручиваем список лайкнувших и собираем имена ───────────────────

    private fun scrollAndCollectLikers() {
        handler.postDelayed({
            val js = """
                (function() {
                    // Ищем модальное окно со списком лайкнувших
                    var modal = null;
                    var divs = document.querySelectorAll('div[role="dialog"]');
                    for (var i = 0; i < divs.length; i++) {
                        if (divs[i].scrollHeight > divs[i].clientHeight + 10) {
                            modal = divs[i];
                            break;
                        }
                    }

                    if (!modal) {
                        // Модалка ещё не открылась, попробуем найти список иначе
                        Android.onLikersScrollResult('no_modal', '[]');
                        return;
                    }

                    var prevHeight = modal.scrollHeight;
                    modal.scrollTo({ top: modal.scrollHeight, behavior: 'smooth' });

                    // Собираем имена из модалки
                    var names = [];
                    var seen = {};
                    var links = modal.querySelectorAll('a[href]');
                    for (var i = 0; i < links.length; i++) {
                        var href = links[i].getAttribute('href');
                        if (!href) continue;
                        var m = href.match(/^\/([a-zA-Z0-9][a-zA-Z0-9._]{0,28})\/?$/);
                        if (m) {
                            var name = m[1].toLowerCase();
                            if (!seen[name]) {
                                seen[name] = true;
                                names.push(name);
                            }
                        }
                    }

                    setTimeout(function() {
                        var hasMore = modal.scrollHeight > prevHeight;
                        Android.onLikersScrollResult(
                            hasMore ? 'more' : 'end',
                            JSON.stringify(names)
                        );
                    }, 1500);
                })()
            """.trimIndent()

            webView.evaluateJavascript(js, null)
        }, 1500) // ждём пока модалка откроется
    }

    // ── Завершаем сбор — возвращаем результаты ────────────────────────────

    private fun finishCollecting() {
        isCollecting = false
        handler.removeCallbacksAndMessages(null)

        btnStart.visibility = View.VISIBLE
        btnStop.visibility = View.GONE
        progressBar.visibility = View.GONE

        val totalLikers = results.values.sumOf { it.size }
        tvProgress.text = getString(R.string.likes_done, postsProcessed, totalLikers)
        tvTitle.text = getString(R.string.likes_title, targetUsername)

        // Возвращаем результаты в MainActivity
        val resultIntent = Intent().apply {
            // Передаём как список строк "postUrl|user1,user2,user3"
            val encoded = results.entries.joinToString("\n") { (url, likers) ->
                "$url|${likers.joinToString(",")}"
            }
            putExtra(EXTRA_RESULT, encoded)
            putExtra(EXTRA_ACCOUNT_ID, intent.getLongExtra(EXTRA_ACCOUNT_ID, 0))
        }
        setResult(RESULT_OK, resultIntent)

        // Показываем диалог с предложением сохранить
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

        // Если уже на странице профиля — сразу собираем посты
        // Если нет — загружаем профиль
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

    // ── JS-интерфейс ──────────────────────────────────────────────────────

    inner class JSInterface {

        @JavascriptInterface
        fun onPostsFound(json: String) {
            runOnUiThread {
                if (!isCollecting) return@runOnUiThread

                try {
                    val array = org.json.JSONArray(json)
                    postQueue.clear()
                    for (i in 0 until array.length()) {
                        postQueue.add(array.getString(i))
                    }
                    totalPosts = postQueue.size

                    if (totalPosts == 0) {
                        tvProgress.text = getString(R.string.likes_no_posts)
                        stopCollecting()
                        return@runOnUiThread
                    }

                    tvProgress.text = getString(R.string.likes_found_posts, totalPosts)
                    // Небольшая пауза перед началом обхода
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
                    // Кнопка нажата — ждём открытия модалки и начинаем прокрутку
                    scrollAndCollectLikers()
                } else {
                    // Кнопка не найдена — пропускаем пост
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
                            // Ещё не всё прокрутили — продолжаем
                            tvProgress.text = getString(
                                R.string.likes_collecting_likers,
                                postsProcessed, totalPosts, likers.size
                            )
                            scrollAndCollectLikers()
                        }
                        "no_modal" -> {
                            // Модалка не открылась — пробуем ещё раз через секунду
                            handler.postDelayed({ scrollAndCollectLikers() }, 1000)
                        }
                        else -> {
                            // Прокрутили до конца — идём к следующему посту
                            tvProgress.text = getString(
                                R.string.likes_post_done,
                                postsProcessed, totalPosts, likers.size
                            )
                            handler.postDelayed({
                                // Возвращаемся на профиль для открытия следующего поста
                                webView.loadUrl("https://www.instagram.com/$targetUsername/")
                            }, 800)
                        }
                    }
                } catch (e: Exception) {
                    // Что-то пошло не так — пропускаем пост
                    handler.postDelayed({ openNextPost() }, 1000)
                }
            }
        }
    }

    companion object {
        const val EXTRA_RESULT = "likes_result"
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        isCollecting = false
    }
}
