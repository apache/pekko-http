# Compatibility Guidelines

## Binary Compatibility Rules

Apache Pekko HTTP follows the same binary compatibility rules as Apache Pekko itself.
In short it means that the versioning scheme should be read as `major.minor.patch`,
wherein all versions with the same `major` version are backwards binary-compatible,
with the exception of `@ApiMayChange`, `@InternalApi` or `@DoNotInherit` marked APIs 
or other specifically documented special-cases.

For more information and a detailed discussion of these rules and guarantees please refer to
@extref:[The @DoNotInherit and @ApiMayChange markers](pekko-docs:common/binary-compatibility-rules.html#the-donotinherit-and-apimaychange-markers).

### Components with no Binary Compatibility Guarantee

The following components and modules don't have the previously mentioned binary compatibility guaranteed within minor or
patch versions. However, binary compatibility will be attempted to be kept as much as possible.

#### pekko-http

Scala
:   ```scala
    org.apache.pekko.http.scaladsl.server.directives.FileUploadDirectives#storeUploadedFile
    org.apache.pekko.http.scaladsl.server.directives.FileUploadDirectives#storeUploadedFiles
    org.apache.pekko.http.scaladsl.server.directives.FileUploadDirectives#fileUploadAll
    org.apache.pekko.http.scaladsl.marshalling.sse.EventStreamMarshalling
    org.apache.pekko.http.scaladsl.server.HttpApp
    org.apache.pekko.http.scaladsl.unmarshalling.sse.EventStreamParser
    org.apache.pekko.http.scaladsl.unmarshalling.sse.EventStreamUnmarshalling
    org.apache.pekko.http.scaladsl.OutgoingConnectionBuilder#managedPersistentHttp2
    org.apache.pekko.http.scaladsl.OutgoingConnectionBuilder#managedPersistentHttp2WithPriorKnowledge
    ```

Java
:   ```java
    org.apache.pekko.http.javadsl.common.PartialApplication#bindParameter
    org.apache.pekko.http.javadsl.server.Directives#anyOf (all overloads)
    org.apache.pekko.http.javadsl.server.Directives#allOf (all overloads)
    org.apache.pekko.http.javadsl.server.directives.FileUploadDirectives#storeUploadedFile
    org.apache.pekko.http.javadsl.server.directives.FileUploadDirectives#storeUploadedFiles
    org.apache.pekko.http.javadsl.server.directives.FileUploadDirectives#fileUploadAll
    org.apache.pekko.http.javadsl.server.HttpApp
    org.apache.pekko.http.javadsl.model.RequestResponseAssociation
    org.apache.pekko.http.javadsl.OutgoingConnectionBuilder#managedPersistentHttp2WithPriorKnowledge
    org.apache.pekko.http.javadsl.OutgoingConnectionBuilder#managedPersistentHttp2
    ```    

#### pekko-http-caching

Scala
:   ```scala
    org.apache.pekko.http.caching.LfuCache
    org.apache.pekko.http.caching.scaladsl.Cache
    org.apache.pekko.http.scaladsl.server.directives.CachingDirectives
    ```

Java
:   ```java
    org.apache.pekko.http.caching.LfuCache
    org.apache.pekko.http.caching.javadsl.Cache
    org.apache.pekko.http.javadsl.server.directives.CachingDirectives
    ```    

#### pekko-http-core

Scala
:   ```scala
    org.apache.pekko.http.scaladsl.ClientTransport
    org.apache.pekko.http.scaladsl.ConnectionContext#httpsClient
    org.apache.pekko.http.scaladsl.ConnectionContext#httpsServer
    org.apache.pekko.http.scaladsl.settings.PoolImplementation
    org.apache.pekko.http.scaladsl.settings.ClientConnectionSettings#transport
    org.apache.pekko.http.scaladsl.settings.ClientConnectionSettings#withTransport
    org.apache.pekko.http.scaladsl.settings.ConnectionPoolSettings#appendHostOverride
    org.apache.pekko.http.scaladsl.settings.ConnectionPoolSettings#poolImplementation
    org.apache.pekko.http.scaladsl.settings.ConnectionPoolSettings#responseEntitySubscriptionTimeout
    org.apache.pekko.http.scaladsl.settings.ConnectionPoolSettings#withHostOverrides
    org.apache.pekko.http.scaladsl.settings.ConnectionPoolSettings#withOverrides
    org.apache.pekko.http.scaladsl.settings.ConnectionPoolSettings#withPoolImplementation
    org.apache.pekko.http.scaladsl.settings.ConnectionPoolSettings#withResponseEntitySubscriptionTimeout
    org.apache.pekko.http.scaladsl.settings.HostOverride
    org.apache.pekko.http.scaladsl.settings.Http2ServerSettings
    org.apache.pekko.http.scaladsl.settings.Http2ClientSettings
    org.apache.pekko.http.scaladsl.settings.PreviewServerSettings
    org.apache.pekko.http.scaladsl.settings.ServerSentEventSettings
    org.apache.pekko.http.scaladsl.model.headers.CacheDirectives.immutableDirective
    org.apache.pekko.http.scaladsl.model.headers.X-Forwarded-Host
    org.apache.pekko.http.scaladsl.model.headers.X-Forwarded-Proto
    org.apache.pekko.http.scaladsl.model.headers.Trailer
    org.apache.pekko.http.scaladsl.model.http2.PeerClosedStreamException
    org.apache.pekko.http.scaladsl.model.http2.Http2Exception
    org.apache.pekko.http.scaladsl.model.SimpleRequestResponseAttribute
    org.apache.pekko.http.scaladsl.model.RequestResponseAssociation
    ```

Java
:   ```java
    org.apache.pekko.http.javadsl.ClientTransport
    org.apache.pekko.http.javadsl.ConnectionContext#httpsClient
    org.apache.pekko.http.javadsl.ConnectionContext#httpsServer
    org.apache.pekko.http.javadsl.settings.ClientConnectionSettings#getTransport
    org.apache.pekko.http.javadsl.settings.ClientConnectionSettings#withTransport
    org.apache.pekko.http.javadsl.settings.ConnectionPoolSettings#appendHostOverride
    org.apache.pekko.http.javadsl.settings.ConnectionPoolSettings#getPoolImplementation
    org.apache.pekko.http.javadsl.settings.ConnectionPoolSettings#getResponseEntitySubscriptionTimeout
    org.apache.pekko.http.javadsl.settings.ConnectionPoolSettings#withHostOverrides
    org.apache.pekko.http.javadsl.settings.ConnectionPoolSettings#withPoolImplementation
    org.apache.pekko.http.javadsl.settings.ConnectionPoolSettings#withResponseEntitySubscriptionTimeout
    org.apache.pekko.http.javadsl.settings.PoolImplementation
    org.apache.pekko.http.javadsl.settings.PreviewServerSettings
    org.apache.pekko.http.javadsl.settings.ServerSentEventSettings
    ```
  
## Versioning and Compatibility

The Apache Pekko HTTP Team currently does not intend to break binary compatibility, i.e. no binary incompatible 2.x.y release is currently planned.
    
## Specific versions inter-op discussion

In this section we discuss some of the specific cases of compatibility between versions of Apache Pekko HTTP and Apache Pekko itself.

For example, you may be interested in those examples if you encountered the following exception in your system when upgrading parts 
of your libraries: `Detected java.lang.NoSuchMethodError error, which MAY be caused by incompatible Pekko versions on the classpath. Please note that a given Pekko version MUST be the same across all modules of Apache Pekko that you are using, e.g. if you use pekko-actor [2.5.3 (resolved from current classpath)] all other core Apache Pekko modules MUST be of the same version. External projects like Apache Pekko Connectors, Persistence plugins or Apache Pekko HTTP etc. have their own version numbers - please make sure you're using a compatible set of libraries.`

### Compatibility with Apache Pekko

To facilitate supporting multiple minor versions of Apache Pekko we do not depend on `pekko-stream`
explicitly but mark it as a `provided` dependency in our build. That means that you will *always* have to add
a manual dependency to `pekko-stream`.

The same goes for `pekko-http-testkit`: If the testkit is used, explicitly declare the dependency on `pekko-stream-testkit` of same Pekko version as `pekko-stream`.

@@dependency [sbt,Gradle,Maven] {
  symbol1=PekkoVersion
  value1=$pekko.version$
  bomGroup2=org.apache.pekko
  bomArtifact2=pekko-http-bom_$scala.binary.version$
  bomVersionSymbols2=PekkoHttpVersion
  symbol2="PekkoHttpVersion"
  value2="$project.version$"
  group1="org.apache.pekko" artifact1="pekko-http_$scala.binary.version$" version1="PekkoHttpVersion"
  group2="org.apache.pekko" artifact2="pekko-stream_$scala.binary.version$" version2=PekkoVersion
  group3="org.apache.pekko" artifact3="pekko-http-testkit_$scala.binary.version$" version3=PekkoHttpVersion scope3=Test
  group4="org.apache.pekko" artifact4="pekko-stream-testkit_$scala.binary.version$" version4=PekkoVersion scope4=Test
}
