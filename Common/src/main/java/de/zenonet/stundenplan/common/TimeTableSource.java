package de.zenonet.stundenplan.common;

import androidx.annotation.Keep;

@Keep
public enum TimeTableSource {
    Cache,
    RawCache,
    Api,
    Preview
}
