package de.zenonet.stundenplan.timetableManagement;

import de.zenonet.stundenplan.models.User;

public interface TimeTableClient {
    public TimeTable getCurrentTimeTable() throws TimeTableLoadException;

    public TimeTable getTimeTableForWeek(int week) throws TimeTableLoadException;

    public User getUser() throws UserLoadException;
}
