# Compatibility Guidelines

## Binary Compatibility Rules

Apache Pekko HTTP follows the same binary compatibility rules as Akka itself.
In short it means that the versioning scheme should be read as `major.minor.patch`,
wherein all versions with the same `major` version are backwards binary-compatible,
with the exception of `@ApiMayChange`, `@InternalApi` or `@DoNotInherit` marked APIs 
or other specifically documented special-cases.

For more information and a detailed discussion of these rules and guarantees please refer to
@extref:[The @DoNotInherit and @ApiMayChange markers](akka-docs:common/binary-compatibility-rules.html#the-donotinherit-and-apimaychange-markers).

### Components with no Binary Compatibility Guarantee

The following components and modules don't have the previously mentioned binary compatibility guaranteed within minor or
patch versions. However, binary compatibility will be attempted to be kept as much as possible.

#### pekko-http

Scala
:   ```scala
    akka.http.scaladsl.server.directives.FileUploadDirectives#storeUploadedFile
    akka.http.scaladsl.server.directives.FileUploadDirectives#storeUploadedFiles
    akka.http.scaladsl.server.directives.FileUploadDirectives#fileUploadAll
    akka.http.scaladsl.marshalling.sse.EventStreamMarshalling
    akka.http.scaladsl.server.HttpApp
    akka.http.scaladsl.unmarshalling.sse.EventStreamParser
    akka.http.scaladsl.unmarshalling.sse.EventStreamUnmarshalling
    akka.http.scaladsl.OutgoingConnectionBuilder#managedPersistentHttp2
    akka.http.scaladsl.OutgoingConnectionBuilder#managedPersistentHttp2WithPriorKnowledge
    ```

Java
:   ```java
    akka.http.javadsl.common.PartialApplication#bindParameter
    akka.http.javadsl.server.Directives#anyOf (all overloads)
    akka.http.javadsl.server.Directives#allOf (all overloads)
    akka.http.javadsl.server.directives.FileUploadDirectives#storeUploadedFile
    akka.http.javadsl.server.directives.FileUploadDirectives#storeUploadedFiles
    akka.http.javadsl.server.directives.FileUploadDirectives#fileUploadAll
    akka.http.javadsl.server.HttpApp
    akka.http.javadsl.model.RequestResponseAssociation
    akka.http.javadsl.OutgoingConnectionBuilder#managedPersistentHttp2WithPriorKnowledge
    akka.http.javadsl.OutgoingConnectionBuilder#managedPersistentHttp2
    ```    

#### pekko-http-caching

Scala
:   ```scala
    akka.http.caching.LfuCache
    akka.http.caching.scaladsl.Cache
    akka.http.scaladsl.server.directives.CachingDirectives
    ```

Java
:   ```java
    akka.http.caching.LfuCache
    akka.http.caching.javadsl.Cache
    akka.http.javadsl.server.directives.CachingDirectives
    ```    

#### pekko-http-core

Scala
:   ```scala
    akka.http.scaladsl.ClientTransport
    akka.http.scaladsl.ConnectionContext#httpsClient
    akka.http.scaladsl.ConnectionContext#httpsServer
    akka.http.scaladsl.settings.PoolImplementation
    akka.http.scaladsl.settings.ClientConnectionSettings#transport
    akka.http.scaladsl.settings.ClientConnectionSettings#withTransport
    akka.http.scaladsl.settings.ConnectionPoolSettings#appendHostOverride
    akka.http.scaladsl.settings.ConnectionPoolSettings#poolImplementation
    akka.http.scaladsl.settings.ConnectionPoolSettings#responseEntitySubscriptionTimeout
    akka.http.scaladsl.settings.ConnectionPoolSettings#withHostOverrides
    akka.http.scaladsl.settings.ConnectionPoolSettings#withOverrides
    akka.http.scaladsl.settings.ConnectionPoolSettings#withPoolImplementation
    akka.http.scaladsl.settings.ConnectionPoolSettings#withResponseEntitySubscriptionTimeout
    akka.http.scaladsl.settings.HostOverride
    akka.http.scaladsl.settings.Http2ServerSettings
    akka.http.scaladsl.settings.Http2ClientSettings
    akka.http.scaladsl.settings.PreviewServerSettings
    akka.http.scaladsl.settings.ServerSentEventSettings
    akka.http.scaladsl.model.headers.CacheDirectives.immutableDirective
    akka.http.scaladsl.model.headers.X-Forwarded-Host
    akka.http.scaladsl.model.headers.X-Forwarded-Proto
    akka.http.scaladsl.model.http2.PeerClosedStreamException
    akka.http.scaladsl.model.http2.Http2Exception
    akka.http.scaladsl.model.SimpleRequestResponseAttribute
    akka.http.scaladsl.model.RequestResponseAssociation
    ```

Java
:   ```java
    akka.http.javadsl.ClientTransport
    akka.http.javadsl.ConnectionContext#httpsClient
    akka.http.javadsl.ConnectionContext#httpsServer
    akka.http.javadsl.settings.ClientConnectionSettings#getTransport
    akka.http.javadsl.settings.ClientConnectionSettings#withTransport
    akka.http.javadsl.settings.ConnectionPoolSettings#appendHostOverride
    akka.http.javadsl.settings.ConnectionPoolSettings#getPoolImplementation
    akka.http.javadsl.settings.ConnectionPoolSettings#getResponseEntitySubscriptionTimeout
    akka.http.javadsl.settings.ConnectionPoolSettings#withHostOverrides
    akka.http.javadsl.settings.ConnectionPoolSettings#withPoolImplementation
    akka.http.javadsl.settings.ConnectionPoolSettings#withResponseEntitySubscriptionTimeout
    akka.http.javadsl.settings.PoolImplementation
    akka.http.javadsl.settings.PreviewServerSettings
    akka.http.javadsl.settings.ServerSentEventSettings
    ```
  
## Versioning and Compatibility

Starting from version 10.1.0, there will be two active release branches:
- The "current" release line (in `main`), where we will basically continue to evolve Apache Pekko HTTP in the same way as currently. New features will be introduced here incrementally.
- The "previous" release line (in a release-10.x branch), where the focus is on stability. We will continue to maintain the previous release by fixing serious bugs but it will not see new features. Previous releases will see less frequent releases over time.

It is planned to rotate versions in an annual fashion. Meaning a new minor version will be created every year.
Whenever a new minor version is created, it will move the at that point current minor version release series over into maintenance mode, making it the "previous".
The former "previous" release has reached its end of life at this point. This way every release line is supported for two years.

The Apache Pekko HTTP Team currently does not intend to break binary compatibility, i.e. no binary incompatible 11.x.y release is currently planned.
    
## Specific versions inter-op discussion

In this section we discuss some of the specific cases of compatibility between versions of Apache Pekko HTTP and Akka itself.

For example, you may be interested in those examples if you encountered the following exception in your system when upgrading parts 
of your libraries: `Detected java.lang.NoSuchMethodError error, which MAY be caused by incompatible Pekko versions on the classpath. Please note that a given Pekko version MUST be the same across all modules of Pekko that you are using, e.g. if you use pekko-actor [2.5.3 (resolved from current classpath)] all other core Apache Pekko modules MUST be of the same version. External projects like Apache Pekko Connectors, Persistence plugins or Apache Pekko HTTP etc. have their own version numbers - please make sure you're using a compatible set of libraries.`

### Compatibility with Apache Pekko

To facilitate supporting multiple minor versions of Apache Pekko we do not depend on `pekko-stream`
explicitly but mark it as a `provided` dependency in our build. That means that you will *always* have to add
a manual dependency to `pekko-stream`.

The same goes for `pekko-http-testkit`: If the testkit is used, explicitly declare the dependency on `pekko-stream-testkit` of same Pekko version as `pekko-stream`.

@@dependency [sbt,Gradle,Maven] {
  symbol1=AkkaVersion
  value1=$akka.version$
  bomGroup2=org.apache.pekko
  bomArtifact2=pekko-http-bom_$scala.binary.version$
  bomVersionSymbols2=AkkaHttpVersion
  symbol2="AkkaHttpVersion"
  value2="$project.version$"
  group1="org.apache.pekko" artifact1="pekko-http_$scala.binary.version$" version1="AkkaHttpVersion"
  group2="org.apache.pekko" artifact2="pekko-stream_$scala.binary.version$" version2=AkkaVersion
  group3="org.apache.pekko" artifact3="pekko-http-testkit_$scala.binary.version$" version3=AkkaHttpVersion scope3=Test
  group4="org.apache.pekko" artifact4="pekko-stream-testkit_$scala.binary.version$" version4=AkkaVersion scope4=Test
}
