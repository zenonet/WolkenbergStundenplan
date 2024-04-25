package de.zenonet.stundenplan;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

public class NameLookup {

    public String lookupDirectory;
    public static JSONObject FallbackLookup;

    public static void setFallbackLookup(String string){
        try {
            FallbackLookup = new JSONObject(string);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

    }
    public String lookupSubjectName(int subjectId) throws JSONException {
        return FallbackLookup.getJSONObject("Subject").getJSONObject(String.valueOf(subjectId)).getString("DESCRIPTION");
    }

    public String lookupSubjectShortName(int subjectId) throws JSONException {
        return FallbackLookup.getJSONObject("Subject").getJSONObject(String.valueOf(subjectId)).getString("NAME");
    }

    public String lookupTeacher(int teacherId) throws JSONException {
        return FallbackLookup.getJSONObject("Teacher").getJSONObject(String.valueOf(teacherId)).getString("DESCRIPTION");
    }

    public String lookupRoom(int roomId) throws JSONException {

        return FallbackLookup.getJSONObject("Room").getJSONObject(String.valueOf(roomId)).getString("NAME");
    }

    private void saveLookupFile(String lookupData){
        try {
            File gpxfile = new File(lookupDirectory, "lookup.json");
            FileWriter writer = new FileWriter(gpxfile);
            writer.append(lookupData);
            writer.flush();
            writer.close();
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
