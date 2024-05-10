package de.zenonet.stundenplan.common;

import java.time.LocalTime;
import java.util.Calendar;

public abstract class Timing {
    public static LocalTime getCurrentTime() {
        return LocalTime.now();
    }

    public static int getCurrentDayOfWeek() {
        return (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 2) % 7;
    }
}
