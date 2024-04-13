package de.zenonet.stundenplan;

import androidx.annotation.Keep;

@Keep
public enum LessonType {
    Regular,
    Cancelled,
    Substitution,
    RoomSubstitution,
    Absent,
    ExtraLesson,
    Holiday,
}
