package de.zenonet.stundenplan;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class TimeTable {
    public Lesson[][] Lessons;
    public LocalDateTime lastConfirmedDate;
    public transient boolean isFromCache;
    public transient boolean isCacheStateConfirmed;
    public TimeTable(){
        Lessons = new Lesson[5][];
    }

    public static final int assumedValidityDurationInMillis =  1000 * 60 * 2;
    public boolean isGoodEnough(){
        return ChronoUnit.MILLIS.between(lastConfirmedDate, LocalDateTime.now()) <= assumedValidityDurationInMillis;
    }
}
