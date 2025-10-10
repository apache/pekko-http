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

package docs.http.javadsl.server.directives;

// #custom-content-type
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.common.EntityStreamingSupport;
import org.apache.pekko.http.javadsl.marshalling.Marshaller;
import org.apache.pekko.http.javadsl.model.*;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.stream.javadsl.Source;

import java.util.Random;
import java.util.stream.Stream;

public class JsonStreamingFullExample extends AllDirectives {

  public Route createRoute() {
    final MediaType.WithFixedCharset mediaType =
        MediaTypes.applicationWithFixedCharset("vnd.example.api.v1+json", HttpCharsets.UTF_8);

    final ContentType.WithFixedCharset contentType = ContentTypes.create(mediaType);

    final Marshaller<User, RequestEntity> userMarshaller =
        Marshaller.withFixedContentType(
            contentType, (User user) -> HttpEntities.create(contentType, user.toJson()));

    final EntityStreamingSupport jsonStreamingSupport =
        EntityStreamingSupport.json()
            .withContentType(contentType)
            .withParallelMarshalling(10, false);

    return get(
        () ->
            pathPrefix(
                "users",
                () -> completeOKWithSource(fetchUsers(), userMarshaller, jsonStreamingSupport)));
  }

  private Source<User, NotUsed> fetchUsers() {
    final Random rnd = new Random();
    return Source.fromIterator(
        () -> Stream.generate(rnd::nextInt).map(this::dummyUser).limit(10000).iterator());
  }

  private User dummyUser(int id) {
    return new User(id, "User " + id);
  }

  static final class User {
    int id;
    String name;

    User(int id, String name) {
      this.id = id;
      this.name = name;
    }

    String toJson() {
      return "{\"id\":\"" + id + "\", \"name\":\"" + name + "\"}";
    }
  }

  public static void main(String[] args) {
    ActorSystem system = ActorSystem.create();
    final JsonStreamingFullExample app = new JsonStreamingFullExample();
    final Http http = Http.get(system);

    http.newServerAt("localhost", 8080).bind(app.createRoute());
  }
}
// #custom-content-type
