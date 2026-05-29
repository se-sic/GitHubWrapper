/**
 * Copyright (C) 2019 Thomas Bock
 * Copyright (C) 2025 Leo Sendelbach
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
package de.uni_passau.fim.gitwrapper;

/**
 * A {@link Commit} available on GitHub, made in a {@link Repository}.
 */
public class GitHubCommit extends Commit {

    private String authorUsername;
    private String committerUsername;
    private boolean addedToPullRequest = false;
    private boolean external = false;

    /**
     * Constructs a new {@link GitHubCommit} with the given <code>id</code> made in the <code>repo</code>.
     *
     * @param repo
     *         the {@link Repository} the {@link GitHubCommit} was made in
     * @param id
     *         the ID of the {@link GitHubCommit}
     */
    GitHubCommit(Repository repo, String id) {
        super(repo, id);
    }

    /**
     * Constructs a new {@link GitHubCommit} from an existing {@link Commit} object with the given
     * <code>id</code> made in the <code>repo</code>.
     *
     * @param commit
     *         the {@link Commit} to construct a {@link GitHubCommit} from
     * @param repo
     *         the {@link Repository} the {@link GitHubCommit} was made in
     * @param id
     *         the ID of the {@link GitHubCommit}
     */
    GitHubCommit(Commit commit, Repository repo, String id) {
        super(repo, id);

        this.setMessage(commit.getMessage());
        this.setAuthor(commit.getAuthor());
        this.setAuthorMail(commit.getAuthorMail());

        this.setAuthorTime(commit.getAuthorTime());

        this.setCommitter(commit.getCommitter());
        this.setCommitterMail(commit.getCommitterMail());
        this.setCommitterTime(commit.getCommitterTime());
    }

    /**
     * Returns the username of the committer of this commit.
     *
     * @return the username of the committer
     */
    public String getCommitterUsername() {
        return committerUsername;
    }

    /**
     * Sets the committer username to the new value.
     *
     * @param username
     *         the new username of the committer
     */
    void setCommitterUsername(String username) {
        this.committerUsername = username;
    }

     /**
     * Returns the username of the author of this commit.
     *
     * @return the username of the author
     */
    public String getAuthorUsername() {
        return authorUsername;
    }

     /**
     * Sets the author username to the new value.
     *
     * @param username
     *         the new username of the author
     */
    void setAuthorUsername(String username) {
        this.authorUsername = username;
    }

    /**
     * Returns whether this commit was added to the pull request of interest.
     *
     * @return whether this commit was added to the pull request of interest
     */
    public boolean isAddedToPullRequest() {
        return addedToPullRequest;
    }

    /**
     * Sets whether this commit was added to the pull request of interest.
     *
     * @param whether this commit was added to the pull request of interest
     */
    void setAddedToPullRequest(boolean added) {
        this.addedToPullRequest = added;
    }

    /**
     * Returns whether this commit is an external commit.
     * 
     * @return whether this commit is an external commit
     */
    boolean isExternal() {
        return this.external;
    }

    /**
     * Sets whether this commit is an external commit
     * 
     * @param external this commit is an external commit
     */
    void setExternal(boolean external) {
        this.external = external;
    }
    
}
