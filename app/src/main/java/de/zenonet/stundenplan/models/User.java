package de.zenonet.stundenplan.models;

import java.io.Serializable;

public class User implements Serializable {
    public int id;
    public String firstname;
    public String lastname;
    public UserType type;
}
