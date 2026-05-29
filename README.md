# GitHubWrapper

GitHubWrapper is an extension to the [GitWrapper](https://gitlab.infosun.fim.uni-passau.de/seibt/GitWrapper) project for interaction with the GitHub issue and pull-request API.

## Setup

GitHubWrapper uses the Gradle build system.


### Dependencies

Since this is an extension, [GitWrapper](https://gitlab.infosun.fim.uni-passau.de/seibt/GitWrapper) needs to be present in the parent directory.
See there for additional information about setting up GitWrapper.


## Usage

Using `./gradlew build` will assemble a .jar file containing the library in the `build/libs` directory. The dependencies of the library may be displayed using `./gradlew dependencies --configuration runtime`.

To get access to additional data provided by the GitHub API, you can wrap an existing git repository.
For information about access to local git data please refer to the GitWrapper project.

*Note: To get more than the unauthenticated limit of 60 requests per hour, you need to supply your own OAuth token.
For furhter information and to generate your own token visit [https://github.com/settings/tokens](https://github.com/settings/tokens).*

The `GitHubRepository` object then allows access to the local repository copy using native git calls as well as read only access to the GitHub API for issues (including comments and events) and pull requests.

### Usage as stand-alone tool

There is an `IssueRunner` object that allows to run GitHubWrapper as a stand-alone tool to extract issue and pull request data from GitHub's issue API.

After building GitHubWrapper via gradle (e.g., `./gradlew build`), you can simply execute the resulting jar file:
```
java -Xmx100G -jar "build/libs/GitHubWrapper-1.0-SNAPSHOT.jar" \
            -dump "name-of-the-result-file.json" \
            -tokens "tokens.txt" \
            -repo "repo-directory/repo-name" \
            -workDir "repo-directory"
```

- Using the `-dump` parameter, you specify the file path of the resulting json file.
- Using the `-tokens` parameter, you specify the path to a text file which contains your OAuth token(s). In this text file, each line has to represent a single token. If there are multiple tokens in this file, multiple tokens will be tried in the order in which they are listed in the text file.
- Using the `-repo` parameter, you specify the file path of the repo you want to analyze. Notice that you need to have cloned the repo locally, such that the origin can be derived from this file path.
- Using the `-workDir` parameter, you specify the working directory, which usually is the directory which contains the repository directory specified at `-repo`.

### Integration into other projects

There is also an option to use the implementation of GitHubWrapper in your code without using the provided `IssueRunner`.

For gradle-based projects, only extend your `settings.gradle` and `build.gradle` as follows:

settings.gradle

**settings.gradle**
```groovy
includeFlat 'GitHubWrapper' // The name of the directory containing your clone of GitWrapper.

build.gradle
```

**build.gradle**
```groovy
dependencies {
    compile project(':GitHubWrapper')
}
```

You than can use GitHubWrapper in your project. Here is possible example:

```java
GitWrapper git;

...

try {
    git = new GitWrapper("git"); // Or /usr/bin/git, C:\Program Files\Git\bin\git.
} catch (ToolNotWorkingException ex) {
    // Handle the case that git can not be called using the supplied command.
    return;
}

GitHubRepository repo = git.clone(new File("."), "git@github.com:se-sic/GitHubWrapper.git", false).map(baseRepo -> new GitHubRepository(baseRepo, git));

// Print number of pull requests
repo.getPullRequests(State.ANY).ifPresent(prs -> System.out.println(prs.size()));

// Print all issues with comments
repo.getIssues(false).ifPresent(issueData -> issueData.forEach(issue -> {
    System.out.println(issue.user.username + ": " + issue.body);
    issue.getCommentsList().forEach(comment ->
        System.out.println(comment.user.username + ": " + comment.body));
}));
```

### Further data processing

The data extracted by this tool can be further processed, for example using the `run-issues.py` script from the tool [`codeface-extraction`](https://github.com/se-sic/codeface-extraction). This organizes and unifies the issue data into a single csv-like .list file. It also allows for synchronization with data from other data extraction tools, such as `codeface`.

### `referenced` events

`referenced` events are events generated in an issue if a commit references that issue in its commit message. The intended behavior is that the event is present in the issue's event data, and the commit is again present in the related commits of the issue. This does not work if it is not possible to fetch that commit. In this case, the event still exists, but it contains a link to a commit that the api cannot resolve, meaning that no data about the commit can be accessed.
Known causes of this include:

- a commit was rebased and changed/removed
- an external repository was deleted
- the commit's branch was deleted

Note that the commit might still be reachable until the automatic garbage collection has removed it from the remote repository.
In itself, this is not problematic. However, when further processing the data using `codeface-extraction`, this may lead to these `referenced` events being present in the final data, even though they should be filtered out as part of the issue processing.
