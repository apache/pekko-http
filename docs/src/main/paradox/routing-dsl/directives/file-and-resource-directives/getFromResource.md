# getFromResource

@@@ div { .group-scala }

## Signature

@@signature [FileAndResourceDirectives.scala](/http/src/main/scala/org/apache/pekko/http/scaladsl/server/directives/FileAndResourceDirectives.scala) { #getFromResource }

@@@

## Description

Completes `GET` requests with the content of the given classpath resource.

For details refer to @ref[getFromFile](getFromFile.md) which works the same way but obtaining the file from the filesystem
instead of the applications classpath.

A resource name that contains a `..` segment, a backslash or a NUL character is rejected, unless the
`pekko.http.routing.reject-unsafe-resource-names` setting is turned off. The directive does not otherwise know which
prefix a name was built from: validate any part of the name that comes from the request, or use
@ref[getFromResourceDirectory](getFromResourceDirectory.md), which serves only resources below the given directory.

Note that it's not required to wrap this directive with `get` as this directive will only respond to `GET` requests.

## Example

Scala
:  @@snip [FileAndResourceDirectivesExamplesSpec.scala](/docs/src/test/scala/docs/http/scaladsl/server/directives/FileAndResourceDirectivesExamplesSpec.scala) { #getFromResource-examples }

Java
:  @@snip [FileAndResourceDirectivesExamplesTest.java](/docs/src/test/java/docs/http/javadsl/server/directives/FileAndResourceDirectivesExamplesTest.java) { #getFromResource }
