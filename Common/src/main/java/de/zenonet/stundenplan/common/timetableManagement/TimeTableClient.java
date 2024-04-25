package de.zenonet.stundenplan.common.timetableManagement;

import de.zenonet.stundenplan.common.models.User;

public interface TimeTableClient {
    public TimeTable getCurrentTimeTable() throws TimeTableLoadException;

    public TimeTable getTimeTableForWeek(int week) throws TimeTableLoadException;

    public User getUser() throws UserLoadException;
}
