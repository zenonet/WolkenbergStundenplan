package de.zenonet.stundenplan.common.timetableManagement;

import androidx.annotation.Keep;

@Keep
public final class Post {
    public int PostId;
    public String Creator;
    public String Title;
    public String Text;
    public String[] Images;

    @Keep
    public Post(){

    }
}
