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

package org.apache.pekko.http.shaded.com.twitter.hpack

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class DynamicTableSpec extends AnyWordSpec with Matchers {
  "DynamicTable" should {
    "retain minimum-sized entries when exactly filled to capacity" in {
      Seq(32, 64, 4096).foreach { capacity =>
        val table = new DynamicTable(capacity)
        val entries = Vector.fill(capacity / 32)(new HeaderField("", ""))
        entries.foreach(table.add)

        table.size() shouldEqual capacity
        table.length() shouldEqual entries.size
        entries.reverse.zipWithIndex.foreach {
          case (entry, index) => (table.getEntry(index + 1) should be).theSameInstanceAs(entry)
        }
        an[IndexOutOfBoundsException] should be thrownBy table.getEntry(entries.size + 1)
      }
    }

    "preserve entry order when a full table wraps and grows" in {
      val table = new DynamicTable(64)
      val first = new HeaderField("", "")
      val second = new HeaderField("", "")
      val third = new HeaderField("", "")
      table.add(first)
      table.add(second)
      table.add(third)
      table.length() shouldEqual 2
      (table.getEntry(1) should be).theSameInstanceAs(third)
      (table.getEntry(2) should be).theSameInstanceAs(second)

      table.setCapacity(96)
      table.length() shouldEqual 2
      (table.getEntry(1) should be).theSameInstanceAs(third)
      (table.getEntry(2) should be).theSameInstanceAs(second)
      (table.remove() should be).theSameInstanceAs(second)
      (table.remove() should be).theSameInstanceAs(third)
      table.length() shouldEqual 0
      table.size() shouldEqual 0
    }

    "evict entries when shrinking and support zero capacity" in {
      val table = new DynamicTable(64)
      val older = new HeaderField("", "")
      val newer = new HeaderField("", "")
      table.add(older)
      table.add(newer)
      table.setCapacity(32)
      table.length() shouldEqual 1
      (table.getEntry(1) should be).theSameInstanceAs(newer)
      table.setCapacity(0)
      table.remove() shouldEqual null
      table.add(new HeaderField("", ""))
      table.length() shouldEqual 0
      table.size() shouldEqual 0
      table.setCapacity(32)
      table.add(newer)
      (table.getEntry(1) should be).theSameInstanceAs(newer)
      table.clear()
      table.length() shouldEqual 0
      table.size() shouldEqual 0
    }
  }
}
