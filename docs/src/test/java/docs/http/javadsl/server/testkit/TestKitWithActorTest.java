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

package docs.http.javadsl.server.testkit;

// #testkit-actor-integration
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.Adapter;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.testkit.JUnitRouteTest;

import org.apache.pekko.http.javadsl.testkit.TestRoute;
import org.apache.pekko.http.javadsl.testkit.TestRouteResult;
import org.junit.Test;

public class TestKitWithActorTest extends JUnitRouteTest {

  @Test
  public void returnPongForGetPing() {
    // This test does not use the classic APIs,
    // so it needs to adapt the system:
    ActorSystem<Void> system = Adapter.toTyped(system());

    TestProbe<MyAppWithActor.Ping> probe = TestProbe.create(system);
    TestRoute testRoute =
        testRoute(new MyAppWithActor().createRoute(probe.getRef(), system.scheduler()));

    TestRouteResult result = testRoute.run(HttpRequest.GET("/ping"));
    MyAppWithActor.Ping ping = probe.expectMessageClass(MyAppWithActor.Ping.class);
    ping.replyTo.tell("PONG!");
    result.assertEntity("PONG!");
  }
}
// #testkit-actor-integration
