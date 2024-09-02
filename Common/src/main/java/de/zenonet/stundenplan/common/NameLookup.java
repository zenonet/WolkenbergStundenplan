package de.zenonet.stundenplan.common;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;


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

    /**
     * Saves json lookup data
     * @param lookupData the json lookup data to save
     * @return whether the data changed since the last time saving
     */
    public boolean saveLookupFile(String lookupData) {
        try {
            // Check if anything changed
            File hashFile = new File(lookupDirectory, "lookup.hash");
            int localHash = hashFile.exists() ? Integer.parseInt(Utils.readAllText(hashFile)) : -1;
            int newHash = lookupData.hashCode();
            if(localHash == newHash){
                return false;
            }

            LookupData = new JSONObject(lookupData);
            File file = new File(lookupDirectory, "lookup.json");
            Utils.writeAllText(file, lookupData);

            Utils.writeAllText(hashFile, String.valueOf(newHash));
            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
