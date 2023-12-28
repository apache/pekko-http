# Cors

Apache Pekko HTTP's cors module provides support for the [W3C cors standard](https://www.w3.org/TR/cors/)

## Dependency

To use Apache Pekko HTTP Cors, add the module to your project:

@@dependency [sbt,Gradle,Maven] {
bomGroup2="org.apache.pekko" bomArtifact2="pekko-http-bom_$scala.binary.version$" bomVersionSymbols2="PekkoHttpVersion"
symbol="PekkoHttpVersion"
value="$project.version$"
group="org.apache.pekko"
artifact="pekko-http-cors_$scala.binary.version$"
version="PekkoHttpVersion"
}

## Quick Start
The simplest way to enable CORS in your application is to use the `cors` directive.
Settings are passed as a parameter to the directive, with your overrides loaded from the `application.conf`.

@@@ div { .group-scala }
```scala
import org.apache.pekko.http.cors.scaladsl.CorsDirectives._

val route: Route = cors() {
  complete(...)
}
```
@@@
@@@ div { .group-java }
```java
import static org.apache.pekko.http.cors.javadsl.CorsDirectives.*;

final Route route = cors(() -> {
    complete(...)
})
```
@@@

The settings can be updated programmatically too.
@@@ div { .group-scala }
```scala
val settings = CorsSettings(...).withAllowGenericHttpRequests(false)
val strictRoute: Route = cors(settings) {
  complete(...)
}
```
@@@
@@@ div { .group-java }
```java
final CorsSettings settings = CorsSettings.create(...).withAllowGenericHttpRequests(false);
final Route route = cors(settings, () -> {
    complete(...)
});
```
@@@

## Rejection
The CORS directives can reject requests using the `CorsRejection` class. Requests can be either malformed or not allowed to access the resource.

A rejection handler is provided by the library to return meaningful HTTP responses. Read the @ref[Pekko documentation](../routing-dsl/rejections.md) to learn more about rejections, or if you need to write your own handler.

Scala
:   @@snip [CorsServerExample.scala](/docs/src/test/scala/docs/http/scaladsl/server/cors/CorsServerExample.scala) { #cors-server-example }

Java
:   @@snip [CorsServerExample.java](/docs/src/test/java/docs/http/javadsl/server/cors/CorsServerExample.java) { #cors-server-example }

#### allowGenericHttpRequests / getAllowGenericHttpRequests
If `true`, allow generic requests (that are outside the scope of the specification) to pass through the directive. Else, strict CORS filtering is applied and any invalid request will be rejected.

#### allowCredentials / getAllowCredentials
Indicates whether the resource supports user credentials.  If `true`, the header `Access-Control-Allow-Credentials` is set in the response, indicating the actual request can include user credentials.

Examples of user credentials are: cookies, HTTP authentication or client-side certificates.

#### allowedOrigins / getAllowedOrigins
List of origins that the CORS filter must allow. Can also be set to `*` to allow access to the resource from any origin. Controls the content of the `Access-Control-Allow-Origin` response header:
* if parameter is `*` **and** credentials are not allowed, a `*` is set in `Access-Control-Allow-Origin`.
* otherwise, the origins given in the `Origin` request header are echoed.

Hostname starting with `*.` will match any sub-domain. The scheme and the port are always strictly matched.

The actual or preflight request is rejected if any of the origins from the request is not allowed.

#### allowedHeaders / getAllowedHeaders
List of request headers that can be used when making an actual request. Controls the content of the `Access-Control-Allow-Headers` header in a preflight response:
* if parameter is `*`, the headers from `Access-Control-Request-Headers` are echoed.
* otherwise the parameter list is returned as part of the header.

#### allowedMethods / getAllowedMethods
List of methods that can be used when making an actual request. The list is returned as part of the `Access-Control-Allow-Methods` preflight response header.

The preflight request will be rejected if the `Access-Control-Request-Method` header's method is not part of the list.

#### exposedHeaders / getAllowedMethods
List of headers (other than [simple response headers](https://www.w3.org/TR/cors/#simple-response-header)) that browsers are allowed to access. If not empty, this list is returned as part of the `Access-Control-Expose-Headers` header in the actual response.

#### maxAge / getMaxAge
When set, the amount of seconds the browser is allowed to cache the results of a preflight request. This value is returned as part of the `Access-Control-Max-Age` preflight response header. If @scala[`None`]@java[`OptionalLong.empty()`], the header is not added to the preflight response.

## Benchmarks

Benchmarks for Apache Pekko cors are located within the @github[http-bench-jmh project](/http-bench-jmh/src/main/scala/org/apache/pekko/http/cors/CorsBenchmark.scala) along with
@github[instructions](/http-bench-jmh/README.md) on how to run them.

Please look at the original project [Akka Http Cors](https://github.com/lomigmegard/akka-http-cors) for previous benchmarks with Akka Http.
