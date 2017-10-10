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

            repo = new GitHubRepository(optRepo.get(), git, tokens);
            repo.sleepOnApiLimit(true);
            repo.allowGuessing(true);
        } else {
            System.out.println("Cloning failed");
            return;
        }
        System.out.println("Starting to build Json.");


        try {
            File directory = new File(resdir);
            if(! directory.exists()) {
                directory.mkdirs();
            }
            PrintWriter out = new PrintWriter(resdir + "/issues.json", "UTF-8");
            out.print("[");
            StringBuilder sb = new StringBuilder();
            repo.getIssues(true).forEach(issue -> {
                sb.append(repo.serialize(issue));
                sb.append(',');
            });
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
