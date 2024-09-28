package de.zenonet.stundenplan.common.timetableManagement;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import java.time.LocalTime;
import java.util.Objects;

@Keep
public final class Lesson {
    public String Teacher;
    public String Subject;
    public String SubjectShortName;
    public String Room;
    public LessonType Type = LessonType.Regular;
    public String Text;
    public transient LocalTime StartTime;
    public transient LocalTime EndTime;

    public boolean isTakingPlace() {
        return Type != LessonType.Cancelled && Type != LessonType.Absent && Type != LessonType.Holiday && Type != LessonType.Assignment;
    }

    public boolean isSubstitution() {
        return Type == LessonType.Substitution || Type == LessonType.RoomSubstitution;
    }

    @Keep
    public Lesson() {
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        // Only handle comparison to other Lessons
        if(obj == null || obj.getClass() != Lesson.class) return super.equals(obj);
        Lesson other = (Lesson) obj;
        return Objects.equals(other.Room, Room) && Objects.equals(other.SubjectShortName, SubjectShortName) && Objects.equals(other.Teacher, Teacher) && other.Type == Type;
    }

    public static boolean doesTakePlace(Lesson lesson){
        return lesson != null && lesson.isTakingPlace();
    }
}
