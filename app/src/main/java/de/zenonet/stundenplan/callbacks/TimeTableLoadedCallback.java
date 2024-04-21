package de.zenonet.stundenplan.callbacks;

import de.zenonet.stundenplan.timetableManagement.TimeTable;

public interface TimeTableLoadedCallback {
    void timeTableLoaded(TimeTable timeTable);
}
