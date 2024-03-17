package de.zenonet.stundenplan;

import java.time.LocalTime;

public class Lesson {
    public String Teacher;
    public String Subject;
    public String SubjectShortName;
    public String Room;
    public LessonType Type = LessonType.Regular;
    public LocalTime StartTime;
    public LocalTime EndTime;
}
