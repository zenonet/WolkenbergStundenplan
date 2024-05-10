package de.zenonet.stundenplan.common.models;

import androidx.annotation.Keep;

import java.io.Serializable;

@Keep
public final class User implements Serializable {
    public int id;
    public String firstname;
    public String lastname;
    public UserType type;

    public String getFullName(){
        return firstname + " " + lastname;
    }
    @Keep
    public User(){

    }
}
