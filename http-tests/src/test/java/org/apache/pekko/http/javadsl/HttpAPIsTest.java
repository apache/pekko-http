/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.http.javadsl;

import org.apache.pekko.event.LoggingAdapter;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.settings.ClientConnectionSettings;
import org.apache.pekko.http.javadsl.testkit.JUnitRouteTest;
import org.apache.pekko.http.scaladsl.settings.ConnectionPoolSettings;
import org.apache.pekko.japi.function.Function;
import org.apache.pekko.stream.javadsl.Flow;
import com.typesafe.config.ConfigFactory;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import java.util.concurrent.CompletionStage;

import static org.apache.pekko.http.javadsl.ConnectHttp.toHost;
import static org.apache.pekko.http.javadsl.ConnectHttp.toHostHttps;

@SuppressWarnings("ConstantConditions")
public class HttpAPIsTest extends JUnitRouteTest {

  @Test
  public void placeholderCompileTimeOnlyTest() {
    // fails if there are no test cases
  }

  @SuppressWarnings("unused")
  public void compileOnly() throws Exception {
    final Http http = Http.get(system());

    final HttpConnectionContext httpContext = ConnectionContext.noEncryption();
    final HttpsConnectionContext httpsContext =
        ConnectionContext.httpsClient(SSLContext.getDefault());

    String host = "";
    int port = 9090;
    ConnectionPoolSettings conSettings = null;
    LoggingAdapter log = null;

    http.newServerAt("127.0.0.1", 8080).connectionSource();
    http.newServerAt("127.0.0.1", 8080).enableHttps(httpsContext).connectionSource();

    final Flow<HttpRequest, HttpResponse, ?> handler = null;
    http.newServerAt("127.0.0.1", 8080).bindFlow(handler);
    http.newServerAt("127.0.0.1", 8080).enableHttps(httpsContext).bindFlow(handler);

    final Function<HttpRequest, CompletionStage<HttpResponse>> handler1 = null;
    http.newServerAt("127.0.0.1", 8080).bind(handler1);
    http.newServerAt("127.0.0.1", 8080).enableHttps(httpsContext).bind(handler1);

    final Function<HttpRequest, HttpResponse> handler2 = null;
    http.newServerAt("127.0.0.1", 8080).bindSync(handler2);
    http.newServerAt("127.0.0.1", 8080).enableHttps(httpsContext).bindSync(handler2);

    final HttpRequest handler3 = null;
    http.singleRequest(handler3);
    http.singleRequest(handler3, httpsContext);
    http.singleRequest(handler3, httpsContext, conSettings, log);

    http.outgoingConnection("pekko.apache.org");
    http.outgoingConnection("pekko.apache.org:8080");
    http.outgoingConnection("https://pekko.apache.org");
    http.outgoingConnection("https://pekko.apache.org:8081");

    http.outgoingConnection(toHost("pekko.apache.org"));
    http.outgoingConnection(toHost("pekko.apache.org", 8080));
    http.outgoingConnection(toHost("https://pekko.apache.org"));
    http.outgoingConnection(toHostHttps("pekko.apache.org")); // default ssl context (ssl-config)
    http.outgoingConnection(
        toHostHttps("ssh://pekko.apache.org")); // throws, we explicitly require https or ""
    http.outgoingConnection(
        toHostHttps("pekko.apache.org", 8081).withCustomHttpsContext(httpsContext));
    http.outgoingConnection(
        toHostHttps("pekko.apache.org", 8081)
            .withCustomHttpsContext(httpsContext)
            .withDefaultHttpsContext());
    http.outgoingConnection(
        toHostHttps("pekko.apache.org", 8081)
            .withCustomHttpsContext(httpsContext)
            .withDefaultHttpsContext());

    http.connectionTo("pekko.apache.org").http();
    http.connectionTo("pekko.apache.org").https();
    http.connectionTo("pekko.apache.org").http2();
    http.connectionTo("pekko.apache.org").http2WithPriorKnowledge();
    http.connectionTo("pekko.apache.org")
        .toPort(8081)
        .withCustomHttpsConnectionContext(httpsContext)
        .withClientConnectionSettings(ClientConnectionSettings.create(ConfigFactory.empty()))
        .logTo(system().log())
        .https();

    // in future we can add modify(context -> Context) to "keep ssl-config defaults, but tweak them
    // in code)

    http.newHostConnectionPool("pekko.apache.org", materializer());
    http.newHostConnectionPool("https://pekko.apache.org", materializer());
    http.newHostConnectionPool("https://pekko.apache.org:8080", materializer());
    http.newHostConnectionPool(toHost("pekko.apache.org"), materializer());
    http.newHostConnectionPool(
        toHostHttps("ftp://pekko.apache.org"),
        materializer()); // throws, we explicitly require https or ""
    http.newHostConnectionPool(toHostHttps("https://pekko.apache.org:2222"), materializer());
    http.newHostConnectionPool(toHostHttps("pekko.apache.org"), materializer());
    http.newHostConnectionPool(toHost(""), conSettings, log, materializer());

    http.cachedHostConnectionPool("pekko.apache.org");
    http.cachedHostConnectionPool("https://pekko.apache.org");
    http.cachedHostConnectionPool("https://pekko.apache.org:8080");
    http.cachedHostConnectionPool(toHost("pekko.apache.org"));
    http.cachedHostConnectionPool(
        toHostHttps("smtp://pekko.apache.org")); // throws, we explicitly require https or ""
    http.cachedHostConnectionPool(toHostHttps("https://pekko.apache.org:2222"));
    http.cachedHostConnectionPool(toHostHttps("pekko.apache.org"));
    http.cachedHostConnectionPool(toHost("pekko.apache.org"), conSettings, log);

    http.superPool();
    http.superPool(conSettings, log);
    http.superPool(conSettings, httpsContext, log);

    final ConnectWithHttps connect =
        toHostHttps("pekko.apache.org", 8081)
            .withCustomHttpsContext(httpsContext)
            .withDefaultHttpsContext();
    connect.effectiveHttpsConnectionContext(
        http.defaultClientHttpsContext()); // usage by us internally
  }
}
