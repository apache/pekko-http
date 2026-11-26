/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.http.javadsl.coding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.typesafe.config.ConfigFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.model.HttpEntities;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.model.headers.HttpEncodings;
import org.apache.pekko.stream.SystemMaterializer;
import org.junit.jupiter.api.Test;

public class GzipWithoutOptionalZstdTest {

  @Test
  void worksWithoutOptionalZstd() throws Exception {
    var isolated = new WithoutZstdClassLoader(getClass().getClassLoader());
    assertThrows(
        ClassNotFoundException.class,
        () -> Class.forName("com.github.luben.zstd.Zstd", false, isolated));

    var thread = Thread.currentThread();
    var previous = thread.getContextClassLoader();
    try {
      thread.setContextClassLoader(isolated);
      var consumer = Class.forName(Consumer.class.getName(), true, isolated);
      assertSame(isolated, consumer.getClassLoader());
      consumer.getMethod("run").invoke(null);
    } catch (InvocationTargetException failure) {
      throw new AssertionError("Isolated Java Gzip consumer failed", failure.getCause());
    } finally {
      thread.setContextClassLoader(previous);
    }
  }

  private static final class WithoutZstdClassLoader extends ClassLoader {
    WithoutZstdClassLoader(ClassLoader parent) {
      super(parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      if (name.startsWith("com.github.luben.")) {
        throw new ClassNotFoundException(name);
      }
      // Pekko Stream supplies the optional adapter. Defining only Pekko HTTP here would let
      // Stream see Zstd through the parent's classes and make the regression test ineffective.
      if (!name.startsWith("org.apache.pekko.")) {
        return super.loadClass(name, resolve);
      }
      synchronized (getClassLoadingLock(name)) {
        var loaded = findLoadedClass(name);
        if (loaded == null) {
          // Read bytes using the runner's loader without reconstructing its classpath.
          String resource = name.replace('.', '/') + ".class";
          try (var source = getParent().getResourceAsStream(resource)) {
            if (source == null) throw new ClassNotFoundException(name);
            byte[] bytes = source.readAllBytes();
            loaded = defineClass(name, bytes, 0, bytes.length);
          } catch (IOException cause) {
            throw new ClassNotFoundException(name, cause);
          }
        }
        if (resolve) resolveClass(loaded);
        return loaded;
      }
    }
  }

  public static final class Consumer {
    public static void run() throws Exception {
      var isolated = Consumer.class.getClassLoader();
      assertSame(isolated, Coder.class.getClassLoader(), "Java coder uses isolated classes");
      assertSame(
          isolated,
          Class.forName("org.apache.pekko.stream.scaladsl.Compression$", false, isolated)
              .getClassLoader(),
          "stream compression uses isolated classes");
      assertSame(
          isolated, ActorSystem.class.getClassLoader(), "actor system uses isolated classes");

      ActorSystem system =
          ActorSystem.create("gzip-without-optional-zstd", ConfigFactory.load(isolated), isolated);
      try {
        var materializer = SystemMaterializer.get(system).materializer();
        String body = "a small ordinary gzip response";
        var response = HttpResponse.create().withEntity(HttpEntities.create(body));
        var encoded = Coder.Gzip.encodeMessage(response);
        assertEquals(HttpEncodings.GZIP, encoded.encoding(), "selected content encoding");

        var encodedBody =
            encoded
                .entity()
                .toStrict(3000, materializer)
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
        try (var gzip =
            new GZIPInputStream(new ByteArrayInputStream(encodedBody.getData().toArray()))) {
          assertEquals(
              body,
              new String(gzip.readAllBytes(), StandardCharsets.UTF_8),
              "independently decoded gzip bytes");
        }

        var decoded = Coder.decodeResponse(encoded.withEntity(encodedBody));
        assertFalse(
            decoded.getHeader("Content-Encoding").isPresent(),
            "the consumed coding header remained on the response");
        var decodedBody =
            decoded
                .entity()
                .toStrict(3000, materializer)
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
        assertEquals(body, decodedBody.getData().utf8String(), "Java default decoder result");
      } finally {
        system.terminate();
        system.getWhenTerminated().toCompletableFuture().get(5, TimeUnit.SECONDS);
      }
    }
  }
}
