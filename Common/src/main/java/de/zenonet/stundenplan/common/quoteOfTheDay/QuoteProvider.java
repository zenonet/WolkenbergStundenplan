package de.zenonet.stundenplan.common.quoteOfTheDay;

import android.util.Log;

import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;


import de.zenonet.stundenplan.common.Utils;
import de.zenonet.stundenplan.common.callbacks.QuoteLoadedCallback;

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
            HttpURLConnection httpCon = (HttpURLConnection) new URL("https://zenonet.de/stundenplan/quotes.json").openConnection();
            httpCon.connect();

            if (httpCon.getResponseCode() != 200)
                return false;

            String content = Utils.readAllFromStream(httpCon.getInputStream());

            // Save loaded quotes to cache
            File cacheFile = new File(Utils.CachePath, "/quotes.json");
            try (FileOutputStream stream = new FileOutputStream(cacheFile)) {
                stream.write(content.getBytes(StandardCharsets.UTF_8));
            }


            // Save etag of the file
            File etagFile = new File(Utils.CachePath, "/etag.txt");
            try (FileOutputStream stream = new FileOutputStream(etagFile)) {
                stream.write(httpCon.getHeaderField("Etag").getBytes(StandardCharsets.UTF_8));
            }

            return true;

        } catch (IOException e) {
            return false;
        }
    }

    public Quote getQuoteOfTheDay() {
        maybeCheckForNewQuotes();
        try {
            File file = new File(Utils.CachePath, "quotes.json");
            if (!file.exists())
                loadQuotesFromApi();

            String contents = Utils.readAllText(file);
            JSONObject root = new JSONObject(contents);

            if (root.has("enabled") && !root.getBoolean("enabled"))
                return null;

            JSONArray quotes = root.getJSONArray("quotes");

            // Allows the server to force a specific quote
            int index = root.has("forcedQuote")
                    ? root.getInt("forcedQuote")
                    : (int) (LocalDate.now().toEpochDay() % quotes.length());

            return new Gson().fromJson(quotes.get(index).toString(), Quote.class);
        } catch (IOException | JSONException e) {
            return null;
        }
    }

    public void maybeCheckForNewQuotes() {
        File cacheFile = new File(Utils.CachePath, "/quotes.json");
        long distance = (Instant.now().getEpochSecond() - cacheFile.lastModified() / 1000);
        // If the cache file was last changed more than one week ago
        //if (distance <= 60 * 60 * 24) return;

        try {

            File etagFile = new File(Utils.CachePath, "/etag.txt");

            if (!etagFile.exists()) {
                loadQuotesFromApi();
                return;
            }

            String localEtag = Utils.readAllText(etagFile);

            HttpURLConnection httpCon;
            httpCon = (HttpURLConnection) new URL("https://zenonet.de/stundenplan/quotes.json").openConnection();
            httpCon.setRequestMethod("HEAD");
            httpCon.connect();

            String etag = httpCon.getHeaderField("Etag");

            if (!localEtag.equals(etag)) {
                loadQuotesFromApi();
            }

        } catch (IOException e) {
            Log.e(Utils.LOG_TAG, e.toString());
        }

    }
}
