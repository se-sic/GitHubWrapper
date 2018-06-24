package de.uni_passau.fim.issuerunner;

import de.uni_passau.fim.gitwrapper.GitHubRepository;
import de.uni_passau.fim.gitwrapper.GitWrapper;
import de.uni_passau.fim.gitwrapper.Repository;
import de.uni_passau.fim.processexecutor.ToolNotWorkingException;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;

public class IssueRunner {

    private static final Logger LOG = Logger.getLogger(IssueRunner.class.getCanonicalName());

    @Option(name = "-repoList",
            metaVar = "list.txt",
            depends = {"-outDir"},
            forbids = {"-repo"},
            usage = "List of URLs for GitHub repositories to analyze. To use an existing dump as cache and update it, add path to JSON file after a space.")
    private String repoList = null;

    @Option(name = "-repo",
            metaVar = "url",
            depends = {"-dump"},
            forbids = {"-repoList"},
            usage = "URL for analyzing a singel GitHub repository.")
    private String singleRepo = null;

    @Option(name = "-dump",
            metaVar = "repo.json",
            depends = {"-repo"},
            usage = "JSON file to dump a single repo to. If the file exists, it is used as a cache for the repo and updated at the end.")
    private String dump = null;

    @Option(name = "-tokens",
            metaVar = "tokens.txt",
            forbids = {"-singleToken"},
            usage = "List of GitHub API tokens, one per line, no trailing newline.")
    private String tokenList = null;

    @Option(name = "-token",
            metaVar = "APITOKEN",
            forbids = {"-tokens"},
            usage = "GitHub API token.")
    private String singleToken = null;

    @Option(name = "-workDir",
            metaVar = "dir",
            required = true,
            usage = "Directory to clone repositories to.")
    private String workDir = null;

    @Option(name = "-outputDIr",
            metaVar = "dir",
            depends = {"-repoList"},
            usage = "Directory to put the JSON dumps in.")
    private String outputDir = null;

    public static void main(String[] args) {
        final IssueRunner runner = new IssueRunner();

        try {
            runner.analyze(args);
        } catch (ToolNotWorkingException e) {
            System.out.println("ERROR: Exception encountered: " + e);
        }
    }

    private void analyze(final String[] arguments) throws ToolNotWorkingException {
        final CmdLineParser parser = new CmdLineParser(this);
        if (arguments.length < 1) {
            parser.printUsage(System.out);
            System.exit(-1);
        }
        try {
            parser.parseArgument(arguments);
        } catch (CmdLineException clEx) {
            System.out.println("ERROR: Unable to parse command-line options: " + clEx);
        }

        List<String> repos = null;
        if (repoList != null && outputDir != null) {
            repos = getLinesFromFile(new File(repoList));
        } else if (singleRepo != null && dump != null) {
            repos = Collections.singletonList(singleRepo);
        } else {
            System.out.println("ERROR: No input or output specified.");
            parser.printUsage(System.out);
            System.exit(-1);
        }

        List<String> tokens;
        if (tokenList != null) {
            tokens = getLinesFromFile(new File(tokenList));
        } else if (singleToken != null) {
            tokens = Collections.singletonList(singleToken);
        } else {
            tokens = Collections.singletonList("");
        }

        GitWrapper git = new GitWrapper("git");
        List<String> finalTokens = tokens;
        repos.forEach(line -> {
            String[] info = line.split("\\s+");
            LOG.info("Running for repo " + info[0]);
            Optional<Repository> clone = git.clone(new File(workDir), info[0], true);

            if (!clone.isPresent()) {
                LOG.severe("Could not clone repository " + info[0] + "! Skipping.");
                return;
            }

            GitHubRepository repo;
            if (dump != null || info.length >= 2) {
                try {
                    StringBuilder path = new StringBuilder();
                    for (int i = 1; i < info.length; ++i) {
                        path.append(info[i]);
                    }
                    if (dump != null) {
                        path = new StringBuilder(dump);
                    }
                    repo = new GitHubRepository(line, clone.get().getDir(), git, finalTokens, new File(path.toString()));
                } catch (FileNotFoundException e) {
                    LOG.warning("Could not read issue cache file " + info[1] + ". Fetching new.");
                    repo = new GitHubRepository(line, clone.get().getDir(), git, finalTokens);
                }
            } else {
                repo = new GitHubRepository(line, clone.get().getDir(), git, finalTokens);
            }

            String json = repo.serialize(repo.getIssues(true));
            try {
                File outFile;
                if (dump != null) {
                    outFile = new File(dump);
                } else {
                    outFile = new File(new File(outputDir), repo.getName() + ".json");
                }

                BufferedWriter out = new BufferedWriter(new FileWriter(outFile));
                out.write(json);
                out.close();
            } catch (IOException e) {
                LOG.severe("Could not write json to file.");
            }
        });
    }

    private static List<String> getLinesFromFile(File file) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }
        } catch (IOException e) {
            LOG.severe("Error while accessing file: " + e);
        }

        return Collections.unmodifiableList(lines);
    }
}
