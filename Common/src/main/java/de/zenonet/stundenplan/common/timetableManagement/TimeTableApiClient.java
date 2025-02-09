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
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.Locale;

import de.zenonet.stundenplan.common.DataNotAvailableException;
import de.zenonet.stundenplan.common.LogTags;
import de.zenonet.stundenplan.common.NameLookup;
import de.zenonet.stundenplan.common.ResultType;
import de.zenonet.stundenplan.common.callbacks.AuthCodeRedeemedCallback;
import de.zenonet.stundenplan.common.models.User;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TimeTableApiClient {
    /**
     * Offset applied to counter values to allow re-fetching when the app version changed
     */
    private final int CounterOffset = 3;

    public String accessToken;
    public User user;

    public NameLookup lookup;
    public SharedPreferences sharedPreferences;
    public boolean isLoggedIn;
    public boolean isOffline;

    /**
     * Fetches "masterdata"/lookup data from the API
     * @return whether the data changed since the last time fetching
     * @throws DataNotAvailableException when the data can't be fetched
     */
    public boolean fetchMasterData() throws DataNotAvailableException {

        Request request = getAuthenticatedRequestBuilder("all").build();
        try(Response response = client.newCall(request).execute()) {
            // Get raw json data
            if (!response.isSuccessful())
                throw new DataNotAvailableException();
            return lookup.saveLookupFile(response.body().string());
        } catch (Exception e) {
            throw new DataNotAvailableException();
        }
    }

    public void init(Context context) {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    }


    public User fetchUserData() throws UserLoadException {
        Request request = getAuthenticatedRequestBuilder("me").build();
        try(Response response = client.newCall(request).execute()) {

            if (!response.isSuccessful()) {
                Log.e(LogTags.Api, String.format("Unable to load personal data: Response code %d", response.code()));
            }
            user = new Gson().fromJson(new InputStreamReader(response.body().byteStream()), User.class);
            return user;
        } catch (Exception e) {
            throw new UserLoadException();
        }
    }

    public ResultType login() {
        String refreshToken = sharedPreferences.getString("refreshToken", "null");

        if (refreshToken.equals("null")) return ResultType.NoLoginSaved;

        Request request = new Request.Builder()
                .url(baseUrl + "token")
                .post(RequestBody.create(
                        "{\"refresh_token\":\"" + refreshToken + "\",\"scope\":\"https://wgmail.onmicrosoft.com/f863619c-ea91-4f1d-85f4-2f907c53963b/user_impersonation\"}",
                        MediaType.get("application/json")
                ))
                .build();

        try(Response response = client.newCall(request).execute()) {
            int respCode = response.code();

            if (respCode != 200) {
                Log.e(LogTags.Api, "Login unsuccessful:" + response.message());
                return ResultType.TokenExpired;
            }

            // Deserialization
            JSONObject jObj = new JSONObject(response.body().string());

            sharedPreferences.edit().putString("refreshToken", jObj.getString("refresh_token")).apply();
            accessToken = jObj.getString("access_token");

            isLoggedIn = true;
            Log.i(LogTags.Login, "Logged in successfully");

            return ResultType.Success;
        } catch (IOException | JSONException e) {
            if(e instanceof UnknownHostException) {
                isOffline = true;
                Log.i(LogTags.Login, "Can't connect to API");
                return ResultType.Offline;
            }
            Log.e(LogTags.Login, "Can't login to API");
        }
        return ResultType.UnknownError;
    }

    public void redeemOAuthCodeAsync(String code, AuthCodeRedeemedCallback callback){
        new Thread(() -> {
            Request request = new Request.Builder()
                    .url("https://www.wolkenberg-gymnasium.de/wolkenberg-app/api/token")
                    .header("Content-Type", "application/json")
                    .header("referer", "https://www.wolkenberg-gymnasium.de/wolkenberg-app/stundenplan-web-app/")
                    .post(RequestBody.create(
                            "{\"code\":\"" + code + "\",\"scope\":\"https://wgmail.onmicrosoft.com/f863619c-ea91-4f1d-85f4-2f907c53963b/user_impersonation\"}",
                            MediaType.get("application/json"))
                    )
                    .build();

            try(Response response = client.newCall(request).execute()) {
                int respCode = response.code();
                Log.i(LogTags.Api, "Get response code " + respCode + " while redeeming OAuth code");

                if (respCode != 200) {
                    callback.errorOccurred(String.format(Locale.GERMAN, "Der Server hat mit Antwort-Code %d geantwortet", respCode));
                    return;
                }

                // get access token
                JSONObject jObj = new JSONObject(response.body().string());
                accessToken = jObj.getString("access_token");

                // get refresh token
                String refreshToken = jObj.getString("refresh_token");
                sharedPreferences.edit().putString("refreshToken", refreshToken).apply();

                if (callback != null) callback.authCodeRedeemed();


            } catch (IOException | JSONException | AssertionError e) {
                if(callback != null)
                    callback.errorOccurred(e.getMessage());
            }
        }).start();
    }

    /**
     * Get the latest currently available counter value from API or from storage
     */
    private long latestCounter = -1;
    public LocalDateTime timeOfConfirmation;
    public boolean isCounterConfirmed;
    public String postsHash;
    public long getLatestCounterValue() {
        return getLatestCounterValue(false, true);
    }
    public long getLatestCounterValue(boolean forceRefetch) {
        return getLatestCounterValue(forceRefetch, true);
    }

    OkHttpClient client = new OkHttpClient();
    public long getLatestCounterValue(boolean forceRefetch, boolean retry) {
        // If the counter has already been fetched, just return it
        if (latestCounter != -1 && !forceRefetch) // TODO: Add expiration and re-fetching
            return latestCounter;

        Request request = getAuthenticatedRequestBuilder("counter").get().build();
        try (Response response = client.newCall(request).execute()) {
            if(response.code() != 200){
                if(!retry){// NEVER remove this. Otherwise recursion won't be stopped!
                    Log.i(LogTags.Api, "Fetching counter failed again! (code:" + response.code() + ") what is going on?");
                    return latestCounter;
                }

                Log.i(LogTags.Api, "Got response code " + response.code() + " while fetching counter value. Trying to relogin...");
                ResultType result = login();
                if(result != ResultType.Success){
                    Log.i(LogTags.Api, "Relogin failed. Giving up");
                    return latestCounter;
                }
                Log.i(LogTags.Api, "Relogin succeeded! Fetching counter value...");
                return getLatestCounterValue(true, false);
            }
            assert response.body() != null;
            JSONObject responseJson = new JSONObject(response.body().string());

            // Let's just assume this counter works like a big number with a few dashes between digits
            latestCounter = Long.parseLong(responseJson.getString("COUNTER").replace("-", ""));
            latestCounter += CounterOffset;
            isCounterConfirmed = true;
            timeOfConfirmation = LocalDateTime.now();

            // while we're here, also use the posts hash
            postsHash = responseJson.getString("POSTS_HASH");
            sharedPreferences.edit()
                    .putLong("counter", latestCounter)
                    .putString("postsHash", postsHash)
                    .apply();

            return latestCounter;
        } catch (IOException | JSONException | AssertionError e) {
            isCounterConfirmed = false;
            postsHash = sharedPreferences.getString("postsHash", "");
            return sharedPreferences.getLong("counter", -1);
        }
    }

    private final String baseUrl = "https://www.wolkenberg-gymnasium.de/wolkenberg-app/api/";


    public Request.Builder getAuthenticatedRequestBuilder(String endpoint){
        return new Request.Builder()
                .url(baseUrl + endpoint)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken);
    }
    public String fetchRawData() throws IOException {
        Request request = getAuthenticatedRequestBuilder("timetable/student/" + user.id).build();
        try(Response response = client.newCall(request).execute()){
            if(!response.isSuccessful()) throw new IOException("Request for timetable/student/ not successful");
            return response.body().string();
        }
    }
    public String fetchRawSubstitutionData() throws IOException {
        Request request = getAuthenticatedRequestBuilder("substitution/student/" + user.id).build();
        try(Response response = client.newCall(request).execute()){
            if(!response.isSuccessful()) throw new IOException("Request for timetable/student/ not successful");
            return response.body().string();
        }
    }

    /**
     * Simultaneously fetches timetable and substitution data from the API
     * @return A Pair containing the raw timetable data and raw substitution data (in that order)
     */
    Pair<String, String> fetchRawDataFromApi() {
        final String[] rawDataArray = {null, null};

        // fetch timetable and substitutions simultaneously
        Thread timeTableFetchThread = new Thread(() -> {
            try {
                rawDataArray[0] = (fetchRawData());
            } catch (IOException ignored) {
            }
        });
        timeTableFetchThread.start();
        Thread substitutionFetchThread = new Thread(() -> {
            try {
                rawDataArray[1] = fetchRawSubstitutionData();
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

    public Post[] fetchPosts(){
        Request request = getAuthenticatedRequestBuilder("posts").build();
        try(Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful())
                return null;

            JSONArray array = new JSONArray(response.body().string());
            Post[] posts = new Post[array.length()];

            for (int i = 0; i < array.length(); i++) {
                JSONObject p = array.getJSONObject(i);
                posts[i] = new Post();
                posts[i].PostId = p.getInt("POST_ID");
                posts[i].Creator = p.getString("CREATOR");
                posts[i].Title = p.getString("TITLE");

                // Load image URLs
                JSONArray jImages = p.getJSONArray("IMAGES");
                String[] images = new String[jImages.length()];
                for (int im = 0; im < jImages.length(); im++) {
                    images[im] = jImages.getString(im);
                }
                posts[i].Images = images;

                // Load text
                StringBuilder textBuilder = new StringBuilder();
                JSONArray textBlocks = new JSONObject(p.getString("TEXT")).getJSONArray("blocks");
                for (int j = 0; j < textBlocks.length(); j++) {
                    JSONObject block = textBlocks.getJSONObject(j);
                    textBuilder.append(block.getString("text"));
                    textBuilder.append('\n');
                }
                posts[i].Text = textBuilder.toString();
            }

            return posts;
        } catch (IOException | JSONException e) {
            e.printStackTrace();
            return null;
        }
    }
}
