package de.uni_passau.fim.noemmer.run_issues;

import com.google.gson.JsonObject;
import de.uni_passau.fim.heck.githubinterface.GitHubRepository;
import de.uni_passau.fim.seibt.gitwrapper.process.ToolNotWorkingException;
import de.uni_passau.fim.seibt.gitwrapper.repo.GitWrapper;
import de.uni_passau.fim.seibt.gitwrapper.repo.Repository;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

/**
 *
 */
public class IssueRetriever {

    private static HashMap<String, JsonObject> buffer = new HashMap<>();

    public static void main(String args[]) {
        GitWrapper git;
        if (args.length != 2) {
            System.out.println("usage: ResultsPath RepositoryPath");
            return;
        }
        String resdir = args[0];
        String repoPath = args[1];
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
            tokens.add("125bc6ec4fdf0eb03eafc7c37e9a46e4b8cc665c");
            repo = new GitHubRepository(optRepo.get(), git, tokens);
            repo.sleepOnApiLimit(true);
        } else {
            System.out.println("Cloning failed");
            return;
        }
        repo.allowGuessing(true);
        System.out.println("Starting to build Json.");


        try {
            File directory = new File(resdir);
            if(! directory.exists()) {
                directory.mkdirs();
            }
            PrintWriter out = new PrintWriter(resdir + "/issues.json", "UTF-8");
            out.print("[");
            StringBuilder sb = new StringBuilder();
            repo.getIssues(true).ifPresent(issueData -> issueData.forEach(issue -> {
                sb.append(repo.serialize(issue));
                sb.append(',');
            }));
            sb.deleteCharAt(sb.length() - 1);
            out.print(sb);
            out.print("]");
            out.flush();
            out.close();
        } catch (FileNotFoundException | UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }
}
