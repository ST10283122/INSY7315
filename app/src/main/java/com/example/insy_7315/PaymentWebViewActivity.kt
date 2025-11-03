package com.example.insy_7315

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class PaymentWebViewActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        val htmlContent = intent.getStringExtra("HTML_CONTENT") ?: ""
        val paymentType = intent.getStringExtra("PAYMENT_TYPE") ?: "Payment"

        // WebView configuration
        webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.loadsImagesAutomatically = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
        }

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                return handleUrl(url)
            }

            // Deprecated fallback for older devices
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return handleUrl(url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Optionally show a toast or hide a loading spinner
            }

            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                Toast.makeText(
                    this@PaymentWebViewActivity,
                    "Payment page error: $description",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        webView.loadDataWithBaseURL(
            "https://payment.local",
            htmlContent,
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun handleUrl(url: String): Boolean {
        when {
            url.contains("payment-success", ignoreCase = true) -> {
                Toast.makeText(this, "Payment successful!", Toast.LENGTH_LONG).show()
                setResult(RESULT_OK)
                finish()
                return true
            }
            url.contains("payment-error", ignoreCase = true) -> {
                Toast.makeText(this, "Payment failed. Please try again.", Toast.LENGTH_LONG).show()
                setResult(RESULT_CANCELED)
                finish()
                return true
            }
            url.contains("payment-cancel", ignoreCase = true) -> {
                Toast.makeText(this, "Payment cancelled", Toast.LENGTH_SHORT).show()
                setResult(RESULT_CANCELED)
                finish()
                return true
            }
            // Allow navigation within known payment gateways
            url.contains("ozow", ignoreCase = true) ||
                    url.contains("payfast", ignoreCase = true) ||
                    url.contains("stripe", ignoreCase = true) -> {
                return false
            }
            else -> {
                // Block unknown/unexpected redirects for security
                Toast.makeText(this, "Unknown redirect blocked: $url", Toast.LENGTH_SHORT).show()
                return true
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.apply {
            clearHistory()
            clearCache(true)
            loadUrl("about:blank")
            removeAllViews()
            destroy()
        }
    }
}
