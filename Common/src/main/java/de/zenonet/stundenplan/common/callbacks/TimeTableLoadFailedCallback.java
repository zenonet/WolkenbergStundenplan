package de.zenonet.stundenplan.common.callbacks;

import de.zenonet.stundenplan.common.ResultType;

public interface TimeTableLoadFailedCallback {
    void errorOccurred(ResultType error);
}
