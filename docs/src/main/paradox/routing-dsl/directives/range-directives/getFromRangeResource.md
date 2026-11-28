<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# getFromRangeResource

Use `getFromRangeResource` to serve a known-length resource that can be read by offset, such as a versioned object
in remote storage. Provide the selected representation's content type, length, and an asynchronous factory for a
`RangeResource.Reader`. Pekko HTTP chooses and coalesces the byte ranges, writes the response headers and
multipart boundaries, and requests only the selected intervals. For an ordinary local file, use
@ref[getFromFile](../file-and-resource-directives/getFromFile.md) instead; its file handling remains separate.

Pekko HTTP opens at most one reader per demanded body materialization and consumes one interval source at a time.
A read must produce exactly the requested number of bytes from the same representation version as every other read
in the response. Return promptly from the factory, `read`, and `close` callbacks: put the work behind an asynchronous
or suitably dispatched source. If the body is cancelled, Pekko cancels the current source and calls `close()` on the
reader, including a reader that arrives after the body stage has already stopped. The provider remains responsible
for resources it has not yet handed over and for I/O outside the source.

## Serve a resource in a route

The examples use an immutable in-memory snapshot to demonstrate the reader contract. Replace the slice operation
with your storage client's bounded read, using the supplied offset and length directly.

@@@ div { .group-scala }

```scala
import scala.concurrent.Future

import org.apache.pekko.http.scaladsl.model.ContentTypes
import org.apache.pekko.http.scaladsl.server.Directives.getFromRangeResource
import org.apache.pekko.http.scaladsl.server.{ RangeResource, Route }
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

def download(snapshot: ByteString): Route = {
  val resource = RangeResource(ContentTypes.`text/plain(UTF-8)`, snapshot.size) { () =>
    Future.successful(new RangeResource.Reader {
      override def read(offset: Long, length: Long): Source[ByteString, Any] =
        Source.single(snapshot.slice(offset.toInt, (offset + length).toInt))
    })
  }
  getFromRangeResource(resource)
}
```

@@@

@@@ div { .group-java }

```java
import java.util.concurrent.CompletableFuture;

import org.apache.pekko.http.javadsl.model.ContentTypes;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.RangeResource;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.util.ByteString;

public class Downloads extends AllDirectives {
  public Route download(ByteString snapshot) {
    RangeResource resource =
        RangeResource.create(ContentTypes.TEXT_PLAIN_UTF8, snapshot.size(), () -> {
          RangeResource.Reader reader = (offset, length) -> Source.single(
              snapshot.slice(Math.toIntExact(offset), Math.toIntExact(offset + length)));
          return CompletableFuture.completedFuture(reader);
        });
    return getFromRangeResource(resource);
  }
}
```

@@@

The route uses `pekko.http.routing` range limits, coalescing, and the `preserve-range-request-order` option.
By default, surviving multipart ranges are sent in ascending byte-offset order. The route accepts `GET` and `HEAD`
and uses the standard conditional directive for supplied validators. Keep
`pekko.http.server.transparent-head-requests` off (the default) to prevent a server from presenting `HEAD` to the
route as `GET`. This is a positional stream, not a transport zero-copy API.

## Use from a different routing framework

An adapter can call `resource.responseFor(originalMethod, requestHeaders)` with modeled Pekko HTTP values.
It does not need a Pekko HTTP `Route`, `RequestContext`, or routing settings. Use the optional
`RangeResponsePolicy` overload when the adapter needs explicit limits, coalescing, or multipart order.

1. Select the representation and evaluate request preconditions first. If they determine another response
   (for example, `304 Not Modified`), return that response without asking the provider to read.
2. Pass the original method, a modeled `Range` header if present, and any `If-Range` that has not been
   satisfied against the selected representation. A remaining `If-Range` suppresses the range. `responseFor`
   returns no result for methods other than `GET` and `HEAD`.
3. Convert the returned response's status, headers, and entity into the framework's response. For actual
   `HEAD`, use the selected entity's metadata, including its full length, but do not materialize its body.
   For a Play adapter, known-length entities can map to `HttpEntity.Streamed`, and multipart entities
   of unknown length to `HttpEntity.Chunked`; forward the returned `Content-Range` when present, keep the
   chosen media type (including parameters), and propagate streaming cancellation.

See also [HTTP range requests in RFC 9110](https://www.rfc-editor.org/rfc/rfc9110.html#section-14).
