Apache Pekko HTTP
=================

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

Learn more at [pekko.apache.org](https://pekko.apache.org/docs/pekko-http/current/).

Documentation
-------------

The documentation is available at
[pekko.apache.org](https://pekko.apache.org/docs/pekko-http/current/), for
[Scala](https://pekko.apache.org/docs/pekko-http/current/scala/http/) and
[Java](https://pekko.apache.org/docs/pekko-http/current/java/http/).


Community
---------

If you have questions about the contribution process or discuss specific issues, please interact with the community using the following resources.

- [GitHub discussions](https://github.com/apache/incubator-pekko-http/discussions): for questions and general discussion.
- [Pekko dev mailing list](https://lists.apache.org/list.html?dev@pekko.apache.org): for Pekko development discussions.
- [GitHub issues](https://github.com/apache/incubator-pekko-http/issues): for bug reports and feature requests. Please search the existing issues before creating new ones. If you are unsure whether you have found a bug, consider asking in GitHub discussions or the mailing list first.

<!--
[stackoverflow-badge]: https://img.shields.io/badge/stackoverflow%3A-pekko--http-blue.svg?style=flat-square
[stackoverflow]:       https://stackoverflow.com/questions/tagged/pekko-http
[github-issues-badge]: https://img.shields.io/badge/github%3A-issues-blue.svg?style=flat-square
[github-issues]:       https://github.com/apache/incubator-pekko-http/issues
[scaladex-badge]:      https://index.scala-lang.org/count.svg?q=dependencies:pekko/pekko-http*&subject=scaladex:&color=blue&style=flat-square
[scaladex-projects]:   https://index.scala-lang.org/search?q=dependencies:pekko/pekko-http*
-->

Contributing
------------
Contributions are *very* welcome!

If you see an issue that you'd like to see fixed, the best way to make it happen is to help out by submitting a pull request.
For ideas of where to contribute, [tickets marked as "help wanted"](https://github.com/apache/incubator-pekko-http/labels/help%20wanted) are a good starting point.

Refer to the [CONTRIBUTING.md](CONTRIBUTING.md) file for more details about the workflow,
and general hints on how to prepare your pull request. You can also ask for clarifications or guidance in GitHub issues directly.


License
-------

Apache Pekko HTTP is Open Source and available under the Apache 2 License.
