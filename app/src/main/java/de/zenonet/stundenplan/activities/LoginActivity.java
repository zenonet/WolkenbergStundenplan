package de.zenonet.stundenplan.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.view.View;
import android.webkit.*;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;

import de.zenonet.stundenplan.R;

public class LoginActivity extends AppCompatActivity {

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_view);

        // The login happens in this webview
        // No login-data is ever controlled by this app
        // Only the login result is intercepted
        WebView webView = findViewById(R.id.webView);
        webView.requestFocus(View.FOCUS_DOWN);
        webView.setWebViewClient(new WebViewClient() {

            public boolean shouldOverrideUrlLoading (WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();


                // The redirection to the official app is intercepted here to get the OAuth-code
                if (!url.startsWith("https://www.wolkenberg-gymnasium.de/wolkenberg-app/stundenplan-web-app/?code="))
                    return false;

                // Get the OAuth-Code from the URL
                String oAuthCode = request.getUrl().getQueryParameter("code");

                clearCacheBloat();

                // Return the OAuth-Code back to the calling activity (e.g. the onboarding activity)
                Intent data = new Intent();
                data.putExtra("code", oAuthCode);
                setResult(RESULT_OK, data);
                finish();
                return true;
            }
        });
        webView.clearCache(true);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowContentAccess(true);
        webView.loadUrl("https://www.wolkenberg-gymnasium.de/wolkenberg-app/stundenplan-web-app");

        // This URL would work as well and would be significantly faster:
        //webView.loadUrl("https://login.microsoftonline.com/wgmail.de/oauth2/v2.0/authorize?client_id=fb82e2a9-1efd-4a8e-9ac6-92413ab4b58b&response_type=code&redirect_uri=https%3A%2F%2Fwww.wolkenberg-gymnasium.de%2Fwolkenberg-app%2Fstundenplan-web-app%2F&response_mode=query&scope=offline_access%20https%3A%2F%2Fwgmail.onmicrosoft.com%2Ff863619c-ea91-4f1d-85f4-2f907c53963b%2Fuser_impersonation%20https%3A%2F%2Fgraph.microsoft.com%2Fmail.read&state=%7B%22hash%22%3A%22%22%7D&domain_hint=wgmail.de");
        // But I am afraid the client id might change at some point which would completely destroy the login
    }

    private void clearCacheBloat(){
        // Clear all the Application Cache, Web SQL Database and the HTML5 Web Storage
        WebStorage.getInstance().deleteAllData();

        // Clear all the cookies
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
    }
}