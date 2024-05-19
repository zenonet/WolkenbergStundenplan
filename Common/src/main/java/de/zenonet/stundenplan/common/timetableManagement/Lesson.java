package de.zenonet.stundenplan.common.timetableManagement;

import androidx.annotation.Keep;

import java.time.LocalTime;
@Keep
public final class Lesson {
    public String Teacher;
    public String Subject;
    public String SubjectShortName;
    public String Room;
    public LessonType Type = LessonType.Regular;
    public transient LocalTime StartTime;
    public transient LocalTime EndTime;

    public boolean isTakingPlace() {
        return Type != LessonType.Cancelled && Type != LessonType.Absent && Type != LessonType.Holiday;
    }

    public boolean isSubstitution() {
        return Type == LessonType.Substitution || Type == LessonType.RoomSubstitution;
    }

    @Keep
    public Lesson() {
    }
}
