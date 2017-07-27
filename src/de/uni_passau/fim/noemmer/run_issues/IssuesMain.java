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
    private static HashMap<String, Long> buffer = new HashMap<>();

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
            repo = new GitHubRepository(optRepo.get(), git, "020755268f1246109600b9c62d10d2ba0df37ee0");
        } else {
            System.out.println("Cloning failed");
            return;
        }
        repo.allowGuessing(true);
        System.out.println("Starting to build Json.");
        JsonParser parser = new JsonParser();
        JsonArray issues = new JsonArray();
        repo.getIssues(true).ifPresent(issueData -> issueData.forEach(issue -> {
            JsonObject issueJson = parser.parse(repo.serialize(issue)).getAsJsonObject();
            insertUserIds(issueJson);
            removeExcess(issueJson);
            issues.add(issueJson);
        }));

        try {
            PrintWriter out = new PrintWriter(resdir + "/issues.json", "UTF-8");
            out.print(issues);
            out.flush();
            out.close();
        } catch (FileNotFoundException | UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    private static void removeExcess(JsonObject json) {
        if (json.has("user") && json.get("user") instanceof JsonObject) {
            JsonElement buffer = ((JsonObject) json.get("user")).get("userId");
            json.add("user", buffer);
        }

        if (json.has("title"))
            json.remove("title");

        if (json.has("body"))
            json.remove("body");

        if (json.has("url"))
            json.remove("url");

        if (json.has("commentsList"))
            ((JsonArray) json.get("commentsList")).forEach(j -> {
                removeExcess((JsonObject) j);
                ((JsonObject) j).addProperty("event", "commented");
            });

        if (json.has("eventsList"))
            ((JsonArray) json.get("eventsList")).forEach(j ->
                        removeExcess((JsonObject) j));

        if (json.has("relatedCommits"))
            ((JsonArray) json.get("relatedCommits")).forEach(commit -> {
                ((JsonObject) commit).remove("time");
                ((JsonObject) commit).remove("author");
            });

        if (json.has("commentsList") && json.has("eventsList")) {
            ((JsonArray) json.get("eventsList")).addAll((JsonArray) json.get("commentsList"));
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
                JsonPrimitive userId;
                System.out.println(user.toString());
                if (buffer.containsKey(user.get("username").getAsString())) {
                    userId = new JsonPrimitive(buffer.get(user.get("username").getAsString()));
                } else {
                    userId = new JsonPrimitive(getPerson(user.get("username").getAsString(), user.get("email").getAsString(),
                            !(user.get("name") instanceof JsonNull) ? user.get("name").getAsString() : null));
                    buffer.put(user.get("username").getAsString(), userId.getAsLong());
                }
                user.add("userId", userId);
            }
        }
    }


    private static long getPerson(String username, String email, String name) {
        long userId = 0;
        HttpClient client = HttpClientBuilder.create().build();
        HttpPost post = new HttpPost("http://" + idServiceUrl + "/post_user_id");
        try {
            HttpResponse response = getHttpResponse(name == null ? username : name, email, client, post);
            BufferedReader rd = new BufferedReader(new InputStreamReader(
                    response.getEntity().getContent()));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = rd.readLine()) != null) {
                builder.append(line);
            }
            rd.close();
            if (builder.toString().contains("error")) {
                response = getHttpResponse(name == null ? username : name, username + "@default.com", client, post);
                rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
                builder = new StringBuilder();
                while ((line = rd.readLine()) != null) {
                    builder.append(line);
                }
                rd.close();
            }
            JsonParser parser = new JsonParser();
            System.out.println(builder.toString());
            JsonObject json = parser.parse(builder.toString()).getAsJsonObject();
            userId = json.get("id").getAsLong();
            System.out.println(userId);
            return userId;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return userId;
    }

    private static HttpResponse getHttpResponse(String username, String email, HttpClient client, HttpPost post) throws IOException {
        List<NameValuePair> nameValuePairs = new ArrayList<>(1);
        nameValuePairs.add(new BasicNameValuePair("projectID", Long.toString(projectId)));
        nameValuePairs.add(new BasicNameValuePair("name", username));
        nameValuePairs.add(new BasicNameValuePair("email", email));
        post.setEntity(new UrlEncodedFormEntity(nameValuePairs, "UTF-8"));
        return client.execute(post);
    }
}
