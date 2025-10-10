# 9. Contributing

## Welcome!

We follow the standard GitHub [fork & pull](https://help.github.com/en/github/collaborating-with-issues-and-pull-requests/about-pull-requests#fork--pull) approach to pull requests. Just fork the official repo, develop in a branch, and submit a PR!

For a more detailed description of our process, please refer to the [CONTRIBUTING.md](https://github.com/apache/pekko-http/blob/main/CONTRIBUTING.md) page on the github project.

## Snapshots

Testing snapshot versions can help us find bugs before a release. We publish snapshot versions for every commit to the `main` branch.

The latest published snapshot version can be found in [ApacheSnapshotRepository].

### Configure repository

sbt
:   ```scala
    resolvers += "apache-snapshot-repository" at "https://repository.apache.org/content/repositories/snapshots"
    ```

Maven
:   ```xml
    <project>
    ...
      <repositories>
        <repository>
          <id>apache-http-snapshots</id>
          <name>Pekko HTTP Snapshots</name>
          <url>https://repository.apache.org/content/repositories/snapshots</url>
        </repository>
      </repositories>
    ...
    </project>
    ```

Gradle
:   ```gradle
    repositories {
      maven {
        url  "https://repository.apache.org/content/repositories/snapshots"
      }
    }
    ```

[ApacheSnapshotRepository]:        https://repository.apache.org/content/repositories/snapshots/org/apache/pekko/pekko-http-core_2.13/
