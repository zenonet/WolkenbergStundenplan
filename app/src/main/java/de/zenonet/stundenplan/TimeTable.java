package de.zenonet.stundenplan;

public class TimeTable {
    public Lesson[][] Lessons;
    public transient boolean isFromCache;
    public transient boolean isCacheStateConfirmed;
    public TimeTable(){
        Lessons = new Lesson[5][];
    }}
