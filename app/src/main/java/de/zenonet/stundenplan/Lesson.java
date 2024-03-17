package de.zenonet.stundenplan;

import java.time.LocalTime;

public class Lesson {
    public String Teacher;
    public String Subject;
    public String SubjectShortName;
    public String Room;
    public LessonType Type = LessonType.Regular;
    public transient LocalTime StartTime;
    public transient LocalTime EndTime;

    public boolean isTakingPlace(){
        return Type == LessonType.Cancelled || Type == LessonType.Absent || Type == LessonType.Holiday;
    }
}
