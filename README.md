# Apache Pekko HTTP

<!--
[![pekko-http-core Scala version support](https://index.scala-lang.org/pekko/pekko-http/pekko-http-core/latest-by-scala-version.svg)](https://index.scala-lang.org/pekko/pekko-http/pekko-http-core)
-->

The Pekko HTTP modules implement a full server- and client-side HTTP stack on top
of pekko-actor and pekko-stream. It's not a web-framework but rather a more
general toolkit for providing and consuming HTTP-based services. While
interaction with a browser is of course also in scope it is not the primary
focus of Pekko HTTP.

Pekko HTTP is a fork of [Akka HTTP](https://github.com/akka/akka-http) 10.2.x release, prior to the Akka project's adoption of the Business Source License.

Pekko HTTP follows a rather open design and many times offers several different
API levels for "doing the same thing". You get to pick the API level of
abstraction that is most suitable for your application. This means that, if you
have trouble achieving something using a high-level API, there's a good chance
that you can get it done with a low-level API, which offers more flexibility but
might require you to write more application code.

## Documentation

The documentation is available at
[pekko.apache.org](https://pekko.apache.org/docs/pekko-http/current/), for
[Scala](https://pekko.apache.org/docs/pekko-http/current/scala/http/) and
[Java](https://pekko.apache.org/docs/pekko-http/current/java/http/).

## Building from Source

### Prerequisites
- Make sure you have installed a Java Development Kit (JDK) version 8 or later.
- Make sure you have [sbt](https://www.scala-sbt.org/) installed.
- [Graphviz](https://graphviz.gitlab.io/download/) is needed for the scaladoc generation build task, which is part of the release.

### h2spec

Some tests for HTTP/2 compliance use [h2spec](https://github.com/summerwind/h2spec). The sbt build downloads
pre-built binaries from the GitHub releases page for the h2spec project. These binaries are not available for all
operating systems. Apple Mac Silicon users may need to install [Rosetta](https://support.apple.com/en-us/HT211861) if
they do not have it installed already.

### Running the Build
- Open a command window and change directory to your preferred base directory
- Use git to clone the [repo](https://github.com/apache/pekko-http) or download a source release from https://pekko.apache.org (and unzip or untar it, as appropriate)
- Change directory to the directory where you installed the source (you should have a file called `build.sbt` in this directory)
- `sbt compile` compiles the main source for project default version of Scala (2.13)
    - `sbt +compile` will compile for all supported versions of Scala
- `sbt test` will compile the code and run the unit tests
- `sbt testQuick` similar to `test` but when repeated in shell mode will only run failing tests
- `sbt package` will build the jars
    - the jars will built into target dirs of the various modules
    - for the the 'http-core' module, the jar will be built to `http-core/target/scala-2.13/`
- `sbt publishLocal` will push the jars to your local Apache Ivy repository
- `sbt publishM2` will push the jars to your local Apache Maven repository
- `sbt docs/paradox` will build the docs (the ones describing the module features)
     - `sbt docs/paradoxBrowse` does the same but will open the docs in your browser when complete
     - the `index.html` file will appear in `target/paradox/site/main/`
- `sbt unidoc` will build the Javadocs for all the modules and load them to one place (may require Graphviz, see Prerequisites above)
     - the `index.html` file will appear in `target/scala-2.13/unidoc/`
- `sbt sourceDistGenerate` will generate source release to `target/dist/`
- The version number that appears in filenames and docs is derived, by default. The derived version contains the most git commit id or the date/time (if the directory is not under git control). 
    - You can set the version number explicitly when running sbt commands
        - eg `sbt "set ThisBuild / version := \"1.0.0\"; sourceDistGenerate"`  
    - Or you can add a file called `version.sbt` to the same directory that has the `build.sbt` containing something like
        - `ThisBuild / version := "1.0.0"`

## Community

If you have questions about the contribution process or discuss specific issues, please interact with the community using the following resources.

- [GitHub discussions](https://github.com/apache/pekko-http/discussions): for questions and general discussion.
- [Pekko users mailing list](https://lists.apache.org/list.html?users@pekko.apache.org): for Pekko development discussions.
- [Pekko dev mailing list](https://lists.apache.org/list.html?dev@pekko.apache.org): for Pekko development discussions.
- [GitHub issues](https://github.com/apache/pekko-http/issues): for bug reports and feature requests. Please search the existing issues before creating new ones. If you are unsure whether you have found a bug, consider asking in GitHub discussions or the mailing list first.

<!--
[stackoverflow-badge]: https://img.shields.io/badge/stackoverflow%3A-pekko--http-blue.svg?style=flat-square
[stackoverflow]:       https://stackoverflow.com/questions/tagged/pekko-http
[github-issues-badge]: https://img.shields.io/badge/github%3A-issues-blue.svg?style=flat-square
[github-issues]:       https://github.com/apache/pekko-http/issues
[scaladex-badge]:      https://index.scala-lang.org/count.svg?q=dependencies:pekko/pekko-http*&subject=scaladex:&color=blue&style=flat-square
[scaladex-projects]:   https://index.scala-lang.org/search?q=dependencies:pekko/pekko-http*
-->

## Contributing

Contributions are *very* welcome!

If you see an issue that you'd like to see fixed, the best way to make it happen is to help out by submitting a pull request.
For ideas of where to contribute, [tickets marked as "help wanted"](https://github.com/apache/pekko-http/labels/help%20wanted) are a good starting point.

Refer to the [CONTRIBUTING.md](CONTRIBUTING.md) file for more details about the workflow,
and general hints on how to prepare your pull request. You can also ask for clarifications or guidance in GitHub issues directly.


## License

Apache Pekko HTTP is Open Source and available under the Apache 2 License.
