package de.zenonet.stundenplan.common;

public class DataNotAvailableException extends Exception {
    public DataNotAvailableException(String message) {
        super(message);
    }

    public DataNotAvailableException() {
    }
}
