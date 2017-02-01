package de.uni_passau.fim.heck.githubinterface.datadefinitions;

/**
 * Skeleton for information about git references.
 */
public class RefData {

    /**
     * The symbolic reference name.
     */
    public String ref;

    /**
     * The hash of the tip.
     */
    public String sha;

    /**
     * Information about the repository
     */
    public RepoData repo;
}
