<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# Unmarshalling

"Unmarshalling" is the process of converting some kind of a lower-level representation, often a "wire format", into a
higher-level (object) structure. Other popular names for it are "Deserialization" or "Unpickling".

In Apache Pekko HTTP "Unmarshalling" means the conversion of a lower-level source object, e.g. a `MessageEntity`
(which forms the "entity body" of an HTTP request or response) or a full @apidoc[HttpRequest] or @apidoc[HttpResponse],
into an instance of type `T`.

## Basic Design

Unmarshalling of instances of type `A` into instances of type `B` is performed by an @apidoc[Unmarshaller[A, B]].

@@@ div { .group-scala }
Apache Pekko HTTP also predefines a number of helpful aliases for the types of unmarshallers that you'll likely work with most:

@@snip [package.scala](/http/src/main/scala/org/apache/pekko/http/scaladsl/unmarshalling/package.scala) { #unmarshaller-aliases }

@@@

At its core an @apidoc[Unmarshaller[A, B]] is very similar to a @scala[function `A => Future[B]`]@java[`Function<A, CompletionStage<B>>`] and as such quite a bit simpler
than its @ref[marshalling](marshalling.md) counterpart. The process of unmarshalling does not have to support
content negotiation which saves two additional layers of indirection that are required on the marshalling side.

## Using unmarshallers

For an example on how to use an unmarshaller on the server side, see for example the @ref[Dynamic Routing Example](../routing-dsl/index.md#dynamic-routing-example).
For the client side, see @ref[Processing Responses](../client-side/request-and-response.md#processing-responses)

## Predefined Unmarshallers

Apache Pekko HTTP already predefines a number of unmarshallers for the most common types.
Specifically these are:

 * @scala[@scaladoc[PredefinedFromStringUnmarshallers](org.apache.pekko.http.scaladsl.unmarshalling.PredefinedFromStringUnmarshallers)]
   @java[@javadoc[StringUnmarshallers](org.apache.pekko.http.javadsl.unmarshalling.StringUnmarshallers)]
    * `Byte`
    * `Short`
    * @scala[`Int`]@java[`Integer`]
    * `Long`
    * `Float`
    * `Double`
    * `Boolean`
 * @scala[@scaladoc[PredefinedFromEntityUnmarshallers](org.apache.pekko.http.scaladsl.unmarshalling.PredefinedFromEntityUnmarshallers)]
   @java[@apidoc[Unmarshaller]]
    * @scala[`Array[Byte]`]@java[`byte[]`]
    * @apidoc[org.apache.pekko.util.ByteString]
    * @scala[`Array[Char]`]@java[`char[]`]
    * `String`
    * @scala[`org.apache.pekko.http.scaladsl.model.FormData`]@java[`org.apache.pekko.http.javadsl.model.FormData`]

@@@ div { .group-scala }
 * @scaladoc[GenericUnmarshallers](org.apache.pekko.http.scaladsl.unmarshalling.GenericUnmarshallers)
    * @apidoc[Unmarshaller[T, T]](Unmarshaller) (identity unmarshaller)
    * @apidoc[Unmarshaller[Option[A], B]], if an @apidoc[Unmarshaller[A, B]] is available
    * @apidoc[Unmarshaller[A, Option[B]]], if an @apidoc[Unmarshaller[A, B]] is available
@@@

Additional unmarshallers are available in separate modules for specific content types, such as
@ref[JSON](json-support.md)@scala[ and @ref[XML](xml-support.md)].

## URL-encoded form data

Since Pekko HTTP 2.1.0, the default `application/x-www-form-urlencoded` unmarshaller ignores empty components between `&` separators,
including leading and trailing separators. For example, `&a=1&&` produces only the field `a=1`, rather than additional
empty-name fields. This matches the empty-component handling in the
[URL Standard form parser](https://url.spec.whatwg.org/#urlencoded-parsing).
Explicit empty-name fields (`=` and `=x`) and nonempty names without `=` remain supported, and charset handling is unchanged.

Applications relying on the additional empty-name fields should account for this behavior change. It also affects
round-tripping a manually constructed `FormData` containing `"" -> Uri.Query.EmptyValue`: this tuple renders as an
empty component and is discarded when unmarshalling. Use `"" -> ""` to represent an explicit empty-name, empty-value
field, which renders as `=` and is preserved. Generic `Uri.Query` parsing and rendering are unchanged.
Consequently, form-field directives discard these empty components in URL-encoded request bodies, while query-parameter
directives still retain them in the URI query. For example, `&a=1&&` as a form body yields only `a=1`, but the same
string as a URI query also contains empty-name parameters.

@@@ div { .group-scala }

## Implicit Resolution

The unmarshalling infrastructure of Apache Pekko HTTP relies on a type-class based approach, which means that @apidoc[Unmarshaller]
instances from a certain type `A` to a certain type `B` have to be available implicitly.

The implicits for most of the predefined unmarshallers in Apache Pekko HTTP are provided through the companion object of the
@apidoc[Unmarshaller] trait. This means that they are always available and never need to be explicitly imported.
Additionally, you can simply "override" them by bringing your own custom version into local scope.

@@@

## Custom Unmarshallers

Apache Pekko HTTP gives you a few convenience tools for constructing unmarshallers for your own types.
Usually you won't have to "manually" implement the @apidoc[Unmarshaller] @scala[trait]@java[class] directly.
Rather, it should be possible to use one of the convenience construction helpers defined on
@scala[the @apidoc[Unmarshaller] companion]@java[@apidoc[Unmarshaller]]:

Scala
:  @@snip [Unmarshaller.scala](/http/src/main/scala/org/apache/pekko/http/scaladsl/unmarshalling/Unmarshaller.scala) { #unmarshaller-creation }

Java
:  @@snip [Unmarshallers.scala](/http/src/main/java/org/apache/pekko/http/javadsl/unmarshalling/Unmarshallers.java) { #unmarshaller-creation }

@@@ note
To avoid unnecessary memory pressure, unmarshallers should make sure to either fully consume the incoming entity data stream, or make sure it is properly cancelled on error.
Failure to do so might keep the remaining part of the stream in memory for longer than necessary.
@@@

## Deriving Unmarshallers

Sometimes you can save yourself some work by reusing existing unmarshallers for your custom ones.
The idea is to "wrap" an existing unmarshaller with some logic to "re-target" it to your type.

Usually what you want to do is to transform the output of some existing unmarshaller and convert it to your type.
For this type of unmarshaller transformation Apache Pekko HTTP defines these methods:

@@@ div { .group-scala }
 * `baseUnmarshaller.transform`
 * `baseUnmarshaller.map`
 * `baseUnmarshaller.mapWithInput`
 * `baseUnmarshaller.flatMap`
 * `baseUnmarshaller.flatMapWithInput`
 * `baseUnmarshaller.recover`
 * `baseUnmarshaller.withDefaultValue`
 * `baseUnmarshaller.mapWithCharset` (only available for FromEntityUnmarshallers)
 * `baseUnmarshaller.forContentTypes` (only available for FromEntityUnmarshallers)
@@@

@@@ div { .group-java }
 * `baseMarshaller.thenApply`
 * `baseMarshaller.flatMap`
 * `Unmarshaller.forMediaType` (to derive from a @apidoc[HttpEntity] unmarshaller)
 * `Unmarshaller.forMediaTypes` (to derive from a @apidoc[HttpEntity] unmarshaller)
@@@

The method signatures should make their semantics relatively clear.

## Using Unmarshallers

In many places throughout Apache Pekko HTTP unmarshallers are used implicitly, e.g. when you want to access the @ref[entity](../routing-dsl/directives/marshalling-directives/entity.md)
of a request using the @ref[Routing DSL](../routing-dsl/index.md).

However, you can also use the unmarshalling infrastructure directly if you wish, which can be useful for example in tests.
The best entry point for this is the @scala[`org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal` object]@java[`org.apache.pekko.http.javadsl.unmarshalling.StringUnmarshallers` class], which you can use like this:

Scala
:  @@snip [UnmarshalSpec.scala](/docs/src/test/scala/docs/http/scaladsl/UnmarshalSpec.scala) { #use-unmarshal }

Java
:  @@snip [UnmarshalTest.scala](/docs/src/test/java/docs/http/javadsl/UnmarshalTest.java) { #imports #use-unmarshal }
