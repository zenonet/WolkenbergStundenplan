package de.zenonet.stundenplan.common.timetableManagement;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.util.Pair;

import androidx.preference.PreferenceManager;

import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Calendar;

import de.zenonet.stundenplan.common.DataNotAvailableException;
import de.zenonet.stundenplan.common.LogTags;
import de.zenonet.stundenplan.common.NameLookup;
import de.zenonet.stundenplan.common.Utils;
import de.zenonet.stundenplan.common.callbacks.AuthCodeRedeemedCallback;
import de.zenonet.stundenplan.common.models.User;

public class TimeTableApiClient {
    /**
     * Offset applied to counter values to allow re-fetching when the app version changed
     */
    private final int CounterOffset = 1;

    private String accessToken;
    public User user;

    public NameLookup lookup;
    public SharedPreferences sharedPreferences;
    public boolean isLoggedIn;

    /**
     * Fetches "masterdata"/lookup data from the API
     * @return whether the data changed since the last time fetching
     * @throws DataNotAvailableException when the data can't be fetched
     */
    public boolean fetchMasterData() throws DataNotAvailableException {

        try {
            // Get raw json data
            HttpURLConnection httpCon = getAuthenticatedUrlConnection("GET", "/all");
            httpCon.connect();
            int respCode = httpCon.getResponseCode();
            if (respCode != 200)
                throw new DataNotAvailableException();
            String raw = Utils.readAllFromStream(httpCon.getInputStream());
            return lookup.saveLookupFile(raw);
        } catch (Exception e) {
            throw new DataNotAvailableException();
        }
    }

    public void init(Context context) {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    }


    public User getUser() throws UserLoadException {
        try {
            HttpURLConnection httpCon = getAuthenticatedUrlConnection("GET", "me");
            httpCon.connect();

            int respCode = httpCon.getResponseCode();
            if (respCode != 200) {
                Log.e(LogTags.Api, String.format("Unable to load personal data: Response code %d", respCode));
            }

            user = new Gson().fromJson(new InputStreamReader(httpCon.getInputStream()), User.class);
            return user;
        } catch (Exception e) {
            throw new UserLoadException();
        }
    }

    public void login() throws ApiLoginException {
        String refreshToken = sharedPreferences.getString("refreshToken", "null");

        if (refreshToken.equals("null")) throw new ApiLoginException();
        try {
            HttpURLConnection httpCon = getUrlApiConnection("POST", "token");
            httpCon.setDoOutput(true);

            OutputStream os = httpCon.getOutputStream();
            OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
            osw.write("{\"refresh_token\":\"" + refreshToken + "\",\"scope\":\"https://wgmail.onmicrosoft.com/f863619c-ea91-4f1d-85f4-2f907c53963b/user_impersonation\"}");
            osw.flush();
            osw.close();
            os.close();

            int respCode = httpCon.getResponseCode();

            if (respCode != 200) {
                Log.e(LogTags.Api, "Login unsuccessful:" + httpCon.getResponseMessage());
                throw new ApiLoginException();
            }

            String content = Utils.readAllFromStream(httpCon.getInputStream());

            // Deserialization
            JSONObject jObj = new JSONObject(content);

            sharedPreferences.edit().putString("refreshToken", jObj.getString("refresh_token")).apply();
            accessToken = jObj.getString("access_token");
            isLoggedIn = true;
            Log.i(LogTags.Login, "Logged in successfully");
        } catch (IOException | JSONException e) {
            throw new ApiLoginException();
        }
    }

    public void redeemOAuthCodeAsync(String code, AuthCodeRedeemedCallback callback) {
        new Thread(() -> {
            HttpURLConnection httpCon;
            try {
                URL url = new URL("https://www.wolkenberg-gymnasium.de/wolkenberg-app/api/token");
                httpCon = (HttpURLConnection) url.openConnection();
                httpCon.setRequestProperty("Content-Type", "application/json");
                httpCon.setRequestProperty("referer", "https://www.wolkenberg-gymnasium.de/wolkenberg-app/stundenplan-web-app/");
                httpCon.setDoOutput(true);
                httpCon.setDoInput(true);

                OutputStream os = httpCon.getOutputStream();
                OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
                osw.write("{\"code\":\"" + code + "\",\"scope\":\"https://wgmail.onmicrosoft.com/f863619c-ea91-4f1d-85f4-2f907c53963b/user_impersonation\"}");
                osw.flush();
                osw.close();
                os.close();

                int respCode = httpCon.getResponseCode();
                Log.i(LogTags.Api, "Get response code " + respCode + " while redeeming OAuth code");

                String body = Utils.readAllFromStream(httpCon.getInputStream());
                JSONObject jObj = new JSONObject(body);
                accessToken = jObj.getString("access_token");
                // isLoggedIn = true; This would stop the login method from doing anything meaning the masterdata wouldn't be fetched

                String refreshToken = jObj.getString("refresh_token");
                sharedPreferences.edit().putString("refreshToken", refreshToken).apply();

                if (callback != null) callback.authCodeRedeemed();

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).start();
    }

    /**
     * Get the latest currently available counter value from API or from storage
     */
    private long latestCounter = -1;
    public boolean isCounterConfirmed;
    public long getLatestCounterValue() {
        // If the counter has already been fetched, just return it
        if(latestCounter != -1) // TODO: Add expiration and re-fetching
            return latestCounter;

        try {
            HttpURLConnection httpCon = getAuthenticatedUrlConnection("GET", "counter");

            JSONObject response = new JSONObject(Utils.readAllFromStream(httpCon.getInputStream()));

            // Let's just assume this counter works like a big number with a few dashes between digits
            latestCounter = Long.parseLong(response.getString("COUNTER").replace("-", ""));
            latestCounter += CounterOffset;
            sharedPreferences.edit().putLong("counter", latestCounter).apply();
            isCounterConfirmed = true;
            return latestCounter;
        } catch (IOException | JSONException e) {
            isCounterConfirmed = false;
            return sharedPreferences.getLong("counter", -1);
        }
    }

    private final String baseUrl = "https://www.wolkenberg-gymnasium.de/wolkenberg-app/api/";

    public HttpURLConnection getAuthenticatedUrlConnection(String method, String endpoint) throws IOException {
        HttpURLConnection httpCon = getUrlApiConnection(method, endpoint);
        httpCon.setRequestProperty("Authorization", "Bearer " + accessToken);
        return httpCon;
    }

    public HttpURLConnection getUrlApiConnection(String method, String endpoint) throws IOException {
        HttpURLConnection httpCon = (HttpURLConnection) new URL(baseUrl + endpoint).openConnection();
        httpCon.setRequestMethod(method);
        httpCon.setRequestProperty("Content-Type", "application/json");
        return httpCon;
    }

    public String getRawData() throws IOException {
        HttpURLConnection httpCon = getAuthenticatedUrlConnection("GET", "timetable/student/" + user.id);
        httpCon.connect();
        if (httpCon.getResponseCode() != 200)
            throw new IOException();

        return Utils.readAllFromStream(httpCon.getInputStream());
    }
    public String getRawSubstitutionData() throws IOException {
        HttpURLConnection httpCon = getAuthenticatedUrlConnection("GET", "substitution/student/" + user.id);
        httpCon.connect();
        if (httpCon.getResponseCode() != 200)
            throw new IOException();

        return Utils.readAllFromStream(httpCon.getInputStream());
    }

    /**
     * Simultaneously fetches timetable and substitution data from the API
     * @return A Pair containing the raw timetable data and raw substitution data (in that order)
     */
    Pair<String, String> getRawDataFromApi() {
        final String[] rawDataArray = {null, null};

        // fetch timetable and substitutions simultaneously
        Thread timeTableFetchThread = new Thread(() -> {
            try {
                rawDataArray[0] = (getRawData());
            } catch (IOException ignored) {
            }
        });
        timeTableFetchThread.start();
        Thread substitutionFetchThread = new Thread(() -> {
            try {
                rawDataArray[1] = getRawSubstitutionData();
            } catch (IOException ignored) {
            }
        });
        substitutionFetchThread.start();

        try {
            timeTableFetchThread.join();
            substitutionFetchThread.join();
        }catch (InterruptedException ignored){

        }
        return new Pair<>(
                rawDataArray[0],
                rawDataArray[1]
        );
    }
}
