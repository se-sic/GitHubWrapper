package de.uni_passau.fim.noemmer.run_issues;

import com.google.gson.*;
import de.uni_passau.fim.heck.githubinterface.GitHubRepository;
import de.uni_passau.fim.seibt.gitwrapper.process.ToolNotWorkingException;
import de.uni_passau.fim.seibt.gitwrapper.repo.GitWrapper;
import de.uni_passau.fim.seibt.gitwrapper.repo.Repository;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;

import java.io.*;
import java.util.*;

/**
 *
 */
public class IssuesMain {

    private static long projectId;
    private static String idServiceUrl;
    private static HashMap<String, JsonObject> buffer = new HashMap<>();

    public static void main(String args[]) {
        GitWrapper git;
        if (args.length != 4) {
            System.out.println("usage: ProjectID IDServiceURL ResultsPath RepositoryPath");
            return;
        }
        projectId = Long.parseLong(args[0]);
        idServiceUrl = args[1];
        String resdir = args[2];
        String repoPath = args[3];
        try {
            git = new GitWrapper("git"); // Or /usr/bin/git, C:\Program Files\Git\bin\git.
        } catch (ToolNotWorkingException ex) {
            // Handle the case that git can not be called using the supplied command.
            return;
        }
        GitHubRepository repo;
        Optional<Repository> optRepo = git.importRepository(new File(repoPath));
        if (optRepo.isPresent()) {
            List<String> tokens = new ArrayList<>();
            tokens.add("020755268f1246109600b9c62d10d2ba0df37ee0");
            tokens.add("747aab46b5b6b973a4f1ebc87d2706d1f14b23f7");
            tokens.add("892a4138acd9134d20aac1b8850ab823369849c7");
            repo = new GitHubRepository(optRepo.get(), git, tokens);
            repo.sleepOnApiLimit(true);
        } else {
            System.out.println("Cloning failed");
            return;
        }
        repo.allowGuessing(true);
        System.out.println("Starting to build Json.");
        JsonParser parser = new JsonParser();
        StringBuilder issues = new StringBuilder("");
        repo.getIssues(true).ifPresent(issueData -> issueData.forEach(issue -> {
            JsonObject issueJson = (parser.parse(repo.serialize(issue))).getAsJsonObject();
            insertUserIds(issueJson);
            removeExcess(issueJson);
            StringBuilder issueString = new StringBuilder();
            for (JsonElement event : issueJson.get("eventsList").getAsJsonArray()) {
                JsonObject eventObject = event.getAsJsonObject();
                if(!(eventObject.get("user") instanceof JsonNull)) {
                    //Add issue Data
                    issueString.append(issueJson.get("number")).append(';');
                    issueString.append(issueJson.get("state")).append(';');
                    issueString.append(issueJson.get("created_at")).append(';');
                    issueString.append(issueJson.get("closed_at")).append(';');
                    issueString.append(issueJson.get("isPullRequest")).append(';');

                    //Add event data
                    issueString.append(eventObject.get("user")).append(';');
                    issueString.append(eventObject.get("username")).append(';');
                    issueString.append(eventObject.get("usermail")).append(';');
                    issueString.append(eventObject.get("created_at")).append(';');
                    issueString.append(eventObject.get("event"));

                    issueString.append("\n");
                }
            }
            issues.append(issueString);
        }));

        try {
            PrintWriter out = new PrintWriter(resdir + "/issues.list", "UTF-8");
            out.print(issues);
            out.flush();
            out.close();
        } catch (FileNotFoundException | UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    private static void removeExcess(JsonObject json) {
        if (json.has("user") && json.get("user") instanceof JsonObject) {
            JsonElement buffer = json.get("user").getAsJsonObject().get("userId");
            JsonElement authorName = json.get("user").getAsJsonObject().get("name");
            JsonElement authorMail = json.get("user").getAsJsonObject().get("email");
            json.add("user", buffer);
            json.add("username", authorName);
            json.add("usermail", authorMail);
        }

        if (json.has("title"))
            json.remove("title");

        if (json.has("body"))
            json.remove("body");

        if (json.has("url"))
            json.remove("url");

        if (json.has("commentsList"))
            ((JsonArray) json.get("commentsList")).forEach(j -> {
                removeExcess(j.getAsJsonObject());
                (j.getAsJsonObject()).addProperty("event", "commented");
            });

        if (json.has("eventsList"))
            ((JsonArray) json.get("eventsList")).forEach(j ->
                    removeExcess(j.getAsJsonObject()));

        if (json.has("relatedCommits"))
            ((JsonArray) json.get("relatedCommits")).forEach(commit -> {
                (commit.getAsJsonObject()).remove("time");
                (commit.getAsJsonObject()).remove("author");
            });

        if (json.has("commentsList") && json.has("eventsList")) {
            JsonObject createdEvent = new JsonObject();
            createdEvent.add("user", json.get("user"));
            createdEvent.add("username", json.get("username"));
            createdEvent.add("usermail", json.get("usermail"));
            createdEvent.add("created_at", json.get("created_at"));
            createdEvent.addProperty("event", "created");
            json.get("eventsList").getAsJsonArray().add(createdEvent);
            json.get("eventsList").getAsJsonArray().addAll(json.get("commentsList").getAsJsonArray());
            json.remove("commentsList");
        }
    }

    private static void insertUserIds(JsonObject issueJson) {
        Set<Map.Entry<String, JsonElement>> entries = issueJson.entrySet();
        for (Map.Entry<String, JsonElement> entry : entries) {
            if (entry.getValue() instanceof JsonArray) {
                ((JsonArray) entry.getValue()).forEach(j -> {
                    if (j instanceof JsonObject) {
                        insertUserIds((JsonObject) j);
                    }
                });
            }
            if (entry.getKey().equals("user") && entry.getValue() instanceof JsonObject) {
                JsonObject user = (JsonObject) entry.getValue();
                JsonObject idServiceUser;
                if (buffer.containsKey(user.get("username").getAsString())) {
                    idServiceUser = buffer.get(user.get("username").getAsString());
                } else {
                    idServiceUser = getPerson(user.get("username").getAsString(), user.get("email").getAsString(),
                            !(user.get("name") instanceof JsonNull) ? user.get("name").getAsString() : null);
                    buffer.put(user.get("username").getAsString(), idServiceUser);
                }
                user.add("userId", idServiceUser.get("id"));
                user.add("name", idServiceUser.get("name"));
                user.add("email", idServiceUser.get("email1"));
            }
        }
    }


    private static JsonObject getPerson(String username, String email, String name) {
        JsonObject user = new JsonObject();
        HttpClient client = HttpClientBuilder.create().build();
        HttpPost post = new HttpPost("http://" + idServiceUrl + "/post_user_id");
        JsonParser parser = new JsonParser();
        try {
            HttpResponse response = getHttpResponseId(name == null ? username : name, email, client, post);
            BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            reader.close();
            if (builder.toString().contains("error")) {
                response = getHttpResponseId(name == null ? username : name, username + "@default.com", client, post);
                reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
                builder = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
                reader.close();
            }
            user = parser.parse(builder.toString()).getAsJsonObject();
            HttpGet get = new HttpGet("http://" + idServiceUrl + "/getUser/" + user.get("id"));
            response = client.execute(get);
            reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
            builder = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            reader.close();
            System.out.println("Found User " + builder.toString());
            user = parser.parse(builder.toString()).getAsJsonArray().get(0).getAsJsonObject();

        } catch (IOException e) {
            e.printStackTrace();
        }
        return user;
    }

    private static HttpResponse getHttpResponseId(String username, String email, HttpClient client, HttpPost post) throws IOException {
        List<NameValuePair> nameValuePairs = new ArrayList<>(1);
        nameValuePairs.add(new BasicNameValuePair("projectID", Long.toString(projectId)));
        nameValuePairs.add(new BasicNameValuePair("name", username));
        nameValuePairs.add(new BasicNameValuePair("email", email));
        post.setEntity(new UrlEncodedFormEntity(nameValuePairs, "UTF-8"));
        return client.execute(post);
    }

}
