<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# Configuration

Just like any other Apache Pekko module, Apache Pekko HTTP is configured via [Typesafe Config](https://github.com/lightbend/config).
Usually this means that you provide an `application.conf` which contains all the application-specific settings that
differ from the default ones provided by the reference configuration files from the individual Apache Pekko modules.

To set an aggregate limit for incoming HTTP/1 fields, configure `pekko.http.parsing.max-http1-field-section-size`.
The default `infinite` preserves the individual field length and count limits without an aggregate limit.
A finite limit applies separately to each field section, including informational responses and trailers.
Set a parser-specific override under `pekko.http.server.parsing`, `pekko.http.client.parsing`, or
`pekko.http.host-connection-pool.client.parsing`. This setting does not affect HTTP/2 or multipart part headers.

These are the relevant default configuration values for the Apache Pekko HTTP modules.

pekko-http-core
:  @@snip [reference.conf](/http-core/src/main/resources/reference.conf)

pekko-http
:  @@snip [reference.conf](/http/src/main/resources/reference.conf)

pekko-http-caching
:  @@snip [reference.conf](/http-caching/src/main/resources/reference.conf)

pekko-http-cors
:  @@snip [reference.conf](/http-cors/src/main/resources/reference.conf)

pekko-http-jackson
:  @@snip [reference.conf](/http-marshallers-java/http-jackson/src/main/resources/reference.conf)

pekko-http-jackson3
:  @@snip [reference.conf](/http-marshallers-java/http-jackson3/src/main/resources/reference.conf)

pekko-http-testkit
:  @@snip [reference.conf](/http-testkit/src/main/resources/reference.conf)

The other Apache Pekko HTTP modules do not offer any configuration via [Typesafe Config](https://github.com/lightbend/config).
