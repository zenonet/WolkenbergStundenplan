package de.zenonet.stundenplan.common.callbacks;

import de.zenonet.stundenplan.common.timetableManagement.TimeTable;

public interface TimeTableFetchedCallback {
     void timeTableFetched(TimeTable timeTable);
}
