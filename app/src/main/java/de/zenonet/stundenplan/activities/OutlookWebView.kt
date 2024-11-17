package de.zenonet.stundenplan.activities

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import de.zenonet.stundenplan.R

class OutlookWebView : AppCompatActivity() {

    lateinit var webView: WebView
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_outlook_web_view)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        webView = findViewById<WebView>(R.id.outlookWebView)

        webView.settings.javaScriptEnabled = true
        webView.setWebViewClient(object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean{
                // do your handling codes here, which url is the requested url
                // probably you need to open that url rather than redirect:
                view.loadUrl(url);
                return false; // then it is not handled by default action
            }
        })


        if (Build.VERSION.SDK_INT >= 29) {
            webView.isForceDarkAllowed = true
            webView.settings.setForceDark(WebSettings.FORCE_DARK_ON)

            if (Build.VERSION.SDK_INT >= 33) {
                webView.settings.isAlgorithmicDarkeningAllowed = true;
            }

        }
        webView.loadUrl("https://outlook.office365.com/mail/", mapOf<String, String>(
            Pair("Referer", "https://www.wolkenberg-gymnasium.de/")
        ))

        onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true){
            override fun handleOnBackPressed() {
                if(webView.canGoBack())
                    webView.goBack()
                else
                    finishAndRemoveTask()

            }
        })
    }
}