package com.example.webviewshell

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * 链接策略：
 * - 白名单内的 http/https 链接在 WebView 内打开（子域名自动放行）
 * - 白名单外的链接、tel:/mailto: 等 scheme 交给系统，避免套壳 App 变成通用浏览器
 */
class ShellWebViewClient(
    private val allowedHosts: Set<String>,
    private val onMainFrameError: (String) -> Unit,
    private val onPageLoaded: () -> Unit
) : WebViewClient() {

    // 主框架加载是否失败。加载失败后 onPageFinished 仍可能被回调，
    // 用这个标记防止刚显示的错误页被立刻隐藏。
    private var mainFrameFailed = false

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        return handle(view, request.url)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        return handle(view, Uri.parse(url))
    }

    private fun handle(view: WebView, uri: Uri): Boolean {
        if (isInternal(uri)) return false

        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            view.context.startActivity(intent)
        }
        return true
    }

    private fun isInternal(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        if (allowedHosts.contains("*")) return true

        val host = uri.host?.lowercase() ?: return false
        return allowedHosts.any { allowed ->
            val domain = allowed.lowercase()
            host == domain || host.endsWith(".$domain")
        }
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        mainFrameFailed = false
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        super.onReceivedError(view, request, error)
        // 只处理主框架错误；图片、CSS 等子资源失败不打扰用户
        if (request.isForMainFrame) {
            mainFrameFailed = true
            onMainFrameError(request.url.toString())
        }
    }

    override fun onReceivedSslError(
        view: WebView,
        handler: SslErrorHandler,
        error: SslError
    ) {
        // 安全红线：始终 cancel，绝不放行非法证书。
        // 自签证书站点会由 MainActivity 自动改用 http 重试一次。
        handler.cancel()
        val current = view.url
        if (current == null || current == error.url) {
            mainFrameFailed = true
            onMainFrameError(error.url)
        }
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        if (!mainFrameFailed) onPageLoaded()
    }
}
