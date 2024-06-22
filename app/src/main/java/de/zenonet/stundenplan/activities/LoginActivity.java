package de.zenonet.stundenplan.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.view.KeyEvent;
import android.webkit.*;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.Toast;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.zenonet.stundenplan.R;

public class LoginActivity extends AppCompatActivity {

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_view);

        WebView webView = findViewById(R.id.webView);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onLoadResource(WebView view, String url) {
                checkForCodeUrl(view, url);
            }

            public boolean shouldOverrideUrlLoading (WebView view, String url) {
                return checkForCodeUrl(view, url);
            }
            public boolean shouldOverrideUrlLoading (WebView view, WebResourceRequest request) {
                return checkForCodeUrl(view, request.getUrl().toString());
            }

            public boolean shouldOverrideKeyEvent (WebView view, KeyEvent event) {
                return true;
            }


            private boolean checkForCodeUrl(WebView view, String url) {
                if (!url.startsWith("https://www.wolkenberg-gymnasium.de/wolkenberg-app/stundenplan-web-app/?code="))
                    return false;

                String oAuthCode = extractRefreshTokenFromUrl(url);

                // Log.i("TokenExtractor", "onPageStarted: Found the code (I think): " + refreshToken);

                clearCacheBloat();

                Toast.makeText(LoginActivity.this, "Auth code found!", Toast.LENGTH_SHORT).show();
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
    }

    private static String extractRefreshTokenFromUrl(String url) {
        String timeRegex = "https?://www.wolkenberg-gymnasium\\.de/wolkenberg-app/stundenplan-web-app/?\\?code=(.*?)&.*";
        Pattern pattern = Pattern.compile(timeRegex);
        Matcher matcher = pattern.matcher(url);
        if (!matcher.matches()) return null;

        return matcher.group(1);
    }

    private void clearCacheBloat(){
        // Clear all the Application Cache, Web SQL Database and the HTML5 Web Storage
        WebStorage.getInstance().deleteAllData();

        // Clear all the cookies
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
    }
}