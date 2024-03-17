package de.zenonet.stundenplan;

public class TimeTable {
    public Lesson[][] Lessons;
    public transient boolean isFromCache;

    public TimeTable(){
        Lessons = new Lesson[5][];
    }
}
