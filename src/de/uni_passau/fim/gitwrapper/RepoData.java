package de.uni_passau.fim.gitwrapper;

/**
 * Skeleton object for data about Repositories.
 */
public class RepoData {

    String full_name;
    String html_url;
    String clone_url;

    /**
     * The full name of the Repository.
     */
    public String getFullName() {
        return full_name;
    }

    /**
     * The HTML URL to the web page on GitHub.
     */
    public String getHtmlUrl() {
        return html_url;
    }

    /**
     * The url to clone the repo.
     */
    public String getCloneUrl() {
        return clone_url;
    }
}
