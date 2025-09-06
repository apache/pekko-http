# Server-Sent Events Support

Server-Sent Events (SSE) is a lightweight and [standardized](https://www.w3.org/TR/eventsource/)
protocol for pushing notifications from an HTTP server to a client. In contrast to WebSocket, which
offers bi-directional communication, SSE only allows for one-way communication from the server to
the client. If that's all you need, SSE has the advantages to be much simpler, to rely on HTTP only
and to offer retry semantics on broken connections by the browser.

According to the SSE specification clients can request an event stream from the server via HTTP. The
server responds with the media type `text/event-stream` which has the fixed character encoding UTF-8
and keeps the response open to send events to the client when available. Events are textual
structures which carry fields and are terminated by an empty line, e.g.

```
data: { "username": "John Doe" }
event: added
id: 42

data: another event
```

Clients can optionally signal the last seen event to the server via the @scala[`Last-Event-ID`]@java[`LastEventId`] header, e.g.
after a reconnect.

## Model

Apache Pekko HTTP represents event streams as @apidoc[Source[ServerSentEvent, \_]] where @apidoc[ServerSentEvent] is a
@scala[case] class with the following read-only properties:

- @scala[`data: String`]@java[`String data`] – the actual payload, may span multiple lines
- @scala[`eventType: Option[String]`]@java[`Optional<String> type`] – optional qualifier, e.g. "added", "removed", etc.
- @scala[`id: Option[String]`]@java[`Optional<String> id`] – optional identifier
- @scala[`retry: Option[Int]`]@java[`OptionalInt retry`] – optional reconnection delay in milliseconds

In accordance to the SSE specification Apache Pekko HTTP also provides the @scala[`Last-Event-ID`]@java[`LastEventId`] header and the
@scala[`text/event-stream`]@java[`TEXT_EVENT_STREAM`] media type.

## Server-side usage: marshalling

In order to respond to an HTTP request with an event stream, you have to
@scala[bring the implicit `ToResponseMarshaller[Source[ServerSentEvent, \_]]` defined by @apidoc[EventStreamMarshalling] into the scope defining the respective route]@java[use the `EventStreamMarshalling.toEventStream` marshaller]:

Scala
:  @@snip [ServerSentEventsExampleSpec.scala](/docs/src/test/scala/docs/http/scaladsl/ServerSentEventsExampleSpec.scala) { #event-stream-marshalling-example }

Java
:  @@snip [EventStreamMarshallingTest.java](/http-tests/src/test/java/org/apache/pekko/http/javadsl/marshalling/sse/EventStreamMarshallingTest.java) { #event-stream-marshalling-example }

## Client-side usage: unmarshalling

In order to unmarshal an event stream as @apidoc[Source[ServerSentEvent, \_]], you have to @scala[bring the implicit `FromEntityUnmarshaller[Source[ServerSentEvent, _]]` defined by @apidoc[EventStreamUnmarshalling] into scope]@java[use the `EventStreamUnmarshalling.fromEventsStream` unmarshaller]:

Scala
:  @@snip [ServerSentEventsExampleSpec.scala](/docs/src/test/scala/docs/http/scaladsl/ServerSentEventsExampleSpec.scala) { #event-stream-unmarshalling-example }

Java
:  @@snip [EventStreamMarshallingTest.java](/http-tests/src/test/java/org/apache/pekko/http/javadsl/unmarshalling/sse/EventStreamUnmarshallingTest.java) { #event-stream-unmarshalling-example }

## Configuration

Apache Pekko HTTP provides several configuration options for Server-Sent Events handling:

### Message Size Limits

The SSE client parser has configurable limits to handle various message sizes:

```hocon
pekko.http.sse {
  # The maximum size for parsing received server-sent events.
  # This value must be larger than `max-line-size`. Set to 0 to disable limit entirely (unlimited).
  max-event-size = 115713

  # The maximum size for parsing received lines of a server-sent event. Set to 0 to disable limit entirely (unlimited).
  max-line-size = 115712
}
```

### Oversized Message Handling

When SSE messages exceed the configured `max-line-size`, Apache Pekko HTTP provides four handling strategies:

- **fail-stream** (default): Fails the stream with a clear error message, maintaining backward compatibility
- **log-and-skip**: Logs a warning and skips the oversized message, continuing stream processing
- **truncate**: Logs a warning and truncates the message to the configured limit, continuing processing
- **dead-letter**: Logs a warning and sends the oversized message to the dead letter queue, continuing processing

```hocon
pekko.http.sse {
  # How to handle messages that exceed max-line-size limit
  # Options:
  #   "fail-stream" - Fail the stream with a clear error message (default)
  #   "log-and-skip" - Log a warning and skip the oversized message
  #   "truncate" - Log a warning and truncate the message to max-line-size
  #   "dead-letter" - Log a warning, send oversized message to dead letters
  oversized-message-handling = "fail-stream"
}
```

For applications that need to handle very large messages (like blockchain data or detailed JSON payloads), consider 
setting `max-line-size = 0` to disable limits entirely, or use one of the non-failing handling modes.

Notice that if you are looking for a resilient way to permanently subscribe to an event stream,
Apache Pekko Connectors provides the [EventSource](https://pekko.apache.org/docs/pekko-connectors/current/sse.html)connector which reconnects automatically with the id of the 
last seen event.
