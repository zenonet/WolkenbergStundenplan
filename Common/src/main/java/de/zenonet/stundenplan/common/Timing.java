package de.zenonet.stundenplan.common;

import androidx.preference.PreferenceManager;

import java.time.LocalTime;
import java.util.Calendar;

public abstract class Timing {

    public static int DayOfWeekOverride = -1;
    public static LocalTime TimeOverride = null;//LocalTime.of(9, 36);
    public static LocalTime getCurrentTime() {
        if(TimeOverride != null) return TimeOverride;
        return LocalTime.now(); // offset so that actual 15:00 is 8:00
    }

    public static int getCurrentDayOfWeek() {

        if(DayOfWeekOverride != -1) return DayOfWeekOverride;
        return (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 2) % 7;
    }

    public static int getRelevantWeekOfYear(){
        int weekOfYear = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR);

        int dayOfWeek = getCurrentDayOfWeek();
        if (dayOfWeek > 4 || dayOfWeek < 0) weekOfYear++;
        return weekOfYear;
    }
}
