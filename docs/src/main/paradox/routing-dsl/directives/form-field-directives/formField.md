<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# formField

## Description

Allows extracting a single Form field sent in the request. Data posted from [HTML Forms](https://www.w3.org/TR/html401/interact/forms.html#h-17.13.4) is either of type `application/x-www-form-urlencoded` or of type `multipart/form-data`.

@scala[See @ref[formFields](formFields.md) for an in-depth description.]

## Example

Scala
:  @@snip [FormFieldDirectivesExamplesSpec.scala](/docs/src/test/scala/docs/http/scaladsl/server/directives/FormFieldDirectivesExamplesSpec.scala) { #formField }

Java
:  @@snip [FormFieldDirectivesExamplesTest.java](/docs/src/test/java/docs/http/javadsl/server/directives/FormFieldDirectivesExamplesTest.java) { #formField }
