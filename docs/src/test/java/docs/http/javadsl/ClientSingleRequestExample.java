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

// #unmarshall-response-body
import org.apache.pekko.http.javadsl.marshallers.jackson.Jackson;

// #unmarshall-response-body

// #single-request-example
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.model.*;
import org.apache.pekko.http.javadsl.server.examples.petstore.Pet;
import org.apache.pekko.stream.SystemMaterializer;

import java.util.concurrent.CompletionStage;

public class ClientSingleRequestExample {

  public static void main(String[] args) {
    final ActorSystem<Void> system = ActorSystem.create(Behaviors.empty(), "SingleRequest");

    final CompletionStage<HttpResponse> responseFuture =
        Http.get(system).singleRequest(HttpRequest.create("https://pekko.apache.org"));
  }
}
// #single-request-example

class OtherRequestResponseExamples {
  public void request() {
    // #create-simple-request
    HttpRequest.create("https://pekko.apache.org");

    // with query params
    HttpRequest.create("https://pekko.apache.org?foo=bar");
    // #create-simple-request
    // #create-post-request
    HttpRequest.POST("https://userservice.example/users")
        .withEntity(HttpEntities.create(ContentTypes.TEXT_PLAIN_UTF8, "data"));
    // #create-post-request

    // TODO should we have an API to create an Entity via a Marshaller?
  }

  public void response() {
    ActorSystem<Void> system = null;
    HttpResponse response = null;
    // #unmarshal-response-body
    CompletionStage<Pet> pet = Jackson.unmarshaller(Pet.class).unmarshal(response.entity(), system);
    // #unmarshal-response-body
  }
}
