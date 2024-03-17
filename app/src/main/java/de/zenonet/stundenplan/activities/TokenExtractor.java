package de.zenonet.stundenplan.activities;

import android.webkit.*;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import de.zenonet.stundenplan.R;

public class TokenExtractor extends AppCompatActivity {

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
        webView.setWebViewClient(new WebViewClient());
        webView.clearCache(true);


        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);

        settings.setAllowContentAccess(true);
        webView.loadUrl("https://www.wolkenberg-gymnasium.de/wolkenberg-app/stundenplan-web-app");

        Button yoinkButton = findViewById(R.id.yoinkButton);

        yoinkButton.setOnClickListener(v -> {
            // Please don't do this at home, I am still thinking about whether this is ethical even in this case, where the token is kept on the device
            webView.evaluateJavascript("await (function(){  return new Promise(function(resolve){    var open = indexedDB.open(\"localforage\");    open.onerror = function(event) {      console.log(\"Error loading database\");    }    open.onsuccess = function(event) {      var db = open.result;      var transaction = db.transaction(\"keyvaluepairs\", \"readonly\");      var objectStore = transaction.objectStore(\"keyvaluepairs\");      var get = objectStore.get(\"authentication_user\");        get.onsuccess = function(event){        resolve(get.result.refreshToken.access_token);      }    }   })})();",
                    value -> {
                        System.out.println(value);
                    });
        });

    }
}