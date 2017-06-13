package de.uni_passau.fim.heck.githubinterface;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import de.uni_passau.fim.heck.githubinterface.datadefinitions.UserData;

/**
 * The UserDataSerializer helps with keeping track of UserData (including the email, which is not provided directly by
 * GitHub).
 */
public class UserDataDeserializer implements JsonDeserializer<UserData> {

    private static Map<String, UserData> users = new HashMap<>();
    private final GitHubRepository repo;

    UserDataDeserializer(GitHubRepository repo) {
        this.repo = repo;
    }

    @Override
    public UserData deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        String username = json.getAsJsonObject().get("login").getAsString();
        if (users.containsKey(username)) return users.get(username);

        UserData user = new UserData();
        user.username = username;
        user.email = guessEmails(json);
        users.put(username, user);
        return user;
    }

    /**
     * Tries getting the email, first by looking if a public email is set on the profile and if not, tries to guess the
     * email by looking at the history for this user and getting the email which is used most often in the list of
     * pushed commits.
     *
     * @param user
     *         the JsonElement representing the data about a user
     * @return the most probable email for this user
     */
    private String guessEmails(JsonElement user) {
        JsonParser parser = new JsonParser();

        // first look at profile
        Optional<String> userData = repo.getJSONStringFromURL(user.getAsJsonObject().get("url").getAsString());
        JsonElement data = parser.parse(userData.orElse(""));
        JsonElement email = data.getAsJsonObject().get("email");
        if (!(email instanceof JsonNull)) return email.getAsString();

        // get list of recent pushes
        Optional<String> eventsData = repo.getJSONStringFromURL(user.getAsJsonObject().get("events_url").getAsString().replaceAll("\\{.*}$", ""));
        data = parser.parse(eventsData.orElse(""));

        Map<String, Integer> emails = new HashMap<>();
        data.getAsJsonArray().forEach(e -> {
            JsonObject event = e.getAsJsonObject();
            if (event.getAsJsonPrimitive("type").getAsString().equals("PushEvent")) {
                event.getAsJsonObject("payload")
                        .getAsJsonArray("commits")
                        .forEach(commit -> emails.merge(commit.getAsJsonObject()
                                .getAsJsonObject("author")
                                .getAsJsonPrimitive("email")
                                .getAsString(), 1, (val, newVal) -> val + newVal));
            }
        });

        List<Map.Entry<String, Integer>> posMails = new ArrayList<>(emails.entrySet());
        if (posMails.isEmpty()) return "";

        // get email with most entries
        posMails.sort(Comparator.comparingInt(Map.Entry::getValue));
        return posMails.get(posMails.size() - 1).getKey();
    }
}
