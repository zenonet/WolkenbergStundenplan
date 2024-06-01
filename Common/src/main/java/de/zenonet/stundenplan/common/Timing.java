package de.zenonet.stundenplan.common;

import androidx.preference.PreferenceManager;

import java.time.LocalTime;
import java.util.Calendar;

public abstract class Timing {

    private static final int DayOfWeekOverride = -1;
    private static final LocalTime TimeOverride = null;//LocalTime.of(9, 36);
    public static LocalTime getCurrentTime() {
        if(TimeOverride != null || PreferenceManager.getDefaultSharedPreferences(StundenplanApplication.application).getBoolean("overrideTime", false)) return TimeOverride;
        return LocalTime.now(); // offset so that actual 15:00 is 8:00
    }

    public static int getCurrentDayOfWeek() {

        if(DayOfWeekOverride != -1 || PreferenceManager.getDefaultSharedPreferences(StundenplanApplication.application).getBoolean("overrideDayOfWeek", false)) return DayOfWeekOverride;
        return (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 2) % 7;
    }
}
