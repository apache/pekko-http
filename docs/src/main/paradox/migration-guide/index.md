# Migration Guides

@@toc { depth=3 }

* If you are currently using an old version of Akka HTTP or Spray, you should first follow the [Akka HTTP migration guide](https://doc.akka.io/docs/akka-http/10.2/migration-guide/index.html)
* The @extref:[migration guide](pekko-docs:migration/index.html) for the core Apache Pekko libs is a useful guideline.
* for Pekko jar dependencies, the groupId is "org.apache.pekko" instead of "com.typesafe.akka"
* the jar names start with "pekko" instead of "akka" - e.g. pekko-http_2.13.jar instead of akka-http_2.13.jar
* If you have a dependency akka-http2-support, Pekko does not need a separate jar. The support is built into main pekko-http jars.
* Pekko packages start with "org.apache.pekko" instead of "akka" - e.g. `import org.apache.pekko.http` instead of `import akka.http`
* Where class names have "Akka" in the name, the Pekko ones have "Pekko" - e.g. PekkoException instead of AkkaException
* Configs in `application.conf` use "pekko" prefix instead of "akka"
* We will soon provide a more detailed guide for migrating from Akka HTTP v10.2 to Apache Pekko HTTP
* If you happen to be using [akka-http-cors](https://github.com/lomigmegard/akka-http-cors) this has been directly integrated into
  Apache Pekko Http which means you should use the @ref:[pekko-http-cors artifact](../common/cors.md) instead. Note that the root configuration naming 
  has also been updated (i.e. changing from `akka-http-cors` to `pekko.http.cors`) to make it consistent with the rest of Apache Pekko Http.
  * In addition there is a single breaking change, the return type of the `org.apache.pekko.http.cors.javadsl.settings.CorsSettings.getMaxAge`
  method has been changed from `java.util.Optional<Long>` to `java.util.OptionalLong` since it's more idiomatic.
