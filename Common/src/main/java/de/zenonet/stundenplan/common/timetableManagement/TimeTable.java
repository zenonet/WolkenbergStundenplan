package de.zenonet.stundenplan.common.timetableManagement;

import androidx.annotation.Keep;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import de.zenonet.stundenplan.common.TimeTableSource;

public final class TimeTable {
    public Lesson[][] Lessons;
    public LocalDateTime lastConfirmedDate;
    public transient TimeTableSource source;
    public transient boolean isCacheStateConfirmed;
    public long CounterValue;
    @Keep
    public TimeTable(){
        Lessons = new Lesson[5][];
    }

    public static final int assumedValidityDurationInMillis =  1000 * 60 * 2;
    public boolean isGoodEnough(){
        return ChronoUnit.MILLIS.between(lastConfirmedDate, LocalDateTime.now()) <= assumedValidityDurationInMillis;
    }
}
