package de.zenonet.stundenplan;

public class TimeTableLoadException extends DataNotAvailableException{
    public Exception causingException;
    public TimeTableLoadException(Exception causingException){
        this.causingException = causingException;
    }
    public TimeTableLoadException(){
        causingException = null;
    }
}
