/*
 * Copyright 2015 Heiko Seeberger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.http.javadsl.marshalling.sse;

import org.apache.pekko.http.javadsl.model.sse.ServerSentEvent;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.testkit.JUnitRouteTest;
import org.apache.pekko.http.javadsl.testkit.TestRouteResult;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.util.ByteString;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.apache.pekko.http.javadsl.model.HttpRequest.GET;
import static org.apache.pekko.http.javadsl.model.MediaTypes.TEXT_EVENT_STREAM;
import static org.apache.pekko.http.javadsl.server.Directives.completeOK;
import static org.apache.pekko.util.ByteString.emptyByteString;

public class EventStreamMarshallingTest extends JUnitRouteTest {

  @Test
  public void testToEventStream() {
    // #event-stream-marshalling-example
    final List<ServerSentEvent> events = new ArrayList<>();
    events.add(ServerSentEvent.create("1"));
    events.add(ServerSentEvent.create("2"));
    final Route route = completeOK(Source.from(events), EventStreamMarshalling.toEventStream());
    // #event-stream-marshalling-example

    final ByteString expectedEntity =
        events.stream()
            .map(e -> ((org.apache.pekko.http.scaladsl.model.sse.ServerSentEvent) e).encode())
            .reduce(emptyByteString(), ByteString::concat);
    final TestRouteResult routeResult = testRoute(route).run(GET("/"));
    routeResult.assertMediaType(TEXT_EVENT_STREAM);
    routeResult.assertEquals(
        expectedEntity, routeResult.entityBytes(), "Entity should carry events!");
  }
}
