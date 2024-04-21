package de.zenonet.stundenplan.callbacks;

import de.zenonet.stundenplan.timetableManagement.TimeTable;

public interface TimeTableFetchedCallback {
     void timeTableFetched(TimeTable timeTable);
}
