package de.zenonet.stundenplan.timetableManagement;

import de.zenonet.stundenplan.DataNotAvailableException;

public class TimeTableLoadException extends DataNotAvailableException {
    public Exception causingException;
    public TimeTableLoadException(Exception causingException){
        this.causingException = causingException;
    }
    public TimeTableLoadException(){
        causingException = null;
    }
}
