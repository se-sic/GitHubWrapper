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
            System.out.println("usage: CasestuyName ResultsPath RepositoryPath TokenFile");
            return;
        }

        // ${CASESTUDY} "${RESULTS}" "${REPOS}" "${CFDATA}/configurations/tokens.txt"
        String casestudy = args[0];
        File resdir = new File(args[1], casestudy + "_issues");
        File repoPath = new File(args[2], casestudy);
        File tokenFile = new File(args[3]);
        File outputFile = new File(resdir, "issues.json");
        File cacheFile = new File(resdir, "cache.json");

        try {
            git = new GitWrapper("git"); // Or /usr/bin/git, C:\Program Files\Git\bin\git.
        } catch (ToolNotWorkingException ex) {
            // Handle the case that git can not be called using the supplied command.
            return;
        }
        GitHubRepository repo;
        Optional<Repository> optRepo = git.importRepository(repoPath);
        if (optRepo.isPresent()) {
            List<String> tokens = new ArrayList<>();
            try(BufferedReader br = new BufferedReader(new FileReader(tokenFile))) {
                String token;
                while((token = br.readLine()) != null) {
                    tokens.add(token);
                }
            } catch (IOException e) {
                LOG.severe("A file containing the GitHub Tokens to be used is required to be at this location: " + tokenFile);
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
            if(!resdir.exists()) {
                resdir.mkdirs();
            }
            PrintWriter out = new PrintWriter(outputFile, "UTF-8");
            out.print("[");
            StringBuilder sb = new StringBuilder();
            repo.getIssues(cacheFile).forEach(issue -> {
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
