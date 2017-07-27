package de.uni_passau.fim.heck.githubinterface;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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

    private static Map<String, UserData> strictUsers = new ConcurrentHashMap<>();
    private static Map<String, UserData> guessedUsers = new ConcurrentHashMap<>();
    private final GitHubRepository repo;

    UserDataDeserializer(GitHubRepository repo) {
        this.repo = repo;
    }

    @Override
    public UserData deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        String username = json.getAsJsonObject().get("login").getAsString();

        Map<String, UserData> lookupList = repo.allowGuessing() ? guessedUsers : strictUsers;

        if (lookupList.containsKey(username)) return lookupList.get(username);

        UserData user = new UserData();
        user.username = username;
        insertNameAndMail(json, user);
        lookupList.put(username, user);
        return user;
    }

    /**
     * Tries getting the email, first by looking if a public email is set on the profile and if not, tries to guess the
     * email by looking at the history for this user and getting the email which is used most often in the list of
     * pushed commits.
     *
     * @param user the JsonElement representing the data about a user
     */
    private void insertNameAndMail(JsonElement json, UserData user) {
        JsonParser parser = new JsonParser();

        // first look at profile
        Optional<String> userData = repo.getJSONStringFromURL(json.getAsJsonObject().get("url").getAsString());
        JsonElement data = parser.parse(userData.orElse(""));
        JsonElement email = data.getAsJsonObject().get("email");
        JsonElement name = data.getAsJsonObject().get("name");
        if (!(name instanceof JsonNull)) {
            user.name = name.getAsString();
        }
        if (!(email instanceof JsonNull)) {
            user.email = email.getAsString();
            return;
        }

        // if we don't want to guess for emails, stop here and don't look at user history
        if (!repo.allowGuessing()) {
            user.email = "";
            return;
        }

        // get list of recent pushes
        Optional<String> eventsData = repo.getJSONStringFromURL(json.getAsJsonObject().get("events_url").getAsString().replaceAll("\\{.*}$", ""));
        data = parser.parse(eventsData.orElse(""));

        List<String> emails = new ArrayList<>();
        data.getAsJsonArray().forEach(e -> {
            JsonObject event = e.getAsJsonObject();
            if (event.getAsJsonPrimitive("type").getAsString().equals("PushEvent")) {
                event.getAsJsonObject("payload")
                        .getAsJsonArray("commits")
                        .forEach(commit -> {
                                    if (commit.getAsJsonObject().getAsJsonObject("author").getAsJsonPrimitive("name").getAsString().equals(user.username) ||
                                            commit.getAsJsonObject().getAsJsonObject("author").getAsJsonPrimitive("name").getAsString().equals(user.name))
                                        emails.add(commit.getAsJsonObject().getAsJsonObject("author")
                                                .getAsJsonPrimitive("email").getAsString());
                                }
                        );
            }
        });
        if (emails.isEmpty()) {
            user.email = "";
            return;
        }
        Map<String, Long> counts = emails.stream().collect(Collectors.groupingBy(e -> e, Collectors.counting()));

        // get email with most entries
        user.email = Collections.max(counts.entrySet(), Comparator.comparingLong(Map.Entry::getValue)).getKey();
        System.out.println("Success: " + user.email);
    }
}
