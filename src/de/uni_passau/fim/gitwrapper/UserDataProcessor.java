package de.uni_passau.fim.gitwrapper;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * The UserDataSerializer helps with keeping track of UserData (including the email, which is not provided directly by
 * GitHub).
 */
public class UserDataProcessor implements JsonDeserializer<UserData> {

    private static final Logger LOG = Logger.getLogger(CommitProcessor.class.getCanonicalName());

    public static final UserData DUMMY_USER = new UserData();

    private static Map<String, UserData> strictUsersByUsername = new ConcurrentHashMap<>();
    private static Map<String, UserData> guessedUsersByUsername = new ConcurrentHashMap<>();
    private static Map<String, UserData> strictUsersByName = new ConcurrentHashMap<>();
    private static Map<String, UserData> guessedUsersByName = new ConcurrentHashMap<>();
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
        Map<String, UserData> lookupList = repo.allowGuessing() ? guessedUsersByUsername : strictUsersByUsername;

        String username = json.getAsJsonObject().get("login").getAsString();
        if (lookupList.containsKey(username)) return lookupList.get(username);

        return buildAndInsertUser(username, json.getAsJsonObject().get("url").getAsString());
    }

    /**
     * Constructs a new UserData instance.
     *
     * The email is determined in multiple steps, first by looking if a public email is set on the profile and if not,
     * tries to guess the email by looking at the history for this user and getting the email which is used most often
     * in the list of pushed commits while the corresponding name is set to the GitHub username or name.
     *
     * @param url
     *         the url to the user profile
     * @return the new UserData instance representing the user
     */
    private UserData buildAndInsertUser(String username, String url) {
        Optional<String> jsonData = repo.getJSONStringFromURL(url);
        if (!jsonData.isPresent()) {
            LOG.warning("Could not get information about user '" + username + "'");
            return DUMMY_USER;
        }
        JsonElement data = parser.parse(jsonData.get());
        UserData user = new UserData();
        user.username = username;
        JsonElement name = data.getAsJsonObject().get("name");
        if (!(name instanceof JsonNull)) {
            user.name = name.getAsString();
        }

        /////EMAIL///// >
        user.email = "";
        // first look at profile
        JsonElement email = data.getAsJsonObject().get("email");
        if (email != null && !(email instanceof JsonNull)) {
            user.email = email.getAsString();
        }

        // if we want to guess for emails, look at user history
        boolean guess = repo.allowGuessing();
        if (guess) {
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
            if (!posMails.isEmpty()) {

                // get email with most entries
                posMails.sort(Comparator.comparingInt(Map.Entry::getValue));
                user.email = posMails.get(posMails.size() - 1).getKey();
            }
        } /////EMAIL///// <

        // Finally insert and return
        (guess ? guessedUsersByUsername : strictUsersByUsername).put(username, user);
        if (user.name != null) {
            (guess ? guessedUsersByName : strictUsersByName).put(user.name, user);
        }

        return user;
    }

    /**
     * Get a UserData instance by its GitHub username.
     *
     * @param username
     *         the username
     * @return optically the UserData, or an empty Optional if that user is not known to GitHub
     */
    Optional<UserData> getUserByUsername(String username) {
        if (username == null) return Optional.empty();
        Map<String, UserData> lookupList = repo.allowGuessing() ? guessedUsersByUsername : strictUsersByUsername;
        UserData user = lookupList.get(username);
        if (user != null) return Optional.of(user);
        return Optional.ofNullable(buildAndInsertUser(username, "https://api.github.com/users/" + username));
    }

    /**
     * Get a UserData instance by its name as known to the local git repository.
     *
     * @param name
     *         the users name
     * @return optically the UserData, or an empty Optional if that name has not yet been in mapped to a user, or if
     * the mapping is not unique.
     */
    Optional<UserData> getUserByName(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable((repo.allowGuessing() ? guessedUsersByName : strictUsersByName).get(name));
    }
}
