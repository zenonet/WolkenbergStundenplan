package de.zenonet.stundenplan.common;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;

public abstract class Timing {

    public static int DayOfWeekOverride = -1;
    public static LocalTime TimeOverride = null;//LocalTime.of(9, 36);
    public static LocalTime getCurrentTime() {
        if(TimeOverride != null) return TimeOverride;
        return LocalTime.now(); // offset so that actual 15:00 is 8:00
    }

    public static DateTimeFormatter TimeFormatter = DateTimeFormatter.ofPattern("HH:mm");

    public static int getCurrentDayOfWeek() {

        if(DayOfWeekOverride != -1) return DayOfWeekOverride;
        return (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 2) % 7;
    }

    public static Week getRelevantWeekOfYear(){
        Calendar cal = Calendar.getInstance();

        int dayOfWeek = getCurrentDayOfWeek();
        if (dayOfWeek > 4 || dayOfWeek < 0) {
            cal.add(Calendar.DAY_OF_WEEK, 6);
        }
        return Week.fromCalendar(cal);
    }
}
