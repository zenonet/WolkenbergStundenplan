package de.zenonet.stundenplan.quoteOfTheDay;

import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoField;


import de.zenonet.stundenplan.Utils;
import de.zenonet.stundenplan.callbacks.QuoteLoadedCallback;

public class QuoteProvider {
    public void init() {

    }

    public void getQuoteOfTheDayAsync(QuoteLoadedCallback callback) {
        new Thread(() -> {
            if (!new File(Utils.CachePath, "quotes.json").exists()) {
                loadQuotesFromApi();
            }
            callback.quoteLoaded(getQuoteOfTheDay());
        }).start();
    }

    private boolean loadQuotesFromApi() {
        try {
            HttpURLConnection httpCon = (HttpURLConnection) new URL("http://api.zenonet.de/stundenplan/quotes.json").openConnection();
            httpCon.connect();

            if (httpCon.getResponseCode() != 200)
                return false;

            String content = Utils.readAllFromStream(httpCon.getInputStream());

            // Save loaded quotes to cache
            File cacheFile = new File(Utils.CachePath, "/quotes.json");
            try (FileOutputStream stream = new FileOutputStream(cacheFile)) {
                stream.write(content.getBytes(StandardCharsets.UTF_8));
            }

            return true;

        } catch (IOException e) {
            return false;
        }
    }

    public Quote getQuoteOfTheDay() {
        try {
            File file = new File(Utils.CachePath, "quotes.json");

            int length = (int) file.length();
            byte[] bytes = new byte[length];
            try (FileInputStream in = new FileInputStream(file)) {
                in.read(bytes);
            }
            String contents = new String(bytes);
            JSONObject root = new JSONObject(contents);

            int index = (int) (LocalDate.now().toEpochDay() % root.getInt("count"));
            return new Gson().fromJson(root.getJSONArray("quotes").get(index).toString(), Quote.class);
        } catch (IOException | JSONException ignored) {
            return null;
        }
    }
}
