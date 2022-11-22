/**
 * Copyright (C) 2018 Florian Heck
 * Copyright (C) 2018 Claus Hunsen
 * Copyright (C) 2019-2021 Thomas Bock
 *
 * This file is part of GitHubWrapper.
 *
 * GitHubWrapper is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * GitHubWrapper is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with GitWrapper. If not, see <http://www.gnu.org/licenses/>.
 */
package de.uni_passau.fim.issuerunner;

import de.uni_passau.fim.gitwrapper.GitHubRepository;
import de.uni_passau.fim.gitwrapper.GitWrapper;
import de.uni_passau.fim.gitwrapper.IssueData;
import de.uni_passau.fim.gitwrapper.Repository;
import de.uni_passau.fim.processexecutor.ToolNotWorkingException;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.*;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

public class IssueRunner {

    private static final Logger LOG = Logger.getLogger(IssueRunner.class.getCanonicalName());

    @Option(name = "-repoList",
            metaVar = "list.txt",
            depends = {"-outputDir"},
            forbids = {"-repo"},
            usage = "List of URLs for GitHub repositories to analyze. To use an existing dump as cache and update it, " +
                    "add the ISO 8601 date of dump creation followed by the path to the JSON file separated with spaces.")
    private String repoList = null;

    @Option(name = "-repo",
            metaVar = "url",
            forbids = {"-repoList"},
            depends = {"-workDir"},
            usage = "URL for analyzing a single GitHub repository.")
    private String singleRepo = null;

    @Option(name = "-dump",
            metaVar = "repo.json",
            depends = {"-repo"},
            usage = "JSON file to dump a single repo to. If the file exists, it is used as a cache for the repo and updated at the end.")
    private String dump = null;

    @Option(name = "-date",
            metaVar = "YYYY-MM-DDTHH:MM:SSZ",
            depends = {"-repo", "-dump"},
            usage = "Time of dump creation.")
    private String date = null;

    @Option(name = "-tokens",
            metaVar = "tokens.txt",
            forbids = {"-token"},
            usage = "List of GitHub API tokens, one per line, no trailing newline.")
    private String tokenList = null;

    @Option(name = "-token",
            metaVar = "APITOKEN",
            forbids = {"-tokens"},
            usage = "GitHub API token.")
    private String singleToken = null;

    @Option(name = "-workDir",
            metaVar = "dir",
            usage = "Directory to clone repositories to. (Default is same as outputDir)")
    private String workDir = null;

    @Option(name = "-outputDir",
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
            System.exit(-1);
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

        if (workDir == null) {
            workDir = outputDir;
        }

        List<String> tokens;
        if (tokenList != null) {
            tokens = getLinesFromFile(new File(tokenList));
        } else if (singleToken != null) {
            tokens = Collections.singletonList(singleToken);
        } else {
            tokens = Collections.singletonList("");
        }

        OffsetDateTime startTime = OffsetDateTime.now();
        BufferedWriter repoListFile = null;
        if (outputDir != null) {
            try {
                repoListFile = new BufferedWriter(new FileWriter(new File(new File(outputDir), "repolist.txt")));
            } catch (IOException e) {
                LOG.severe("Cannot write repo list: " + e);
            }
        }

        GitWrapper git = new GitWrapper("git");
        List<String> finalTokens = tokens;
        BufferedWriter finalRepoListFile = repoListFile;
        repos.forEach(line -> {
            String[] info = line.split("\\s+");
            LOG.info("Running for repo " + info[0]);
            Optional<Repository> clone = git.clone(new File(workDir), info[0], true);

            if (!clone.isPresent()) {
                LOG.severe("Could not clone repository " + info[0] + "! Skipping.");
                return;
            }

            GitHubRepository repo;

            String dumpPath = null;
            String dumpTime = null;
            if (dump != null) {
                dumpPath = dump;
                dumpTime = date;
            } else if (info.length >= 3) {
                StringBuilder path = new StringBuilder();
                for (int i = 2; i < info.length; ++i) {
                    path.append(info[i]);
                }
                dumpPath = path.toString();
                dumpTime = info[1];
            }

            OffsetDateTime since = null;
            if (dumpTime != null) {
                try {
                    since = OffsetDateTime.parse(dumpTime);
                } catch (DateTimeParseException e) {
                    LOG.severe("Could not parse date: " + e);
                }
            }

            if (dumpPath != null) {
                File dumpFile = new File(dumpPath);
                if (dumpFile.exists()) {
                    try {
                        repo = new GitHubRepository(clone.get(), finalTokens, dumpFile);
                    } catch (FileNotFoundException e) {
                        LOG.severe("Could not read issue cache file. Should not happen, since we just checked. Skipping");
                        return;
                    }
                } else {
                    repo = new GitHubRepository(clone.get(), finalTokens);
                }
            } else {
                repo = new GitHubRepository(clone.get(), finalTokens);
            }

            Optional<List<IssueData>> issueData = repo.getIssues(true, since);

            if (!issueData.isPresent()) {
                LOG.severe("Could not net issues. Skipping.");
                return;
            }

            File outFile = null;
            FileOutputStream outStream = null;
            try {
                if (dump != null) {
                    outFile = new File(dump);
                } else {
                    outFile = new File(new File(outputDir), repo.getName() + ".json");
                }

                // Use FileOutputStream instead of BufferedWriter to prevent OutOfMemoryErrors on huge amount of data
                outStream = new FileOutputStream(outFile);
                OutputStreamWriter outStreamWriter = new OutputStreamWriter(outStream,"UTF-8");
                repo.streamSerialize(outStreamWriter, issueData.get());
                outStreamWriter.close();
            } catch (IOException e) {
                LOG.severe("Could not write JSON to file: " + e);
            }
            if (finalRepoListFile != null) {
                try {
                    finalRepoListFile.write(repo.getUrl() + " " + startTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                            + " " + outFile.getCanonicalPath());
                } catch (IOException e) {
                    LOG.severe("Could not write repo list to file: " + e);
                }
            }
        });

        if (finalRepoListFile != null) {
            try {
                finalRepoListFile.flush();
                finalRepoListFile.close();
            } catch (IOException e) {
                LOG.severe("Could not write repo list to file: " + e);
            }
        }
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
