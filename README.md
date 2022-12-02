Apache Pekko HTTP
=================

<!--
[![akka-http-core Scala version support](https://index.scala-lang.org/akka/akka-http/akka-http-core/latest-by-scala-version.svg)](https://index.scala-lang.org/akka/akka-http/akka-http-core)
-->

The Pekko HTTP modules implement a full server- and client-side HTTP stack on top
of pekko-actor and pekko-stream. It's not a web-framework but rather a more
general toolkit for providing and consuming HTTP-based services. While
interaction with a browser is of course also in scope it is not the primary
focus of Pekko HTTP.

Pekko HTTP follows a rather open design and many times offers several different
API levels for "doing the same thing". You get to pick the API level of
abstraction that is most suitable for your application. This means that, if you
have trouble achieving something using a high-level API, there's a good chance
that you can get it done with a low-level API, which offers more flexibility but
might require you to write more application code.

Learn more at [pekko.apache.org](https://pekko.apache.org/).

Documentation
-------------

The documentation is available at
[doc.akka.io](https://doc.akka.io/docs/akka-http/current/), for
[Scala](https://doc.akka.io/docs/akka-http/current/scala/http/) and
[Java](https://doc.akka.io/docs/akka-http/current/java/http/).


Community
---------

If you have questions about the contribution process or discuss specific issues, please interact with the community using the following resources.

- [GitHub discussions](https://github.com/apache/incubator-pekko-http/discussions): for questions and general discussion.
- [Pekko dev mailing list](https://lists.apache.org/list.html?dev@pekko.apache.org): for Pekko development discussions.
- [GitHub issues](https://github.com/apache/incubator-pekko-http/issues): for bug reports and feature requests. Please search the existing issues before creating new ones. If you are unsure whether you have found a bug, consider asking in GitHub discussions or the mailing list first.

<!--
[groups-user-badge]:   https://img.shields.io/badge/group%3A-akka--user-blue.svg?style=flat-square
[groups-user]:         https://groups.google.com/forum/#!forum/akka-user
[gitter-user-badge]:   https://img.shields.io/badge/gitter%3A-akka%2Fakka-blue.svg?style=flat-square
[gitter-user]:         https://gitter.im/akka/akka
[stackoverflow-badge]: https://img.shields.io/badge/stackoverflow%3A-akka--http-blue.svg?style=flat-square
[stackoverflow]:       https://stackoverflow.com/questions/tagged/akka-http
[github-issues-badge]: https://img.shields.io/badge/github%3A-issues-blue.svg?style=flat-square
[github-issues]:       https://github.com/apache/incubator-pekko-http/issues
[scaladex-badge]:      https://index.scala-lang.org/count.svg?q=dependencies:akka/akka-http*&subject=scaladex:&color=blue&style=flat-square
[scaladex-projects]:   https://index.scala-lang.org/search?q=dependencies:akka/akka-http*
-->

Contributing
------------
Contributions are *very* welcome!

If you see an issue that you'd like to see fixed, the best way to make it happen is to help out by submitting a pull request.
For ideas of where to contribute, [tickets marked as "help wanted"](https://github.com/apache/incubator-pekko-http/labels/help%20wanted) are a good starting point.

Refer to the [CONTRIBUTING.md](CONTRIBUTING.md) file for more details about the workflow,
and general hints on how to prepare your pull request. You can also ask for clarifications or guidance in GitHub issues directly.

Maintenance
-----------

This project is maintained by Apache's core Pekko Team as well as the extended Pekko HTTP Team, consisting of excellent and experienced developers who have shown their dedication and knowledge about HTTP and the codebase. This team may grow dynamically, and it is possible to propose new members to it.

Joining the extended team in such form gives you, in addition to street-cred, of course committer rights to this repository as well as higher impact onto the roadmap of the project. Come and join us!

License
-------

Apache Pekko HTTP is Open Source and available under the Apache 2 License.
