package de.zenonet.stundenplan.callbacks;

import de.zenonet.stundenplan.TimeTable;

public interface TimeTableLoadedCallback {
    void timeTableLoaded(TimeTable timeTable);
}
