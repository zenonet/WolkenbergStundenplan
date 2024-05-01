package de.zenonet.stundenplan.common.models;

import java.io.Serializable;

public class User implements Serializable {
    public int id;
    public String firstname;
    public String lastname;
    public UserType type;

    public String getFullName(){
        return firstname + " " + lastname;
    }
}
