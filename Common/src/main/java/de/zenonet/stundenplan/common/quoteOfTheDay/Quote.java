package de.zenonet.stundenplan.common.quoteOfTheDay;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

@Keep
public final class Quote {
    public String text;
    public String author;
    @Nullable
    public String classification;

    @Keep
    public Quote(){
    }
}
