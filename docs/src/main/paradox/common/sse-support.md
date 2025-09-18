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
  # The maximum size for parsing server-sent events. Set to 0 to disable limit entirely (unlimited).
  max-event-size = 8192

  # The maximum size for parsing lines of a server-sent event. Set to 0 to disable limit entirely (unlimited).
  max-line-size = 4096
}
```

### Oversized Message Handling

Apache Pekko HTTP uses a two-stage parsing process for SSE streams, and oversized content can be handled at either stage:

1. **Line-level parsing**: Individual lines are checked against `max-line-size`
2. **Event-level parsing**: Complete events are limited to `max-event-size`

When SSE content exceeds the configured limits, Apache Pekko HTTP provides four handling strategies that can be configured separately for lines and events:

- **fail-stream** (default): Fails the stream with an error message
- **log-and-skip**: Logs a warning and skips the oversized content, continuing stream processing
- **truncate**: Logs an info message and handles oversized content appropriately, continuing processing
- **dead-letter**: Sends the oversized content to the dead letter queue, continuing processing

**Warning about truncate strategy**: For event-level truncation, the strategy drops entire lines that would exceed event size limits rather than truncating field values. This can change event semantics in unexpected ways when non-data fields (like `id:` or `event:`) are dropped. For predictable behavior, ensure that `id:` and `event:` fields appear before `data:` fields in your SSE events, or consider using `log-and-skip` or `dead-letter` strategies instead.

```hocon
pekko.http.sse {
  # How to handle lines that exceed max-line-size limit
  # Options:
  #   "fail-stream" - Fail the stream with an error message (default)
  #   "log-and-skip" - Log a warning and skip the oversized line, continuing stream processing
  #   "truncate" - Log an info message and truncate the line to max-line-size, continuing processing
  #   "dead-letter" - Send oversized line to the dead letter queue, continuing processing
  oversized-line-handling = "fail-stream"

  # How to handle events that exceed max-event-size limit
  # Options:
  #   "fail-stream" - Fail the stream with an error message (default)
  #   "log-and-skip" - Log a warning and skip the oversized event, continuing stream processing
  #   "truncate" - Log an info message and drop lines that would exceed max-event-size, continuing processing
  #   "dead-letter" - Send oversized event to the dead letter queue, continuing processing
  oversized-event-handling = "fail-stream"
}
```

#### Line vs Event Handling Examples

Line-level and event-level size limits are imposed separately and their behavior is different:
- Lines are parsed one line at a time. The limits and handling strategy are applied per line. Line length limits include
the SSE field names (`id: `, `data: `, `event: `, etc.).
- Events are built from successive `data:` lines. As each line is added to the built event, the event size limit is used
to short-circuit processing of the current and/or subsequent lines. This limit is generally meant to help prevent
runaway memory usage causing an application crash from a single (possible erroneous) message from the server.

Since line and event strategies can be configured independently, you can have different behaviors for each level. For example:
```hocon
pekko.http.sse {
  oversized-line-handling = "truncate"    # Truncate oversized lines
  oversized-event-handling = "log-and-skip"  # Skip oversized events entirely
}
```

**Example 1: Oversized Line in Multi-line Event**

Consider this SSE event with `max-line-size = 50`:

```
data: This is a normal line
data: This line is much too long and exceeds the configured max-line-size limit by a lot
data: Another normal line

```

With **log-and-skip** strategy:
- The oversized line gets skipped
- The resulting event contains only:
```
data: This is a normal line
data: Another normal line
```

With **truncate** strategy:
- The oversized line gets truncated to 50 characters
- The resulting event contains:
```
data: This is a normal line
data: This line is much too long and exceeds the c
data: Another normal line
```

With **dead-letter** strategy:
- The oversized line is sent to the dead letter queue as `OversizedSseLine(line: String)`
- The resulting event contains only:
```
data: This is a normal line
data: Another normal line
```

**Example 2: Event Exceeds max-event-size**

If the complete event (after line processing) exceeds `max-event-size`, the strategy applies to the entire event:

```
data: Line 1
data: Line 2
data: Line 3
[... many more lines causing total event size > max-event-size]
```

With **log-and-skip**: The entire event is logged and skipped
With **truncate**: The event's data field is truncated to fit within the limit
With **dead-letter**: The oversized event is sent to dead letters as `OversizedSseEvent(event: ServerSentEvent)` The
`OversizedSseEvent` sent to DeadLetters will be truncated to the `max-event-size`.

**Example 3: Event Size Exceeded During Line Parsing (Memory Protection)**

If while parsing individual lines, the accumulated event size would exceed `max-event-size`, the parser stops processing 
additional lines for that event to prevent memory exhaustion:

```
data: Line 1 (fits)
data: Line 2 (fits)
data: Line 3 (would exceed max-event-size)
data: Line 4 (fits line limit)
```

With **log-and-skip**:
- Line 3 is logged and skipped. Line 4 is processed
- The event containing all of lines 1, 2, and 4 is emitted to the stream.

With **truncate**:
- Line 3 is truncated as needed and line 4 is processed
- The event containing all of lines 1 and 2, truncated line 3, and all of line 4 is emitted to the stream

With **dead-letter**:
- The partial event (Lines 1 and 2) is sent to dead letters as `OversizedSseEvent(partialEvent: ServerSentEvent)`
- Lines 3 and 4 are skipped
- Parser resets and continues with the next event

This behavior prevents the application from running out of memory when processing very large events, as only the partial 
event up to the size limit is handled.

#### Processing Order

1. **First**: Individual lines are processed against `max-line-size`
2. **Then**: Each line is added to the event builder, checking against `max-event-size`
3. **Finally**: Complete events are validated against `max-event-size`

This means an event can have individual lines handled by the line-level strategy, then be subject to partial event 
handling during building, and finally be subject to complete event-level handling.

For applications that need to handle very large messages (like blockchain data or detailed JSON payloads), consider
setting `max-line-size = 0` and `max-event-size = 0` to disable limits entirely, or use one of the non-failing handling modes.

Notice that if you are looking for a resilient way to permanently subscribe to an event stream,
Apache Pekko Connectors provides the [EventSource](https://pekko.apache.org/docs/pekko-connectors/current/sse.html) 
connector which reconnects automatically with the id of the last seen event.
