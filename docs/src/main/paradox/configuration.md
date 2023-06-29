# Configuration

Just like any other Apache Pekko module, Apache Pekko HTTP is configured via [Typesafe Config](https://github.com/lightbend/config).
Usually this means that you provide an `application.conf` which contains all the application-specific settings that
differ from the default ones provided by the reference configuration files from the individual Apache Pekko modules.

These are the relevant default configuration values for the Apache Pekko HTTP modules.

pekko-http-core
:  @@snip [reference.conf](/http-core/src/main/resources/reference.conf)

pekko-http
:  @@snip [reference.conf](/http/src/main/resources/reference.conf)

pekko-http-caching
:  @@snip [reference.conf](/http-caching/src/main/resources/reference.conf)

pekko-http-cors
:  @@snip [reference.conf](/http-cors/src/main/resources/reference.conf)

The other Apache Pekko HTTP modules do not offer any configuration via [Typesafe Config](https://github.com/lightbend/config).
