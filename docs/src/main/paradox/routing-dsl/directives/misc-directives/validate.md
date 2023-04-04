# validate

Allows validating a precondition before handling a route.

@@@ div { .group-scala }

## Signature

@@signature [MiscDirectives.scala](/http/src/main/scala/org/apache/pekko/http/scaladsl/server/directives/MiscDirectives.scala) { #validate }

@@@

## Description

Checks an arbitrary condition and passes control to the inner route if it returns `true`.
Otherwise, rejects the request with a @apidoc[ValidationRejection] containing the given error message.

## Example

Scala
:  @@snip [MiscDirectivesExamplesSpec.scala](/docs/src/test/scala/docs/http/scaladsl/server/directives/MiscDirectivesExamplesSpec.scala) { #validate-example }

Java
:  @@snip [MiscDirectivesExamplesTest.java](/docs/src/test/java/docs/http/javadsl/server/directives/MiscDirectivesExamplesTest.java) { #validate-example }
