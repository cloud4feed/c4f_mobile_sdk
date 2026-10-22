package com.c4f.mobileSDK

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.View
import android.webkit.*
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import android.net.http.SslError

object WebViewConfigurator {

    // ====================================================================
    // MAIN CONFIGURATION METHOD
    // ====================================================================
    fun setupSecureWebView(
        webView: WebView,
        url: String,
        allowedDomain: String?,
        onPageStarted: (() -> Unit)? = null,
        onPageFinished: (() -> Unit)? = null,
        onSurveyClosed: (() -> Unit)? = null
    ) {
        configureWebViewSettings(webView)
        webView.webViewClient = createSafeWebViewClient(
            allowedDomain, 
            webView, 
            url, 
            onPageStarted, 
            onPageFinished, 
            onSurveyClosed
        )
        webView.webChromeClient = WebChromeClient()
        webView.loadUrl(url)
    }

    // ====================================================================
    // WEBVIEW SETTINGS CONFIGURATION
    // ====================================================================
    private fun configureWebViewSettings(webView: WebView) {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            // Survey links are often shortened URLs that redirect, sometimes to an
            // http target. Use compatibility mode so an https survey page can still
            // follow such redirects / load their resources instead of a blank page.
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
            
            // Additional security settings
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                allowFileAccessFromFileURLs = false
                allowUniversalAccessFromFileURLs = false
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }
            
            // Performance settings
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
        }
    }

    // ====================================================================
    // WEBVIEW CLIENT CREATION
    // ====================================================================
    private fun createSafeWebViewClient(
        allowedDomain: String?,
        webView: WebView,
        originalUrl: String,
        onPageStarted: (() -> Unit)? = null,
        onPageFinished: (() -> Unit)? = null,
        onSurveyClosed: (() -> Unit)? = null
    ): WebViewClient {
        return object : WebViewClient() {
            private var errorOccurred = false

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                Log.d("WebViewConfigurator", "🚀 Starting to load: $url")
                errorOccurred = false
                hideErrorLayout(webView)
                onPageStarted?.invoke()
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                if (errorOccurred) return
                hideErrorLayout(webView)
                onPageFinished?.invoke()
                Log.d("WebViewConfigurator", "✅ Page loaded successfully: $loadedUrl")
            }

            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                errorOccurred = true
                Log.e("WebViewConfigurator", "❌ Page load error: $description")
                Log.e("WebViewConfigurator", "❌ Error code: $errorCode")
                Log.e("WebViewConfigurator", "❌ Failing URL: $failingUrl")
                Log.e("WebViewConfigurator", "❌ Original URL: $originalUrl")
                showErrorLayout(webView, originalUrl, onSurveyClosed)
            }

            @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                if (errorResponse != null) {
                    Log.e("WebViewConfigurator", "❌ HTTP Error: ${errorResponse.statusCode}")
                }
                if (request != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    Log.e("WebViewConfigurator", "❌ HTTP Error URL: ${request.url}")
                }
            }

            // ============ FOR ANDROID 5.0+ (API 21+) ============
            @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return true
                Log.d("WebViewConfigurator", "🔗 Navigation attempt (API 21+): $url")
                return handleUrlLoading(url, allowedDomain)
            }

            // ============ FOR ANDROID < 5.0 (LEGACY) ============
            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                // On Android 5.0+, let the new method handle it
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    return super.shouldOverrideUrlLoading(view, url)
                }
                
                val safeUrl = url ?: return true
                Log.d("WebViewConfigurator", "🔗 Navigation attempt (Legacy): $safeUrl")
                return handleUrlLoading(safeUrl, allowedDomain)
            }

            // ============ SSL ERROR HANDLING ============
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                Log.e("WebViewConfigurator", "🔒 SSL Error: ${error?.toString()}")
                
                // For safety, cancel SSL errors in production
                // You can modify this based on your needs
                handler?.cancel()
                errorOccurred = true
                showErrorLayout(webView, originalUrl, onSurveyClosed)
            }
            
            // ============ HELPER METHOD ============
            private fun handleUrlLoading(url: String, allowedDomain: String?): Boolean {
                // Special schemes (mailto:, tel:, market://, intent:// ...) are opened
                // by the matching external app, never inside the survey WebView.
                if (isSpecialUrl(url)) {
                    openUrlExternally(url, webView.context)
                    return true // Handled externally
                }

                // Web navigation is NOT restricted by domain. Survey links are often
                // shortened URLs (ShortIO, bit.ly, custom shorteners) that redirect to
                // an arbitrary host, so there is no fixed domain to allow-list. Let the
                // WebView follow http/https navigation and redirects freely.
                errorOccurred = false
                return false // Allow navigation
            }
        }
    }

    // ====================================================================
    // ERROR LAYOUT HANDLING
    // ====================================================================
    private fun showErrorLayout(webView: WebView, originalUrl: String, onSurveyClosed: (() -> Unit)? = null) {
        webView.post {
            try {
                val context = webView.context

                // Create error layout
                val errorLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(0xFFFFFFFF.toInt())
                    setPadding(50, 100, 50, 100)
                    gravity = android.view.Gravity.CENTER
                }

                // Error icon
                val errorIcon = TextView(context).apply {
                    text = "⚠️"
                    textSize = 48f
                    gravity = android.view.Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = 20
                    }
                }

                // Error title
                val errorText = TextView(context).apply {
                    text = "Survey Loading Failed"
                    textSize = 18f
                    setTextColor(0xFF333333.toInt())
                    gravity = android.view.Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = 10
                    }
                }

                // Error description
                val errorDesc = TextView(context).apply {
                    text = "Please check your internet connection and try again."
                    textSize = 14f
                    setTextColor(0xFF666666.toInt())
                    gravity = android.view.Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = 30
                    }
                }

                // Retry button
                val retryButton = Button(context).apply {
                    text = "Retry Survey"
                    setBackgroundColor(0xFF007AFF.toInt())
                    setTextColor(0xFFFFFFFF.toInt())
                    setPadding(40, 20, 40, 20)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = android.view.Gravity.CENTER
                    }
                    setOnClickListener {
                        Log.d("WebViewConfigurator", "🔄 Retry button clicked")
                        hideErrorLayout(webView)
                        webView.loadUrl(originalUrl)
                    }
                }

                // Close button
                val closeButton = Button(context).apply {
                    text = "Close"
                    setBackgroundColor(0xFF8E8E93.toInt())
                    setTextColor(0xFFFFFFFF.toInt())
                    setPadding(40, 20, 40, 20)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = android.view.Gravity.CENTER
                        topMargin = 10
                    }
                    setOnClickListener {
                        Log.d("WebViewConfigurator", "❌ Close button clicked")
                        hideErrorLayout(webView)
                        onSurveyClosed?.invoke()
                    }
                }

                // Add views to error layout
                errorLayout.addView(errorIcon)
                errorLayout.addView(errorText)
                errorLayout.addView(errorDesc)
                errorLayout.addView(retryButton)
                errorLayout.addView(closeButton)

                // Replace WebView with error layout
                val parent = webView.parent as? android.view.ViewGroup
                parent?.let {
                    webView.tag = "survey_webview"
                    it.removeView(webView)
                    it.addView(errorLayout)
                    errorLayout.tag = "error_layout"
                }

                Log.d("WebViewConfigurator", "🔄 Error layout shown with retry/close buttons")

            } catch (e: Exception) {
                Log.e("WebViewConfigurator", "❌ Error showing error layout: ${e.message}")
            }
        }
    }

    private fun hideErrorLayout(webView: WebView) {
        webView.post {
            try {
                val parent = webView.parent as? android.view.ViewGroup
                parent?.let {
                    // Remove error layout if present
                    val errorLayout = it.findViewWithTag<LinearLayout>("error_layout")
                    errorLayout?.let { errorView ->
                        it.removeView(errorView)
                        Log.d("WebViewConfigurator", "✅ Error layout removed")
                    }

                    // Restore WebView if not already present
                    if (it.indexOfChild(webView) == -1) {
                        it.addView(webView)
                        Log.d("WebViewConfigurator", "✅ WebView restored")
                    }
                }
            } catch (e: Exception) {
                Log.e("WebViewConfigurator", "❌ Error hiding error layout: ${e.message}")
            }
        }
    }

    // ====================================================================
    // URL VALIDATION & HANDLING
    // ====================================================================
    private fun isSpecialUrl(url: String): Boolean {
        return url.startsWith("mailto:") ||
               url.startsWith("tel:") ||
               url.startsWith("sms:") ||
               url.startsWith("market://") ||
               url.startsWith("intent://")
    }

    private fun openUrlExternally(url: String, context: Context?) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context?.startActivity(intent)
            Log.d("WebViewConfigurator", "🔗 Opened URL externally: $url")
        } catch (e: Exception) {
            Log.e("WebViewConfigurator", "❌ Failed to open URL externally: $url - ${e.message}")
        }
    }
}