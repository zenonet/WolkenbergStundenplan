package de.zenonet.stundenplan.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.webkit.*;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.Toast;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.zenonet.stundenplan.R;

public class LoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_token_extractor);

        // Clear all the Application Cache, Web SQL Database and the HTML5 Web Storage
        WebStorage.getInstance().deleteAllData();

        // Clear all the cookies
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();

        WebView webView = findViewById(R.id.webView);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (url.startsWith("https://www.wolkenberg-gymnasium.de/wolkenberg-app/stundenplan-web-app/?code=")) {

                    String refreshToken = extractRefreshTokenFromUrl(url);
                    saveRefreshToken(refreshToken);

                    // Log.i("TokenExtractor", "onPageStarted: Found the code (I think): " + refreshToken);

                    Toast.makeText(LoginActivity.this, "Refresh token found!",  Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(LoginActivity.this, TimeTableViewActivity.class);
                    startActivity(intent);
                }
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
    private void saveRefreshToken(String token){
        SharedPreferences sharedPreferences = getSharedPreferences("de.zenonet.stundenplan", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("refreshToken", token);
        editor.apply();
    }
}