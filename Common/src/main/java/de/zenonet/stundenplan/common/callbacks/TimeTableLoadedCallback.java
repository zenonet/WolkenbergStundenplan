package de.zenonet.stundenplan.common.callbacks;

import de.zenonet.stundenplan.common.timetableManagement.TimeTable;

public interface TimeTableLoadedCallback {
    void timeTableLoaded(TimeTable timeTable);
}
