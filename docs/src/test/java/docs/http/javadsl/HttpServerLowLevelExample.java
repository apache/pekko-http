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

package docs.http.javadsl;

// #low-level-server-example
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.http.javadsl.model.ContentTypes;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.stream.SystemMaterializer;
import org.apache.pekko.util.ByteString;

import java.util.concurrent.CompletionStage;

public class HttpServerLowLevelExample {

  public static void main(String[] args) throws Exception {
    ActorSystem<Void> system = ActorSystem.create(Behaviors.empty(), "lowlevel");

    try {
      CompletionStage<ServerBinding> serverBindingFuture =
          Http.get(system)
              .newServerAt("localhost", 8080)
              .bindSync(
                  request -> {
                    if (request.getUri().path().equals("/"))
                      return HttpResponse.create()
                          .withEntity(
                              ContentTypes.TEXT_HTML_UTF8,
                              ByteString.fromString("<html><body>Hello world!</body></html>"));
                    else if (request.getUri().path().equals("/ping"))
                      return HttpResponse.create().withEntity(ByteString.fromString("PONG!"));
                    else if (request.getUri().path().equals("/crash"))
                      throw new RuntimeException("BOOM!");
                    else {
                      request.discardEntityBytes(system);
                      return HttpResponse.create()
                          .withStatus(StatusCodes.NOT_FOUND)
                          .withEntity("Unknown resource!");
                    }
                  });

      System.out.println("Server online at http://localhost:8080/\nPress RETURN to stop...");
      System.in.read(); // let it run until user presses return

      serverBindingFuture
          .thenCompose(ServerBinding::unbind) // trigger unbinding from the port
          .thenAccept(unbound -> system.terminate()); // and shutdown when done

    } catch (RuntimeException e) {
      system.terminate();
    }
  }
}
// #low-level-server-example
