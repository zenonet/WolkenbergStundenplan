package de.zenonet.stundenplan.common.timetableManagement;

import androidx.annotation.Keep;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

import de.zenonet.stundenplan.common.TimeTableSource;

@Keep
public final class TimeTable {
    public Lesson[][] Lessons;
    public LocalDateTime lastConfirmedDate;
    public transient TimeTableSource source;
    public transient boolean isCacheStateConfirmed;
    public transient LocalDateTime timeOfConfirmation;
    public long CounterValue;
    @Keep
    public TimeTable(){
        Lessons = new Lesson[5][];
    }

    public static final int assumedValidityDurationInMillis =  1000 * 60 * 2;
    public boolean isGoodEnough(){
        return ChronoUnit.MILLIS.between(lastConfirmedDate, LocalDateTime.now()) <= assumedValidityDurationInMillis;
    }

    public boolean hasDataForLesson(int dayOfWeek, int period){
        return Lessons != null && dayOfWeek >= 0 && dayOfWeek < 5 && Lessons[dayOfWeek].length > period && Lessons[dayOfWeek][period] != null;
    }

    public boolean isEmpty(){
        for (Lesson[] day : Lessons) {
            if (day.length > 0) return false;
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TimeTable timeTable = (TimeTable) o;
        return Arrays.deepEquals(Lessons, timeTable.Lessons);
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(Lessons);
    }
}
