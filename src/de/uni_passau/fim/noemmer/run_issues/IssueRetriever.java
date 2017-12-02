package de.uni_passau.fim.noemmer.run_issues;

import de.uni_passau.fim.heck.githubinterface.GitHubRepository;
import de.uni_passau.fim.seibt.gitwrapper.process.ToolNotWorkingException;
import de.uni_passau.fim.seibt.gitwrapper.repo.GitWrapper;
import de.uni_passau.fim.seibt.gitwrapper.repo.Repository;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Starts the process of loading the issues from Github.
 */
public class IssueRetriever {

    private static final Logger LOG = Logger.getLogger(GitHubRepository.class.getCanonicalName());

    public static void main(String args[]) {
        GitWrapper git;
        if (args.length != 4) {
            System.out.println("usage: ResultsPath RepositoryPath TokenFile CacheDirectory");
            return;
        }
        String resdir = args[0];
        String repoPath = args[1];
        String tokendir = args[2];
        String cachedir = args[3];

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
            try(BufferedReader br = new BufferedReader(new FileReader(tokendir))) {
                String token;
                while((token = br.readLine()) != null) {
                    tokens.add(token);
                }
            } catch (IOException e) {
                LOG.severe("A file containing the GitHub Tokens to be used is required to be at this location: " + tokendir);
                return;
            }
            repo = new GitHubRepository(optRepo.get(), git, tokens);
            repo.allowGuessing(true);
        } else {
            LOG.severe("Cloning failed");
            return;
        }
        LOG.info("Starting to build Json.");


        try {
            File directory = new File(resdir);
            if(! directory.exists()) {
                directory.mkdirs();
            }
            PrintWriter out = new PrintWriter(resdir + "/issues.json", "UTF-8");
            out.print("[");
            StringBuilder sb = new StringBuilder();
            repo.getIssues(cachedir).forEach(issue -> {
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
