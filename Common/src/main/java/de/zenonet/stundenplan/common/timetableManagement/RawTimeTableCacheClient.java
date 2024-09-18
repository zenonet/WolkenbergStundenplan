package de.zenonet.stundenplan.common.timetableManagement;

import android.util.Log;
import android.util.Pair;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import de.zenonet.stundenplan.common.LogTags;
import de.zenonet.stundenplan.common.Utils;

public class RawTimeTableCacheClient {
    public void saveRawData(String json, String substitutionsJson){
        try {
            File rawCacheDir = new File(Utils.CachePath, "raw");
            if(!rawCacheDir.exists()){
                rawCacheDir.mkdir();
            }


            File timetableFile = new File(Utils.CachePath, "/raw/timetable.json");

            Log.v(LogTags.Caching, "saving raw timetable data...");
            try (FileOutputStream out = new FileOutputStream(timetableFile)) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            }

            File substitutionsFile = new File(Utils.CachePath, "/raw/substitutions.json");

            Log.v(LogTags.Caching, "saving raw substitutions data...");
            try (FileOutputStream out = new FileOutputStream(substitutionsFile)) {
                out.write(substitutionsJson.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {

        }
    }

    public Pair<String, String> loadRawData(){
        try {
            File timetableFile = new File(Utils.CachePath, "/raw/timetable.json");
            String timetable = Utils.readAllText(timetableFile);

            File substitutionsFile = new File(Utils.CachePath, "/raw/substitutions.json");
            String substitutions = Utils.readAllText(substitutionsFile);
            return new Pair<>(timetable, substitutions);

        } catch (IOException e) {
            return null;
        }
    }
    public boolean doesRawCacheExist(){
        return new File(Utils.CachePath, "/raw/timetable.json").exists() && new File(Utils.CachePath, "/raw/substitutions.json").exists();
    }
}
