#GitHubWrapper

An extensions to the [GitWrapper](https://github.com/se-passau/GitWrapper) project for interaction with the GitHub issue and pull request system.

##Setup

GitHubWrapper uses the Gradle build system.


###Dependencies 

Since this is an extension, [GitWrapper](https://github.com/se-passau/GitWrapper) needs to be present in the parent directory.

###Integration into other projects

Using `./gradlew` build will assemble a .jar file containing the library in the `build/libs` directory. The dependencies of the library may be displayed using `./gradlew dependencies --configuration runtime`.

For gradle based projects only extend your `settings.gradle` and `build.gradle` as follows:

settings.gradle

`settings.gradle` 
```groovy
includeFlat 'GitHubWrapper' // The name of the directory containing your clone of GitWrapper.

build.gradle
```

`build.gradle`
```groovy
dependencies {
    compile project(':GitHubWrapper')
}
```

##Usage
To get access to additional data provided by the GitHub API, you can wrap an existing git repository. 
For information about access to local git data please refer to the GitWrapper project.

To get more than the unauthenticated limit of 60 requests per hour, you need to supply your own OAuth token.

The created `GitHubRepository` object then allows access to the local repository copy using native git calls as well as read only access to the GitHub API for issues (including comments and events) and pull requests.

###Example
```java
GitWrapper git;
 
...
 
try {
    git = new GitWrapper("git"); // Or /usr/bin/git, C:\Program Files\Git\bin\git.
} catch (ToolNotWorkingException ex) {
    // Handle the case that git can not be called using the supplied command.
    return;
}

GitHubRepository repo = git.clone(new File("."), "git@github.com:se-passau/GitHubWrapper.git", false).map(baseRepo -> new GitHubRepository(baseRepo, git, "token"));
 
// Print number of pull requests
repo.getPullRequests(State.ANY).ifPresent(prs -> System.out.println(prs.size()));
 
// Print all issues with comments
repo.getIssues(false).ifPresent(issueData -> issueData.forEach(issue -> {
    System.out.println(issue.user.username + ": " + issue.body);
    issue.getCommentsList().forEach(comment ->
        System.out.println(comment.user.username + ": " + comment.body));
}));
```