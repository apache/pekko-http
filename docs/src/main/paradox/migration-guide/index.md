# Migration Guides

@@toc { depth=3 }

* If you are currently using an old version of Akka HTTP or Spray, you should first follow the [Akka HTTP migration guide](https://doc.akka.io/docs/akka-http/10.2/migration-guide/index.html)
* The @extref:[migration guide](pekko-docs:project/migration-guides.html) for the core Apache Pekko libs is a useful guideline.
* for Pekko jar dependencies, the groupId is "org.apache.pekko" instead of "com.typesafe.akka"
* the jar names start with "pekko" instead of "akka" - e.g. pekko-http_2.13.jar instead of akka-http_2.13.jar
* If you have a dependency akka-http2-support, Pekko does not need a separate jar. The support is built into main pekko-http jars.
* Pekko packages start with "org.apache.pekko" instead of "akka" - e.g. `import org.apache.pekko.http` instead of `import akka.http`
* Where class names have "Akka" in the name, the Pekko ones have "Pekko" - e.g. PekkoException instead of AkkaException
* Configs in `application.conf` use "pekko" prefix instead of "akka"
* We will soon provide a more detailed guide for migrating from Akka HTTP v10.2 to Apache Pekko HTTP
