package de.uni_passau.fim.gitwrapper;

/**
 * Skeleton for information about git references.
 */
public class RefData {

    String ref;
    String sha;
    RepoData repo;

    /**
     * The symbolic reference name.
     */
    public String getRef() {
        return ref;
    }

    /**
     * The hash of the tip.
     */
    public String getSha() {
        return sha;
    }

    /**
     * Information about the repository
     */
    public RepoData getRepo() {
        return repo;
    }
}
