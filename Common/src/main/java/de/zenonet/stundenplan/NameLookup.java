package de.zenonet.stundenplan;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import de.zenonet.stundenplan.common.Utils;

public class NameLookup {

    public String lookupDirectory;
    public JSONObject LookupData;

    public String lookupSubjectName(int subjectId) throws JSONException {
        return LookupData.getJSONObject("Subject").getJSONObject(String.valueOf(subjectId)).getString("DESCRIPTION");
    }

    public String lookupSubjectShortName(int subjectId) throws JSONException {
        return LookupData.getJSONObject("Subject").getJSONObject(String.valueOf(subjectId)).getString("NAME");
    }

    public String lookupTeacher(int teacherId) throws JSONException {
        return LookupData.getJSONObject("Teacher").getJSONObject(String.valueOf(teacherId)).getString("DESCRIPTION");
    }

    public String lookupRoom(int roomId) throws JSONException {

        return LookupData.getJSONObject("Room").getJSONObject(String.valueOf(roomId)).getString("NAME");
    }

    public void loadLookupData() throws IOException {
        try {
            LookupData = new JSONObject(Utils.readAllText(new File(lookupDirectory, "lookup.json")));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isLookupDataAvailable(){
        return new File(lookupDirectory, "lookup.json").exists();
    }

    public void saveLookupFile(String lookupData) {
        try {
            LookupData = new JSONObject(lookupData);
            File file = new File(lookupDirectory, "lookup.json");
            Utils.writeAllText(file, lookupData);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
