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

package org.apache.pekko.http.javadsl.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class MultipartFilenameTest {

  private static Multipart.FormData.BodyPart.Strict part(String filename) {
    return Multiparts.createFormDataBodyPartStrict(
        "upload", HttpEntities.create("body"), Collections.singletonMap("filename", filename));
  }

  @Test
  public void testPercentDecodedFilename() {
    Multipart.FormData.BodyPart part = part("caf%C3%A9.txt");
    assertEquals(Optional.of("caf%C3%A9.txt"), part.getFilename());
    assertEquals(Optional.of("café.txt"), part.getPercentDecodedFilename());
  }

  @Test
  public void testPercentDecodedFilenameKeepsInvalidEncoding() {
    assertEquals(Optional.of("100%.txt"), part("100%.txt").getPercentDecodedFilename());
  }

  @Test
  public void testPercentDecodedFilenameWithoutFilename() {
    Multipart.FormData.BodyPart part =
        Multiparts.createFormDataBodyPartStrict("field", HttpEntities.create("body"));
    assertEquals(Optional.empty(), part.getPercentDecodedFilename());
  }
}
