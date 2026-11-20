package com.example.webviewshell

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView

/**
 * 网页套壳主界面：全屏 WebView + 顶部进度条 + 加载失败重试页。
 * 只做「纯展示」，不申请任何敏感权限，也不提供 JS 与原生互调接口。
 */
class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorView: View
    private lateinit var errorMessage: TextView
    private var lastUrl: String = BuildConfig.SHELL_URL
    private var httpFallbackTried = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        httpFallbackTried = savedInstanceState?.getBoolean(KEY_HTTP_FALLBACK, false) ?: false

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        errorView = findViewById(R.id.errorView)
        errorMessage = findViewById(R.id.errorMessage)
        findViewById<Button>(R.id.retryButton).setOnClickListener { load(lastUrl) }

        setupWebView()

        // 进程被系统回收后恢复浏览历史与当前页面，避免重新跳回首页
        val restored = savedInstanceState?.let { webView.restoreState(it) }
        if (restored == null) {
            load(BuildConfig.SHELL_URL)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            // 现代网页普遍依赖 JS，不开会白屏；本工程不加载任意第三方脚本
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        webView.webViewClient = ShellWebViewClient(
            allowedHosts = BuildConfig.ALLOWED_HOSTS
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet(),
            onMainFrameError = { url -> showError(url) },
            onPageLoaded = { errorView.visibility = View.GONE }
        )

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
        }

        // 下载链接交给系统处理，不在 WebView 内静默下载
        webView.setDownloadListener { url, _, _, _, _ -> openExternally(url) }
    }

    private fun load(url: String) {
        lastUrl = url
        errorView.visibility = View.GONE
        progressBar.progress = 0
        progressBar.visibility = View.VISIBLE
        webView.loadUrl(url)
    }

    /**
     * 主框架加载失败。DDNS 站点常见「只开了 HTTP」或「HTTPS 用自签证书」，
     * 所以在 https 首次失败时自动换 http 重试一次。
     * 注意：这是换协议重试，不是绕过证书校验，SSL 错误始终 handler.cancel()。
     */
    private fun showError(url: String) {
        lastUrl = url
        progressBar.visibility = View.GONE

        val httpUrl = if (url.startsWith("https://", ignoreCase = true)) {
            "http://" + url.substring("https://".length)
        } else null
        if (BuildConfig.ENABLE_HTTP_FALLBACK && httpUrl != null && !httpFallbackTried) {
            httpFallbackTried = true
            load(httpUrl)
            return
        }

        val host = runCatching { Uri.parse(url).host }.getOrNull() ?: url
        errorMessage.text = getString(R.string.error_message, host)
        errorView.visibility = View.VISIBLE
    }

    private fun openExternally(url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::webView.isInitialized) webView.saveState(outState)
        outState.putBoolean(KEY_HTTP_FALLBACK, httpFallbackTried)
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) webView.onResume()
    }

    override fun onPause() {
        if (::webView.isInitialized) webView.onPause()
        super.onPause()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.removeAllViews()
            webView.destroy()
        }
        super.onDestroy()
    }

    private companion object {
        const val KEY_HTTP_FALLBACK = "httpFallbackTried"
    }
}
