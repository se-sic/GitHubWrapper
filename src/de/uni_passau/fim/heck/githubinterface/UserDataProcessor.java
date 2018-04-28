package de.uni_passau.fim.heck.githubinterface;

import com.google.gson.*;
import de.uni_passau.fim.heck.githubinterface.datadefinitions.UserData;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The UserDataSerializer helps with keeping track of UserData (including the email, which is not provided directly by
 * GitHub).
 */
public class UserDataProcessor implements JsonDeserializer<UserData> {

    private static Map<String, UserData> strictUsers = new ConcurrentHashMap<>();
    private static Map<String, UserData> guessedUsers = new ConcurrentHashMap<>();
    private static final JsonParser parser = new JsonParser();

    private final GitHubRepository repo;

    /**
     * Creates a new UserDataDeserializer for the given repo
     *
     * @param repo
     *         the repo
     */
    UserDataProcessor(GitHubRepository repo) {
        this.repo = repo;
    }

    @Override
    public UserData deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        Map<String, UserData> lookupList = repo.allowGuessing() ? guessedUsers : strictUsers;

        String username = json.getAsJsonObject().get("login").getAsString();
        if (lookupList.containsKey(username)) return lookupList.get(username);

        Optional<String> userProfile = repo.getJSONStringFromURL(json.getAsJsonObject().get("url").getAsString());
        JsonElement userData = parser.parse(userProfile.orElse(""));
        UserData user = buildUser(username, userData);
        lookupList.put(username, user);
        return user;
    }

    /**
     * Constructs a new UserData instance.
     *
     * The email is determined in multiple steps, first by looking if a public email is set on the profile and if not,
     * tries to guess the email by looking at the history for this user and getting the email which is used most often
     * in the list of pushed commits while the corresponding name is set to the GitHub username or name.
     *
     * @param data
     *         the JsonElement representing the data from the user profile
     * @return the new UserData instance representing the user
     */
    private UserData buildUser(String username, JsonElement data) {
        UserData user = new UserData();
        user.username = username;
        JsonElement name = data.getAsJsonObject().get("name");
        if (!(name instanceof JsonNull)) {
            user.name = name.getAsString();
        }

        /////EMAIL/////
        user.email = "";
        // first look at profile
        JsonElement email = data.getAsJsonObject().get("email");
        if (!(email instanceof JsonNull)) {
            user.email = email.getAsString();
            return user;
        }

        // if we don't want to guess for emails, stop here and don't look at user history
        if (!repo.allowGuessing()) {
            return user;
        }

        // get list of recent pushes
        Optional<String> eventsData = repo.getJSONStringFromURL(data.getAsJsonObject().get("events_url").getAsString().replaceAll("\\{.*}$", ""));
        JsonElement userData = parser.parse(eventsData.orElse(""));

        Map<String, Integer> emails = new HashMap<>();
        userData.getAsJsonArray().forEach(e -> {
            JsonObject event = e.getAsJsonObject();
            if (event.getAsJsonPrimitive("type").getAsString().equals("PushEvent")) {
                event.getAsJsonObject("payload")
                        .getAsJsonArray("commits")
                        .forEach(commit -> {
                            JsonObject author = commit.getAsJsonObject().getAsJsonObject("author");
                            if (author.getAsJsonPrimitive("name").getAsString().equals(user.name)
                             || author.getAsJsonPrimitive("name").getAsString().equals(user.username)) {
                                emails.merge(commit.getAsJsonObject()
                                                .getAsJsonObject("author")
                                                .getAsJsonPrimitive("email")
                                                .getAsString(),
                                        1, (val, newVal) -> val + newVal);
                            }
                        });
            }
        });

        List<Map.Entry<String, Integer>> posMails = new ArrayList<>(emails.entrySet());
        if (posMails.isEmpty()) {
            return user;
        }

        // get email with most entries
        posMails.sort(Comparator.comparingInt(Map.Entry::getValue));
        user.email = posMails.get(posMails.size() - 1).getKey();

        return user;
    }
}
