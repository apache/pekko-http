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

import java.util.List;
import java.util.Optional;
import org.apache.pekko.http.javadsl.model.headers.EntityTag;
import org.apache.pekko.http.javadsl.model.headers.EntityTagRange;
import org.apache.pekko.http.javadsl.model.headers.IfModifiedSince;
import org.apache.pekko.http.javadsl.model.headers.IfNoneMatch;
import org.junit.jupiter.api.Test;

public class SelectedRepresentationConditionsTest {
  @Test
  void usesTheSelectedRepresentationValidator() {
    EntityTag coded = EntityTag.create("coded-r1", true);
    EntityTag identity = EntityTag.create("identity-r1", false);
    List<IfNoneMatch> condition =
        List.of(IfNoneMatch.create(EntityTagRange.create(EntityTag.create("coded-r1", false))));

    assertEquals(
        SelectedRepresentationDecision.NOT_MODIFIED,
        SelectedRepresentationConditions.evaluate(
            HttpMethods.GET, condition, Optional.of(coded), Optional.empty()));
    assertEquals(
        SelectedRepresentationDecision.PROCEED,
        SelectedRepresentationConditions.evaluate(
            HttpMethods.GET, condition, Optional.of(identity), Optional.empty(), false));
  }

  @Test
  void considersTheSelectedDateOnlyWhenIfNoneMatchIsAbsent() {
    DateTime modified = DateTime.create(2025, 1, 10, 12, 0, 0);
    IfModifiedSince since = IfModifiedSince.create(modified.plus(1000));
    IfNoneMatch otherVariant =
        IfNoneMatch.create(EntityTagRange.create(EntityTag.create("identity-r1", false)));
    EntityTag selected = EntityTag.create("coded-r1", false);

    assertEquals(
        SelectedRepresentationDecision.NOT_MODIFIED,
        SelectedRepresentationConditions.evaluate(
            HttpMethods.GET, List.of(since), Optional.of(selected), Optional.of(modified)));
    assertEquals(
        SelectedRepresentationDecision.PROCEED,
        SelectedRepresentationConditions.evaluate(
            HttpMethods.GET,
            List.of(since, otherVariant),
            Optional.of(selected),
            Optional.of(modified)));
  }
}
