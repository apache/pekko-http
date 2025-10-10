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

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.io.IOUtils

import java.io.{ BufferedInputStream, File, FileInputStream, FileOutputStream }
import scala.util.Using

object Untar {

  // extracts files from `.tar.gz` / `.tgz` files
  def unTarGz(tarFile: File, toDirectory: File): Unit = {
    Using(new FileInputStream(tarFile)) { tarFileStream =>
      val buffIn = new BufferedInputStream(tarFileStream)
      val gzIn = new GzipCompressorInputStream(buffIn)
      Using(new TarArchiveInputStream(gzIn)) { tis =>
        toDirectory.mkdirs()
        var entry = tis.getNextEntry
        while (entry ne null) {
          val name = entry.getName
          val outFile = new File(toDirectory, name)
          Using(new FileOutputStream(outFile)) { fos =>
            IOUtils.copy(tis, fos)
          }
          entry = tis.getNextEntry
        }
      }
    }
  }
}
