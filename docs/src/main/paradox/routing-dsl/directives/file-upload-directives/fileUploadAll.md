<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

<a id="fileuploadall"></a>
# fileUploadAll

@@@ div { .group-scala }
## Signature

@@signature [FileUploadDirectives.scala](/http/src/main/scala/org/apache/pekko/http/scaladsl/server/directives/FileUploadDirectives.scala) { #fileUploadAll }

@@@

## Description

Simple access to streams of bytes for all files uploaded in a multipart form together with metadata
about each upload.

If there is no field with the given name the request will be rejected.

@@@ note
This directive buffers all files to temporary files on disk in files prefixed `pekko-http-upload`. This is
to work around limitations of the HTTP multipart format. To upload only one file it may be preferred to
use the @ref[fileUpload](fileUpload.md#fileupload) directive, as it streams the file directly without
buffering.
@@@

## Example

Scala
:  @@snip [FileUploadDirectivesExamplesSpec.scala](/docs/src/test/scala/docs/http/scaladsl/server/directives/FileUploadDirectivesExamplesSpec.scala) { #fileUploadAll }

Java
:  @@snip [FileUploadDirectivesExamplesTest.java](/docs/src/test/java/docs/http/javadsl/server/directives/FileUploadDirectivesExamplesTest.java) { #fileUploadAll }
