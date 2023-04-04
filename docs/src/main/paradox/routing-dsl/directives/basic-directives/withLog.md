# withLog

@@@ div { .group-scala }

## Signature

@@signature [BasicDirectives.scala](/http/src/main/scala/org/apache/pekko/http/scaladsl/server/directives/BasicDirectives.scala) { #withLog }

@@@

## Description

Allows running an inner route using an alternative @apidoc[LoggingAdapter] in place of the default one.

The logging adapter can be extracted in an inner route using @ref[extractLog](extractLog.md) directly,
or used by directives which internally extract the materializer without surfacing this fact in the API.

## Example

Scala
:  @@snip [BasicDirectivesExamplesSpec.scala](/docs/src/test/scala/docs/http/scaladsl/server/directives/BasicDirectivesExamplesSpec.scala) { #withLog0 }

Java
:  @@snip [BasicDirectivesExamplesTest.java](/docs/src/test/java/docs/http/javadsl/server/directives/BasicDirectivesExamplesTest.java) { #withLog }
