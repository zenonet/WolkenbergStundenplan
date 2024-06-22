package de.zenonet.stundenplan.common;

import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

public abstract class StatisticsManager {

    // Ensure that only the first render is actually reported
    private static boolean hasReported = false;
    /**
     * Reports how long the app took from the app entrypoint to the first time when the timetable is visible
     *
     * @param time the time in milliseconds
     */
    public static void reportTimetableTime(int time) {
        if(hasReported) return;

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(StundenplanApplication.application);
        int measurements = preferences.getInt("measurementCount", 0);
        float avg = preferences.getFloat("averageTimetableTime", 0);

        // new average = (total time 'till now + reported time) / measurements
        avg = (avg*measurements+time)/++measurements;

        preferences.edit()
                .putInt("measurementCount", measurements)
                .putFloat("averageTimetableTime", avg)
                .apply();
        hasReported = true;
    }

    /**
     * @return The average time the app takes from entrypoint to the first time the timetable is visible or -1 if there's no data
     */
    public static float getAverageTimetableTime(){
        return PreferenceManager.getDefaultSharedPreferences(StundenplanApplication.application).getFloat("averageTimetableTime", -1);
    }
}
