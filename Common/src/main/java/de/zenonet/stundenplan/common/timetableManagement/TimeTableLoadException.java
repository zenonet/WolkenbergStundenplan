package de.zenonet.stundenplan.common.timetableManagement;

import de.zenonet.stundenplan.common.DataNotAvailableException;

public class TimeTableLoadException extends DataNotAvailableException {
    public Exception causingException;
    public TimeTableLoadException(Exception causingException){
        this.causingException = causingException;
    }

    public TimeTableLoadException(String message) {
        super(message);
    }

    public TimeTableLoadException(){
        causingException = null;
    }
}
