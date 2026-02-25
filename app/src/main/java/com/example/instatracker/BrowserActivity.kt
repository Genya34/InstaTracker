package com.example.instatracker

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JavascriptInterface
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONArray

class BrowserActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var tvTitle: TextView
    private lateinit var tvHint: TextView
    private lateinit var tvCollectHint: TextView
    private var targetUsername = ""
    private var listType = ""

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
        tvCollectHint = findViewById(R.id.tvCollectHint)
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
                val typeText2 = if (listType == "followers") "подписчиков" else "подписок"
                tvTitle.text = "Сбор $typeText2 @$targetUsername"

                if (url?.contains("login") == true) {
                    tvHint.text = "Войдите в свой аккаунт Instagram"
                } else {
                    tvHint.text = "1. Прокрутите список вниз до конца\n" +
                            "2. Нажмите зелёную кнопку"
                }
            }
        }

        webView.webChromeClient = WebChromeClient()

        val path = if (listType == "followers") "followers" else "following"
        val url = "https://www.instagram.com/$targetUsername/$path/"
        webView.loadUrl(url)

        findViewById<FloatingActionButton>(R.id.fabCollect).setOnClickListener {
            collectUsernames()
        }
    }

    private fun collectUsernames() {
        tvTitle.text = "Собираю имена..."
        tvCollectHint.visibility = View.GONE

        val skipList = listOf(
            "explore", "reels", "direct", "accounts", "p",
            "stories", "about", "privacy", "terms", "help",
            "press", "api", "jobs", "nametag", "session",
            "login", "emails", "newsroom", "download", "contact",
            "lite", "directory", "legal", "locations", "tags",
            "tv", targetUsername.lowercase()
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

                    var hasText = false;
                    var spans = links[i].querySelectorAll('span');
                    for (var j = 0; j < spans.length; j++) {
                        if (spans[j].textContent.trim().length > 0) {
                            hasText = true;
                            break;
                        }
                    }
                    if (!hasText && links[i].textContent.trim().length === 0) continue;

                    seen[name] = true;
                    names.push(name);
                }

                Android.receiveUsernames(JSON.stringify(names));
            })()
        """.trimIndent()

        webView.evaluateJavascript(js, null)
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun receiveUsernames(json: String) {
            runOnUiThread {
                try {
                    val array = JSONArray(json)
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
                    val typeText = if (listType == "followers") "подписчиков" else "подписок"
                    tvTitle.text = "Сбор $typeText @$targetUsername"
                }
            }
        }
    }

    private fun showResultDialog(names: List<String>) {
        if (names.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Ничего не найдено")
                .setMessage(
                    "Не удалось собрать имена.\n\n" +
                    "Возможные причины:\n" +
                    "• Вы не вошли в Instagram\n" +
                    "• Список ещё не загрузился\n" +
                    "• Профиль закрытый\n\n" +
                    "Попробуйте:\n" +
                    "1. Войти в аккаунт\n" +
                    "2. Открыть список подписчиков\n" +
                    "3. Прокрутить вниз\n" +
                    "4. Нажать кнопку снова"
                )
                .setPositiveButton("OK", null)
                .show()

            val typeText = if (listType == "followers") "подписчиков" else "подписок"
            tvTitle.text = "Сбор $typeText @$targetUsername"
            tvCollectHint.visibility = View.VISIBLE
            return
        }

        val preview = names.take(15).joinToString("\n") { "@$it" }
        val moreText = if (names.size > 15) "\n\n... и ещё ${names.size - 15}" else ""

        AlertDialog.Builder(this)
            .setTitle("Найдено: ${names.size} имён")
            .setMessage("$preview$moreText")
            .setPositiveButton("Сохранить") { _, _ ->
                val intent = Intent()
                intent.putExtra(EXTRA_RESULT_NAMES, names.joinToString("\n"))
                setResult(RESULT_OK, intent)
                finish()
            }
            .setNeutralButton("Прокрутить ещё") { _, _ ->
                val typeText = if (listType == "followers") "подписчиков" else "подписок"
                tvTitle.text = "Сбор $typeText @$targetUsername"
                tvCollectHint.visibility = View.VISIBLE
                Toast.makeText(this,
                    "Прокрутите список дальше и нажмите кнопку снова",
                    Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Отмена", null)
            .setCancelable(false)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
