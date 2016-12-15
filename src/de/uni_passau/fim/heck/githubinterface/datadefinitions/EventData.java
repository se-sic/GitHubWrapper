package de.uni_passau.fim.heck.githubinterface.datadefinitions;

import java.util.Date;

import com.google.gson.annotations.SerializedName;

public class EventData {

    @SerializedName("actor")
    public UserData user;
    public Date created_at;
    public String event;

    public class DefaultEventData extends EventData { }

    public class LabeledEventData extends EventData {
        public LabelData label;

        public class LabelData {
            public String name;
        }
    }

    public class ReferencedEventData extends EventData {
        public String commit_id;
    }
}
