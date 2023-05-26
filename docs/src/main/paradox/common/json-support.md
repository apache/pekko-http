# JSON Support

Apache Pekko HTTP's @ref[marshalling](marshalling.md) and @ref[unmarshalling](unmarshalling.md) infrastructure makes it rather easy to seamlessly convert application-domain objects from and to JSON.
Integration with @scala[[spray-json]]@java[[Jackson]] is provided out of the box through the @scala[`pekko-http-spray-json`]@java[`pekko-http-jackson`] module.
Integration with other JSON libraries are supported by the community.
See @ref[the list of current community extensions for Apache Pekko HTTP](../examples-and-extensions.md#extensions).

@@@ div { .group-java }

## Jackson Support

To make use of the support module for (un)marshalling from and to JSON with [Jackson], add a library dependency onto:

@@dependency [sbt,Gradle,Maven] {
  bomGroup2="org.apache.pekko" bomArtifact2="pekko-http-bom_$scala.binary.version$" bomVersionSymbols2="PekkoHttpVersion"
  symbol="PekkoHttpVersion"
  value="$project.version$"
  group="org.apache.pekko"
  artifact="pekko-http-jackson_$scala.binary.version$"
  version="PekkoHttpVersion"
}

Use `org.apache.pekko.http.javadsl.marshallers.jackson.Jackson.unmarshaller(T.class)` to create an @apidoc[Unmarshaller[HttpEntity,T]] which expects the request
body (HttpEntity) to be of type `application/json` and converts it to `T` using Jackson.

@@snip [PetStoreExample.java](/http-tests/src/main/java/org/apache/pekko/http/javadsl/server/examples/petstore/PetStoreExample.java) { #imports #unmarshall }

Use `org.apache.pekko.http.javadsl.marshallers.jackson.Jackson.marshaller(T.class)` to create a @apidoc[Marshaller[T,RequestEntity]] which can be used with
`RequestContext.complete` or `RouteDirectives.complete` to convert a POJO to an HttpResponse.

@@snip [PetStoreExample.java](/http-tests/src/main/java/org/apache/pekko/http/javadsl/server/examples/petstore/PetStoreExample.java) { #imports #marshall }

Refer to @github[this file](/http-tests/src/main/java/org/apache/pekko/http/javadsl/server/examples/petstore/PetStoreExample.java) in the sources for the complete example.

@@@


@@@ div { .group-scala }

## spray-json Support

The @scaladoc[SprayJsonSupport](org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport) trait provides a `FromEntityUnmarshaller[T]` and `ToEntityMarshaller[T]` for every type `T`
that an implicit `spray.json.RootJsonReader` and/or `spray.json.RootJsonWriter` (respectively) is available for.

To enable automatic support for (un)marshalling from and to JSON with [spray-json], add a library dependency onto:

@@dependency [sbt,Gradle,Maven] {
  bomGroup2="org.apache.pekko" bomArtifact2="pekko-http-bom_$scala.binary.version$" bomVersionSymbols2="PekkoHttpVersion"
  symbol="PekkoHttpVersion"
  value="$project.version$"
  group="org.apache.pekko"
  artifact="pekko-http-spray-json_$scala.binary.version$"
  version="PekkoHttpVersion"
}

Next, provide a `RootJsonFormat[T]` for your type and bring it into scope. Check out the [spray-json] documentation for more info on how to do this.

Finally, import the `FromEntityUnmarshaller[T]` and `ToEntityMarshaller[T]` implicits directly from `SprayJsonSupport` as shown in the example below or mix the `org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport` trait into your JSON support module.

Once you have done this (un)marshalling between JSON and your type `T` should work nicely and transparently.

@@snip [SprayJsonExampleSpec.scala](/docs/src/test/scala/docs/http/scaladsl/SprayJsonExampleSpec.scala) { #minimal-spray-json-example }

@@@ 

<a id="json-streaming-client-side"></a>
## Consuming JSON Streaming style APIs

A popular way of implementing streaming APIs is [JSON Streaming](https://en.wikipedia.org/wiki/JSON_Streaming) (see @ref[Source Streaming](../routing-dsl/source-streaming-support.md)
for documentation on building server-side of such API).

Depending on the way the API returns the streamed JSON (newline delimited, raw sequence of objects, or "infinite array") 
you may have to apply a different framing mechanism, but the general idea remains the same: consuming the infinite entity stream
and applying a framing to it, such that the single objects can be easily deserialized using the usual marshalling infrastructure:

Scala
:   @@snip [EntityStreamingSpec.scala](/http-tests/src/test/scala/org/apache/pekko/http/scaladsl/server/EntityStreamingSpec.scala) { #json-streaming-client-example }
 
Java
:   @@snip [HttpClientExampleDocTest.java](/docs/src/test/java/docs/http/javadsl/server/JsonStreamingExamplesTest.java) { #json-streaming-client-example-raw }

@@@ div { .group-scala }

In the above example the marshalling is handled by the implicitly provided `JsonEntityStreamingSupport`, which is also used when building server-side streaming APIs.
You can also achieve the same more explicitly, by manually connecting the entity byte stream through a framing and then deserialization stage: 

Scala
:   @@snip [EntityStreamingSpec.scala](/http-tests/src/test/scala/org/apache/pekko/http/scaladsl/server/EntityStreamingSpec.scala) { #json-streaming-client-example-raw }
 
@@@

@@@ div { .group-java }

In the above example the `JsonEntityStreamingSupport` class is used to obtain the proper framing, though you could also
pick the framing manually by using `org.apache.pekko.stream.javadsl.Framing` or `org.apache.pekko.stream.javadsl.JsonFraming`. 
Framing stages are used to "chunk up" the pieces of incoming bytes into appropriately sized pieces of valid JSON,
which then can be handled easily by a not-streaming JSON serializer such as jackson in the example. This technique is simpler to use
and often good enough rather than writing a fully streaming JSON parser (which also is possible). 

@@@ 


@@@ div { .group-scala }

## Pretty printing

By default, spray-json marshals your types to compact printed JSON by implicit conversion using `CompactPrinter`, as defined in:

@@snip [SprayJsonSupport.scala](/http-marshallers-scala/http-spray-json/src/main/scala/org/apache/pekko/http/scaladsl/marshallers/sprayjson/SprayJsonSupport.scala) { #sprayJsonMarshallerConverter }

Alternatively to marshal your types to pretty printed JSON, bring a `PrettyPrinter` in scope to perform implicit conversion.

@@snip [SprayJsonPrettyMarshalSpec.scala](/docs/src/test/scala/docs/http/scaladsl/SprayJsonPrettyMarshalSpec.scala) { #example }

To learn more about how spray-json works please refer to its [documentation][spray-json].

@@@

[spray-json]: https://github.com/spray/spray-json
[jackson]: https://github.com/FasterXML/jackson
