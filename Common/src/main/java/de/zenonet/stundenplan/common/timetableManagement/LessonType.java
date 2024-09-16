package de.zenonet.stundenplan.common.timetableManagement;

import androidx.annotation.Keep;

@Keep
public enum LessonType {
    Regular,
    Cancelled,
    Assignment,
    Substitution,
    RoomSubstitution,
    Absent,
    ExtraLesson,
    Holiday,
}
