package de.zenonet.stundenplan.common;

import androidx.annotation.NonNull;

import java.util.Calendar;
import java.util.Locale;
import java.util.Objects;

public class Week {
    public Week(int year, int weekOfYear) {
        Year = year;
        WeekOfYear = weekOfYear;
    }

    public int Year;
    public int WeekOfYear;

    public Calendar getMonday(){
        Calendar cal = Calendar.getInstance();
        cal.setFirstDayOfWeek(Calendar.MONDAY);
        cal.set(Calendar.YEAR, Year);
        cal.set(Calendar.WEEK_OF_YEAR, WeekOfYear);
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        return cal;
    }
    public String getCacheKey(){
        return String.format(Locale.GERMANY,"%d-%d", Year, WeekOfYear);
    }

    @NonNull
    @Override
    public String toString() {
        return getCacheKey();
    }

    public static Week fromCalendar(Calendar cal){
        cal.setFirstDayOfWeek(Calendar.MONDAY);
        cal.set(Calendar.DAY_OF_WEEK, 0);
        return new Week(cal.get(Calendar.YEAR), cal.get(Calendar.WEEK_OF_YEAR));
    }

    public Week getSucceedingWeek(){
        Calendar c = getMonday();
        c.add(Calendar.WEEK_OF_YEAR, 1);
        return fromCalendar(c);
    }

    public Week getPreceedingWeek(){
        Calendar c = getMonday();
        c.add(Calendar.WEEK_OF_YEAR, -1);
        return fromCalendar(c);
    }

    public Week plusWeeks(int offset){
        Calendar c = getMonday();
        c.add(Calendar.WEEK_OF_YEAR, offset);
        return fromCalendar(c);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Week week = (Week) o;
        return Year == week.Year && WeekOfYear == week.WeekOfYear;
    }

    @Override
    public int hashCode() {
        return Objects.hash(Year, WeekOfYear);
    }
}
