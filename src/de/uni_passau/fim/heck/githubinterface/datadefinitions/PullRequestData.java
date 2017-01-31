package de.uni_passau.fim.heck.githubinterface.datadefinitions;

public class PullRequestData extends IssueData {

    public Ref head;
    public Ref base;

    public PullRequestData() {
        this.isPullRequest = true;
    }

    public class Ref {
        public String ref;
        public RepoData repo;
    }
}
