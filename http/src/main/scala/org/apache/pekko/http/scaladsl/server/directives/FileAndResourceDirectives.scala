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

package org.apache.pekko.http.scaladsl.server
package directives

import java.io.{ File, FileNotFoundException, IOException, InputStream }
import java.net.{ JarURLConnection, URL, URLConnection }

import scala.annotation.tailrec
import scala.jdk.CollectionConverters._

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.event.LoggingAdapter
import pekko.http.impl.util._
import pekko.http.impl.util.JavaMapping.Implicits._
import pekko.http.javadsl
import pekko.http.javadsl.{ marshalling, model }
import pekko.http.javadsl.server.RoutingJavaMapping
import pekko.http.scaladsl.marshalling.{ Marshaller, ToEntityMarshaller }
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.model.headers._
import pekko.stream.scaladsl.{ FileIO, StreamConverters }

/**
 * @groupname fileandresource File and resource directives
 * @groupprio fileandresource 70
 */
trait FileAndResourceDirectives {
  import BasicDirectives._
  import CacheConditionDirectives._
  import FileAndResourceDirectives._
  import MethodDirectives._
  import RouteConcatenation._
  import RouteDirectives._

  /**
   * Completes GET requests with the content of the given file.
   * If the file cannot be found or read the request is rejected.
   *
   * @group fileandresource
   */
  def getFromFile(fileName: String)(implicit resolver: ContentTypeResolver): Route =
    getFromFile(new File(fileName))

  /**
   * Completes GET requests with the content of the given file.
   * If the file cannot be found or read the request is rejected.
   *
   * @group fileandresource
   */
  def getFromFile(file: File)(implicit resolver: ContentTypeResolver): Route =
    getFromFile(file, resolver(file.getName))

  /**
   * Completes GET requests with the content of the given file.
   * If the file cannot be found or read the request is rejected.
   *
   * @group fileandresource
   */
  def getFromFile(file: File, contentType: ContentType): Route =
    get {
      if (file.isFile && file.canRead)
        conditionalFor(file.length, file.lastModified) {
          if (file.length > 0) {
            withRangeSupportAndPrecompressedMediaTypeSupport {
              complete(HttpEntity.Default(contentType, file.length, FileIO.fromPath(file.toPath)))
            }
          } else complete(HttpEntity.Empty)
        }
      else reject
    }

  private def conditionalFor(length: Long, lastModified: Long): Directive0 =
    extractSettings.flatMap(settings =>
      if (settings.fileGetConditional) {
        val tag = java.lang.Long.toHexString(lastModified ^ java.lang.Long.reverse(length))
        val lastModifiedDateTime = DateTime(math.min(lastModified, System.currentTimeMillis))
        conditional(EntityTag(tag), lastModifiedDateTime)
      } else pass)

  /**
   * Completes GET requests with the content of the given class-path resource.
   * If the resource cannot be found or read the Route rejects the request.
   *
   * @group fileandresource
   */
  def getFromResource(resourceName: String)(implicit resolver: ContentTypeResolver): Route =
    getFromResource(resourceName, resolver(resourceName))

  /**
   * Completes GET requests with the content of the given resource.
   * If the resource is a directory or cannot be found or read the Route rejects the request.
   *
   * @group fileandresource
   */
  def getFromResource(
      resourceName: String, contentType: ContentType, classLoader: ClassLoader = _defaultClassLoader): Route =
    if (!resourceName.endsWith('/'))
      get {
        extractSettings { settings =>
          val useJarFileCache = settings.useJarFileCache
          Option(classLoader.getResource(resourceName)).flatMap(ResourceFile(_, useJarFileCache)) match {
            case Some(ResourceFile(url, length, lastModified)) =>
              conditionalFor(length, lastModified) {
                if (length > 0) {
                  withRangeSupportAndPrecompressedMediaTypeSupport {
                    complete(HttpEntity.Default(contentType, length,
                      StreamConverters.fromInputStream(() => openStream(url, useJarFileCache))))
                  }
                } else complete(HttpEntity.Empty)
              }
            case _ => reject // not found or directory
          }
        }
      }
    else reject // don't serve the content of resource "directories"

  /**
   * Completes GET requests with the content of a file underneath the given directory.
   * If the file cannot be read the Route rejects the request.
   *
   * @group fileandresource
   */
  def getFromDirectory(directoryName: String)(implicit resolver: ContentTypeResolver): Route =
    extractUnmatchedPath { unmatchedPath =>
      extractLog { log =>
        safeDirectoryChildPath(withTrailingSlash(directoryName), unmatchedPath, log) match {
          case ""       => reject
          case fileName => getFromFile(fileName)
        }
      }
    }

  /**
   * Completes GET requests with a unified listing of the contents of all given directories.
   * The actual rendering of the directory contents is performed by the in-scope `Marshaller[DirectoryListing]`.
   *
   * @group fileandresource
   */
  def listDirectoryContents(directories: String*)(implicit renderer: DirectoryRenderer): Route =
    get {
      extractRequestContext { ctx =>
        extractMatchedPath { matched =>
          val prefixPath = matched.toString
          val remainingPath = ctx.unmatchedPath
          val pathString = withTrailingSlash(safeJoinPaths("/", remainingPath, ctx.log, '/'))

          val dirs = directories.flatMap { dir =>
            safeDirectoryChildPath(withTrailingSlash(dir), remainingPath, ctx.log) match {
              case ""       => None
              case fileName =>
                val file = new File(fileName)
                if (file.isDirectory && file.canRead) Some(file) else None
            }
          }

          implicit val marshaller: ToEntityMarshaller[DirectoryListing] =
            renderer.marshaller(ctx.settings.renderVanityFooter)

          if (dirs.isEmpty) reject
          else
            complete(DirectoryListing(prefixPath + pathString, isRoot = pathString == "/", dirs.flatMap(_.listFiles)))
        }
      }
    }

  /**
   * Same as `getFromBrowseableDirectories` with only one directory.
   *
   * @group fileandresource
   */
  def getFromBrowseableDirectory(directory: String)(
      implicit renderer: DirectoryRenderer, resolver: ContentTypeResolver): Route =
    getFromBrowseableDirectories(directory)

  /**
   * Serves the content of the given directories as a file system browser, i.e. files are sent and directories
   * served as browseable listings.
   *
   * @group fileandresource
   */
  def getFromBrowseableDirectories(directories: String*)(implicit renderer: DirectoryRenderer,
      resolver: ContentTypeResolver): Route = {
    directories.map(getFromDirectory).reduceLeft(_ ~ _) ~ listDirectoryContents(directories: _*)
  }

  /**
   * Same as "getFromDirectory" except that the file is not fetched from the file system but rather from a
   * "resource directory".
   * If the requested resource is itself a directory or cannot be found or read the Route rejects the request.
   *
   * @group fileandresource
   */
  def getFromResourceDirectory(directoryName: String, classLoader: ClassLoader = _defaultClassLoader)(
      implicit resolver: ContentTypeResolver): Route = {
    val base = if (directoryName.isEmpty) "" else withTrailingSlash(directoryName)

    extractUnmatchedPath { path =>
      extractLog { log =>
        safeJoinPaths(base, path, log, separator = '/') match {
          case ""           => reject
          case resourceName => getFromResource(resourceName, resolver(resourceName), classLoader)
        }
      }
    }
  }

  protected[http] def _defaultClassLoader: ClassLoader = classOf[ActorSystem].getClassLoader
}

object FileAndResourceDirectives extends FileAndResourceDirectives {
  private val withRangeSupportAndPrecompressedMediaTypeSupport =
    RangeDirectives.withRangeSupport &
    CodingDirectives.withPrecompressedMediaTypeSupport

  private def withTrailingSlash(path: String): String = if (path.endsWith('/')) path else path + '/'

  /**
   * Given a base directory and a (Uri) path, returns a path to a location contained in the base directory,
   * while checking that no path traversal is possible. Path traversal is prevented by two individual measures:
   *  - A path segment must not be ".." and must not contain slashes or backslashes that may carry special meaning in
   *    file-system paths. This logic is intentionally a bit conservative as it might also prevent legitimate access
   *    to files containing one of those characters on a file-system that allows those characters in file names
   *    (e.g. backslash on posix).
   *  - Resulting paths are checked to be "contained" in the base directory. "Contained" means that the canonical location
   *    of the file (according to File.getCanonicalPath) has the canonical version of the basePath as a prefix. The exact
   *    semantics depend on the implementation of `File.getCanonicalPath` that may or may not resolve symbolic links and
   *    similar structures depending on the OS and the JDK implementation of file system accesses.
   */
  private def safeDirectoryChildPath(basePath: String, path: Uri.Path, log: LoggingAdapter,
      separator: Char = File.separatorChar): String =
    safeJoinPaths(basePath, path, log, separator) match {
      case ""   => ""
      case path => checkIsSafeDescendant(basePath, path, log)
    }

  private def safeJoinPaths(base: String, path: Uri.Path, log: LoggingAdapter, separator: Char): String = {
    import java.lang.StringBuilder
    @tailrec def rec(p: Uri.Path, result: StringBuilder = new StringBuilder(base)): String =
      p match {
        case Uri.Path.Empty               => result.toString
        case Uri.Path.Slash(tail)         => rec(tail, result.append(separator))
        case Uri.Path.Segment(head, tail) =>
          if (head.indexOf('/') >= 0 || head.indexOf('\\') >= 0 || head == "..") {
            log.warning("File-system path for base [{}] and Uri.Path [{}] contains suspicious path segment [{}], " +
              "GET access was disallowed", base, path, head)
            ""
          } else rec(tail, result.append(head))
      }
    rec(if (path.startsWithSlash) path.tail else path)
  }

  /**
   * Check against directory traversal attempts by making sure that the final is a "true child"
   * of the given base directory.
   *
   * Returns "" if the finalPath is suspicious and the canonical path otherwise.
   */
  private def checkIsSafeDescendant(basePath: String, finalPath: String, log: LoggingAdapter): String = {
    val baseFile = new File(basePath)
    val finalFile = new File(finalPath)
    val canonicalFinalPath = finalFile.getCanonicalPath

    if (!canonicalFinalPath.startsWith(baseFile.getCanonicalPath)) {
      log.warning("[{}] points to a location that is not part of [{}]. This might be a directory traversal attempt.",
        finalFile, baseFile)
      ""
    } else canonicalFinalPath
  }

  object ResourceFile {

    /**
     * Probes the resource without keeping any handle open: for a resource inside a jar file the jar is opened, read
     * and closed again, as this overload always did before the jar file cache became configurable. Use the
     * two-argument overload to let the JDK's jar file cache keep the jar open across requests.
     */
    def apply(url: URL): Option[ResourceFile] = apply(url, useJarFileCache = false)

    /**
     * @param useJarFileCache whether the JDK's jar file cache may be used for resources inside a jar file, see the
     *                        `pekko.http.routing.use-jar-file-cache` setting
     * @since 2.0.0
     */
    def apply(url: URL, useJarFileCache: Boolean): Option[ResourceFile] = url.getProtocol match {
      case "file" =>
        val file = new File(url.toURI)
        if (file.isDirectory) None
        else Some(ResourceFile(url, file.length(), file.lastModified()))
      case _ =>
        openConnection(url, useJarFileCache) match {
          case jarConnection: JarURLConnection =>
            // Ask the connection for the entry instead of opening the jar file here: opening it means reading and
            // parsing the whole central directory again for every single request. With the JDK's cache in use the
            // same open jar file is reused across requests, so nothing is opened here at all in the common case.
            // A connection that does not use the cache owns its jar file and has to close it again.
            val ownsJarFile = !jarConnection.getUseCaches
            try {
              val entry = Option(jarConnection.getJarEntry).filterNot(_.isDirectory)
              entry.map(e => ResourceFile(url, e.getSize, e.getTime))
            } catch {
              case _: FileNotFoundException => None // the entry disappeared from the jar in the meantime
            } finally if (ownsJarFile) {
                try jarConnection.getJarFile.close()
                catch { case _: IOException => } // the jar itself may be gone, then there is nothing to close
              }
          case connection => fromUrlConnection(url, connection)
        }
    }

    private def fromUrlConnection(url: URL, connection: URLConnection): Option[ResourceFile] =
      try {
        connection.setUseCaches(false) // otherwise the JDK will keep the connection open when we close!
        val len = connection.getContentLengthLong
        val lm = connection.getLastModified
        if (len < 0) None // an unknown length would be served as an empty response, so reject instead
        else Some(ResourceFile(url, len, lm))
      } catch {
        case _: FileNotFoundException => None // nothing behind the URL: reject instead of failing the request
      } finally {
        try connection.getInputStream.close()
        catch { case _: IOException => } // a stream that cannot be opened holds nothing to close
      }

    /**
     * The one place that decides how a connection relates to the JDK's caches: with the jar file cache disabled the
     * connection must own its jar file, with it enabled the JVM-wide default is left untouched, so that an
     * application-wide `URLConnection.setDefaultUseCaches(false)` keeps its effect.
     */
    private[directives] def openConnection(url: URL, useJarFileCache: Boolean): URLConnection = {
      val connection = url.openConnection()
      if (!useJarFileCache) connection.setUseCaches(false)
      connection
    }
  }

  /**
   * Opens the resource content. Metadata and content are read through separate connections, so with the jar file
   * cache disabled a jar-hosted resource is opened and parsed once more here: the entity may never be materialized
   * at all (HEAD or conditional requests), so the metadata connection cannot simply be handed over. Closing the
   * returned stream also closes the jar file it came from when the connection owns it.
   */
  private def openStream(url: URL, useJarFileCache: Boolean): InputStream =
    ResourceFile.openConnection(url, useJarFileCache).getInputStream
  case class ResourceFile(url: URL, length: Long, lastModified: Long)

  trait DirectoryRenderer extends pekko.http.javadsl.server.directives.DirectoryRenderer {
    type JDL = pekko.http.javadsl.server.directives.DirectoryListing
    type SDL = pekko.http.scaladsl.server.directives.DirectoryListing
    type SRE = pekko.http.scaladsl.model.RequestEntity
    type JRE = pekko.http.javadsl.model.RequestEntity

    def marshaller(renderVanityFooter: Boolean): ToEntityMarshaller[DirectoryListing]

    final override def directoryMarshaller(renderVanityFooter: Boolean): marshalling.Marshaller[JDL, JRE] = {
      val combined = Marshaller.combined[JDL, SDL, SRE](x =>
        JavaMapping.toScala(x)(RoutingJavaMapping.convertDirectoryListing))(marshaller(renderVanityFooter))
        .map(_.asJava)
      marshalling.Marshaller.fromScala(combined)
    }

  }
  trait LowLevelDirectoryRenderer {
    implicit def defaultDirectoryRenderer: DirectoryRenderer =
      new DirectoryRenderer {
        def marshaller(renderVanityFooter: Boolean): ToEntityMarshaller[DirectoryListing] =
          DirectoryListing.directoryMarshaller(renderVanityFooter)
      }
  }
  object DirectoryRenderer extends LowLevelDirectoryRenderer {
    implicit def liftMarshaller(implicit _marshaller: ToEntityMarshaller[DirectoryListing]): DirectoryRenderer =
      new DirectoryRenderer {
        def marshaller(renderVanityFooter: Boolean): ToEntityMarshaller[DirectoryListing] = _marshaller
      }
  }
}

trait ContentTypeResolver extends pekko.http.javadsl.server.directives.ContentTypeResolver {
  def apply(fileName: String): ContentType
  final override def resolve(fileName: String): model.ContentType = apply(fileName)
}

object ContentTypeResolver {

  /**
   * The default way of resolving a filename to a ContentType is by looking up the file extension in the
   * registry of all defined media-types. By default all non-binary file content is assumed to be UTF-8 encoded.
   */
  implicit val Default: ContentTypeResolver = withDefaultCharset(HttpCharsets.`UTF-8`)

  def withDefaultCharset(charset: HttpCharset): ContentTypeResolver =
    new ContentTypeResolver {
      def apply(fileName: String) = {
        val lastDotIx = fileName.lastIndexOf('.')
        val mediaType = if (lastDotIx >= 0) {
          fileName.substring(lastDotIx + 1) match {
            case "gz" => fileName.lastIndexOf('.', lastDotIx - 1) match {
                case -1 => MediaTypes.`application/octet-stream`
                case x  => MediaTypes.forExtension(fileName.substring(x + 1, lastDotIx)).withComp(MediaType.Gzipped)
              }
            case ext => MediaTypes.forExtension(ext)
          }
        } else MediaTypes.`application/octet-stream`
        ContentType(mediaType, () => charset)
      }
    }

  def apply(f: String => ContentType): ContentTypeResolver =
    new ContentTypeResolver {
      def apply(fileName: String): ContentType = f(fileName)
    }
}

final case class DirectoryListing(
    path: String, isRoot: Boolean, files: Seq[File]) extends javadsl.server.directives.DirectoryListing {
  override def getPath: String = path
  override def getFiles: java.util.List[File] = files.asJava
}

object DirectoryListing {

  private val html =
    """<html>
      |<head><title>Index of $</title></head>
      |<body>
      |<h1>Index of $</h1>
      |<hr>
      |<pre>
      |$</pre>
      |<hr>$
      |<div style="width:100%;text-align:right;color:gray">
      |<small>rendered by <a href="https://pekko.apache.org">Pekko Http</a> on $</small>
      |</div>$
      |</body>
      |</html>
      |""".stripMarginWithNewline("\n").split('$')

  private def escapeHtml(s: String): String = {
    val sb = new java.lang.StringBuilder(s.length + 16)
    var i = 0
    while (i < s.length) {
      s.charAt(i) match {
        case '&'  => sb.append("&amp;")
        case '<'  => sb.append("&lt;")
        case '>'  => sb.append("&gt;")
        case '"'  => sb.append("&quot;")
        case '\'' => sb.append("&#39;")
        case c    => sb.append(c)
      }
      i += 1
    }
    sb.toString
  }

  def directoryMarshaller(renderVanityFooter: Boolean): ToEntityMarshaller[DirectoryListing] =
    Marshaller.StringMarshaller.wrap(MediaTypes.`text/html`) { listing =>
      val DirectoryListing(path, isRoot, files) = listing
      val filesAndNames = files.map(file => file -> file.getName).sortBy(_._2)
      val deduped = filesAndNames.zipWithIndex.flatMap {
        case (fan @ (file, name), ix) =>
          if (ix == 0 || filesAndNames(ix - 1)._2 != name) Some(fan) else None
      }
      val (directoryFilesAndNames, fileFilesAndNames) = deduped.partition(_._1.isDirectory)
      def maxNameLength(seq: Seq[(File, String)]) = if (seq.isEmpty) 0 else seq.map(_._2.length).max
      val maxNameLen = math.max(maxNameLength(directoryFilesAndNames) + 1, maxNameLength(fileFilesAndNames))
      val sb = new java.lang.StringBuilder
      val escapedPath = escapeHtml(path)
      sb.append(html(0)).append(escapedPath).append(html(1)).append(escapedPath).append(html(2))
      if (!isRoot) {
        val secondToLastSlash = path.lastIndexOf('/', path.lastIndexOf('/', path.length - 1) - 1)
        sb.append("<a href=\"%s/\">../</a>\n".format(escapeHtml(path.substring(0, secondToLastSlash))))
      }
      def lastModified(file: File) = DateTime(file.lastModified).toIsoLikeDateTimeString
      def start(name: String) = {
        val escapedName = escapeHtml(name)
        sb.append("<a href=\"").append(escapeHtml(path)).append(escapedName).append("\">").append(escapedName).append(
          "</a>")
        var padding = maxNameLen - name.length
        while (padding > 0) {
          sb.append(' ')
          padding -= 1
        }
        sb
      }
      def renderDirectory(file: File, name: String) =
        start(name + '/').append("        ").append(lastModified(file)).append('\n')
      def renderFile(file: File, name: String) = {
        val size = pekko.http.impl.util.humanReadableByteCount(file.length, si = true)
        start(name).append("        ").append(lastModified(file))
        sb.append("                ".substring(size.length)).append(size).append('\n')
      }
      for ((file, name) <- directoryFilesAndNames) renderDirectory(file, name)
      for ((file, name) <- fileFilesAndNames) renderFile(file, name)
      if (isRoot && files.isEmpty) sb.append("(no files)\n")
      sb.append(html(3))
      if (renderVanityFooter) sb.append(html(4)).append(DateTime.now.toIsoLikeDateTimeString).append(html(5))
      sb.append(html(6)).toString
    }
}
