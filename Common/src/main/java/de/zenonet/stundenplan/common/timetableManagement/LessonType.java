package de.zenonet.stundenplan.common.timetableManagement;

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
