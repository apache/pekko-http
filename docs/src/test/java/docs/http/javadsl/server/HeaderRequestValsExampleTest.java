/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.http.javadsl.server;

import org.junit.Test;

import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.headers.Host;
import org.apache.pekko.http.javadsl.model.headers.RawHeader;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.testkit.JUnitRouteTest;

//#by-class
import static org.apache.pekko.http.javadsl.server.Directives.extractHost;
import static org.apache.pekko.http.javadsl.server.Directives.complete;

//#by-class
//#by-name
import static org.apache.pekko.http.javadsl.server.Directives.headerValueByName;
import static org.apache.pekko.http.javadsl.server.Directives.complete;

//#by-name

public class HeaderRequestValsExampleTest extends JUnitRouteTest {

  @Test
  public void testHeaderVals() {
    //#by-class

    final Route route =
      extractHost(host ->
        complete(String.format("Host header was: %s", host))
      );

    // tests:
    final HttpRequest request =
      HttpRequest
        .GET("http://akka.io/")
      .addHeader(Host.create("akka.io"));
    testRoute(route).run(request).assertEntity("Host header was: akka.io");

    //#by-class
  }

  @Test
  public void testHeaderByName() {
    //#by-name

    final Route route =
      // extract the `value` of the header:
      headerValueByName("X-Fish-Name", xFishName ->
        complete(String.format("The `X-Fish-Name` header's value was: %s", xFishName))
      );

    // tests:
    final HttpRequest request =
      HttpRequest
        .GET("/")
        .addHeader(RawHeader.create("X-Fish-Name", "Blippy"));
    testRoute(route).run(request).assertEntity("The `X-Fish-Name` header's value was: Blippy");

    //#by-name
  }
}