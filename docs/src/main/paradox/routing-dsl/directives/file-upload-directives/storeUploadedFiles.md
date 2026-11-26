<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

<a id="storeuploadedfiles"></a>
# storeUploadedFiles

@@@ div { .group-scala }
## Signature

@@signature [FileUploadDirectives.scala](/http/src/main/scala/org/apache/pekko/http/scaladsl/server/directives/FileUploadDirectives.scala) { #storeUploadedFiles }

@@@

## Description

Streams the contents of all files uploaded in a multipart form into files on disk and provides a list of each
file and metadata about the upload.

The application chooses the destination. Treat the uploaded filename in `FileInfo` as untrusted metadata, not as
a filesystem path. For temporary storage, use an application-controlled filename prefix.

If there is an error writing to disk the request will be failed with the thrown exception. If there is no field
with the given name the request will be rejected.

## Example

Scala
:  @@snip [FileUploadDirectivesExamplesSpec.scala](/docs/src/test/scala/docs/http/scaladsl/server/directives/FileUploadDirectivesExamplesSpec.scala) { #storeUploadedFiles }

Java
:  @@snip [FileUploadDirectivesExamplesTest.java](/docs/src/test/java/docs/http/javadsl/server/directives/FileUploadDirectivesExamplesTest.java) { #storeUploadedFiles }
