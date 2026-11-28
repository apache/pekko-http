<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# getFromFile

@@@ div { .group-scala }

## Signature

@@signature [FileAndResourceDirectives.scala](/http/src/main/scala/org/apache/pekko/http/scaladsl/server/directives/FileAndResourceDirectives.scala) { #getFromFile }

@@@

## Description

Allows exposing a file to be streamed to the client issuing the request.

The `unmatchedPath` (see @ref[extractUnmatchedPath](../basic-directives/extractUnmatchedPath.md)) of the @apidoc[RequestContext] is first transformed by
the given `pathRewriter` function, before being appended to the given directory name to build the final file name.

To files from a given directory use @ref[getFromDirectory](getFromDirectory.md).
To serve browsable directory listings use @ref[getFromBrowseableDirectories](getFromBrowseableDirectories.md).
To serve files from a classpath directory use @ref[getFromResourceDirectory](getFromResourceDirectory.md) instead.

Note that it's not required to wrap this directive with `get` as this directive will only respond to `GET` requests.

@@@ note
The file's contents will be read using an Apache Pekko Streams *`Source`* which *automatically uses
a pre-configured dedicated blocking io dispatcher*, which separates the blocking file operations from the rest of the stream.

Note also that thanks to using Apache Pekko Streams internally, the file will be served at the highest speed reachable by
the client, and not faster – i.e. the file will *not* end up being loaded in full into memory before writing it to
the client.
@@@

## Example

Scala
:  @@snip [FileAndResourceDirectivesExamplesSpec.scala](/docs/src/test/scala/docs/http/scaladsl/server/directives/FileAndResourceDirectivesExamplesSpec.scala) { #getFromFile-examples }

Java
:  @@snip [FileAndResourceDirectivesExamplesTest.java](/docs/src/test/java/docs/http/javadsl/server/directives/FileAndResourceDirectivesExamplesTest.java) { #getFromFile }

## Serving a precompressed gzip variant

`getFromFileWithPrecompressedGzip` is an opt-in for a file that has an application-provided gzip variant. Pass the
same underlying content type for both files and two distinct strong entity tags that describe the actual bytes of
each version. Keep the declared files immutable for the lifetime of the route. For example, use paths and validators
from a versioned asset manifest. Do not derive the gzip tag by just appending a suffix to an unverified identity tag.

The directive selects the file using `Accept-Encoding` before testing the request's conditions (RFC 9110,
[section 8.8.3.3](https://www.rfc-editor.org/rfc/rfc9110.html#section-8.8.3.3) and
[section 13.2.2](https://www.rfc-editor.org/rfc/rfc9110.html#section-13.2.2)). If revalidation finds the selected
representation unchanged, the directive returns an empty `304 Not Modified` with its tag and `Vary: Accept-Encoding`,
without preparing or opening a file body. This still checks file availability and modification time. The identity
file must be readable; an unreadable gzip variant is not offered. The route rejects if none of the available
encodings is acceptable. Earlier origin preconditions remain handled by @ref[conditional](../cache-condition-directives/conditional.md)
using the same selected file's validators.

This directive serves whole representations, does not apply byte ranges, and does not compress files on demand.
It is separate from `getFromFile` and its settings. Do not wrap it in `encodeResponse` or transform the response's
representation after selection: the supplied strong ETag describes the bytes of the selected file.

Scala
:  ``getFromFileWithPrecompressedGzip(file, identityTag, gzipFile, gzipTag, ContentTypes.`text/plain(UTF-8)`)``

Java
:  @@snip [FileAndResourceDirectivesExamplesTest.java](/docs/src/test/java/docs/http/javadsl/server/directives/FileAndResourceDirectivesExamplesTest.java) { #getFromFileWithPrecompressedGzip }
