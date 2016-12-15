package de.uni_passau.fim.heck.githubinterface.datadefinitions;

import com.google.gson.annotations.SerializedName;

public class EventData {

    @SerializedName("actor")
    public UserData user;
}
