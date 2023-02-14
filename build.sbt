import akka._
import akka.ValidatePullRequest._
import PekkoDependency._
import Dependencies.{ h2specExe, h2specName }
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import java.nio.file.Files
import java.nio.file.attribute.{ PosixFileAttributeView, PosixFilePermission }

import sbtdynver.GitDescribeOutput
import spray.boilerplate.BoilerplatePlugin
import com.lightbend.paradox.apidoc.ApidocPlugin.autoImport.apidocRootPackage

inThisBuild(Def.settings(
  organization := "org.apache.pekko",
  apiURL := {
    val apiVersion = if (isSnapshot.value) "current" else version.value
    Some(url(s"https://doc.akka.io/api/akka-http/$apiVersion/"))
  },
  scmInfo := Some(
    ScmInfo(url("https://github.com/apache/incubator-pekko-http"), "git@github.com:apache/incubator-pekko-http.git")),
  licenses := Seq("Apache-2.0" -> url("https://opensource.org/licenses/Apache-2.0")),
  description := "Pekko Http: Modern, fast, asynchronous, streaming-first HTTP server and client.",
  testOptions ++= Seq(
    Tests.Argument(TestFrameworks.JUnit, "-q", "-v"),
    Tests.Argument(TestFrameworks.ScalaTest, "-oDF")),
  Dependencies.Versions,
  shellPrompt := { s => Project.extract(s).currentProject.id + " > " },
  concurrentRestrictions in Global += Tags.limit(Tags.Test, 1),
  onLoad in Global := {
    sLog.value.info(
      s"Building Pekko HTTP ${version.value} against Pekko ${PekkoDependency.pekkoVersion} on Scala ${(httpCore / scalaVersion).value}")
    (onLoad in Global).value
  },
  scalafixScalaBinaryVersion := scalaBinaryVersion.value,
  apacheSonatypeProjectProfile := "pekko"))

// When this is updated the set of modules in Http.allModules should also be updated
lazy val userProjects: Seq[ProjectReference] = List[ProjectReference](
  parsing,
  httpCore,
  http2Support,
  http,
  httpCaching,
  httpTestkit,
  httpMarshallersScala,
  httpMarshallersJava,
  httpSprayJson,
  httpXml,
  httpJackson,
  httpScalafixRules // don't aggregate tests for now as this will break with Scala compiler updates too easily
)
lazy val aggregatedProjects: Seq[ProjectReference] = userProjects ++ List[ProjectReference](
  httpTests,
  docs,
  // compatibilityTests,
  httpJmhBench,
  billOfMaterials)
lazy val root = Project(
  id = "pekko-http",
  base = file("."))
  .enablePlugins(UnidocRoot, NoPublish, PublishRsyncPlugin, AggregatePRValidation)
  .disablePlugins(MimaPlugin)
  .settings(
    name := "pekko-http-root",
    // Unidoc doesn't like macro definitions
    // compatibilityTests temporarily disabled
    unidocProjectExcludes := Seq(parsing, docs, httpTests, httpJmhBench, httpScalafix, httpScalafixRules,
      httpScalafixTestInput, httpScalafixTestOutput, httpScalafixTests),
    // Support applying macros in unidoc:
    scalaMacroSupport,
    Compile / headerCreate / unmanagedSources := (baseDirectory.value / "project").**("*.scala").get,
    publishRsyncArtifacts := {
      val unidocArtifacts = (Compile / unidoc).value
      // unidoc returns a Seq[File] which contains directories of generated API docs, one for
      // Java, one for Scala. It's not specified which is which, though.
      // We currently expect the java documentation at http/target/javaunidoc, so
      // the following heuristic is hopefully good enough to determine which one is the Java and
      // which one the Scala version.

      // This will fail with a MatchError when -Dakka.genjavadoc.enabled is not set
      val (Seq(java), Seq(scala)) = unidocArtifacts.partition(_.getName contains "java")

      Seq(
        scala -> gustavDir("api").value,
        java -> gustavDir("japi").value)
    })
  .aggregate(aggregatedProjects: _*)

/**
 * Adds a `src/.../scala-2.13+` source directory for Scala 2.13 and newer
 * and a `src/.../scala-2.13-` source directory for Scala version older than 2.13
 */
def add213CrossDirs(config: Configuration): Seq[Setting[_]] = Seq(
  config / unmanagedSourceDirectories += {
    val sourceDir = (config / sourceDirectory).value
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, n)) if n >= 13 => sourceDir / "scala-2.13+"
      case _                       => sourceDir / "scala-2.13-"
    }
  })

val commonSettings =
  add213CrossDirs(Compile) ++
  add213CrossDirs(Test)

val scalaMacroSupport = Seq(
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, n)) if n >= 13 =>
        Seq("-Ymacro-annotations")
      case _ =>
        Seq.empty
    }
  },
  libraryDependencies ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, n)) if n < 13 =>
      Seq(compilerPlugin(("org.scalamacros" % "paradise" % "2.1.1").cross(CrossVersion.full)))
    case _ => Seq.empty
  }))

lazy val parsing = project("parsing")
  .settings(commonSettings)
  .settings(AutomaticModuleName.settings("pekko.http.parsing"))
  .settings(MetaInfLicenseNoticeCopy.settings)
  .addPekkoModuleDependency("pekko-actor", "provided")
  .settings(Dependencies.parsing)
  .settings(
    scalacOptions --= Seq("-Xfatal-warnings", "-Xlint", "-Ywarn-dead-code"), // disable warnings for parboiled code
    scalacOptions += "-language:_")
  .settings(scalaMacroSupport)
  .enablePlugins(ScaladocNoVerificationOfDiagrams)
  .enablePlugins(ReproducibleBuildsPlugin)
  .disablePlugins(MimaPlugin)

lazy val httpCore = project("http-core")
  .settings(commonSettings)
  .settings(AutomaticModuleName.settings("pekko.http.core"))
  .settings(MetaInfLicenseNoticeCopy.settings)
  .dependsOn(parsing, httpScalafixRules % ScalafixConfig)
  .addPekkoModuleDependency("pekko-stream", "provided")
  .addPekkoModuleDependency(
    "pekko-stream-testkit",
    "test",
    pekko =
      if (System.getProperty("pekko.http.test-against-pekko-main", "false") == "true") PekkoDependency.masterSnapshot
      else PekkoDependency.default)
  .settings(Dependencies.httpCore)
  .settings(VersionGenerator.versionSettings)
  .settings(scalaMacroSupport)
  .enablePlugins(BootstrapGenjavadoc)
  .enablePlugins(ReproducibleBuildsPlugin)

lazy val http = project("http")
  .settings(commonSettings)
  .settings(AutomaticModuleName.settings("pekko.http"))
  .settings(MetaInfLicenseNoticeCopy.settings)
  .dependsOn(httpCore)
  .addPekkoModuleDependency("pekko-stream", "provided")
  .settings(Dependencies.http)
  .settings(
    Compile / scalacOptions += "-language:_")
  .settings(scalaMacroSupport)
  .enablePlugins(BootstrapGenjavadoc, BoilerplatePlugin)
  .enablePlugins(ReproducibleBuildsPlugin)

def gustavDir(kind: String) = Def.task {
  val ver =
    if (isSnapshot.value) "snapshot"
    else version.value
  s"www/$kind/akka-http/$ver"
}

lazy val http2Support = project("http2-support")
  .settings(commonSettings)
  .settings(AutomaticModuleName.settings("pekko.http.http2"))
  .settings(MetaInfLicenseNoticeCopy.settings)
  .dependsOn(httpCore, httpTestkit % "test", httpCore % "test->test")
  .addPekkoModuleDependency("pekko-stream", "provided")
  .addPekkoModuleDependency("pekko-stream-testkit", "test")
  .settings(Dependencies.http2)
  .settings(Dependencies.http2Support)
  .settings {
    lazy val h2specPath = Def.task {
      (Test / target).value / h2specName / h2specExe
    }
    Seq(
      Test / run / fork := true,
      Test / fork := true,
      Test / run / sbt.Keys.connectInput := true,
      Test / javaOptions += "-Dh2spec.path=" + h2specPath.value,
      Test / resourceGenerators += Def.task {
        val log = streams.value.log
        val h2spec = h2specPath.value

        if (!h2spec.exists) {
          log.info("Extracting h2spec to " + h2spec)

          for (zip <- (Test / update).value.select(artifact = artifactFilter(name = h2specName, extension = "zip")))
            IO.unzip(zip, (Test / target).value)

          // Set the executable bit on the expected path to fail if it doesn't exist
          for (view <- Option(Files.getFileAttributeView(h2spec.toPath, classOf[PosixFileAttributeView]))) {
            val permissions = view.readAttributes.permissions
            if (permissions.add(PosixFilePermission.OWNER_EXECUTE))
              view.setPermissions(permissions)
          }
        }
        Seq(h2spec)
      })
  }
  .enablePlugins(BootstrapGenjavadoc)
  .enablePlugins(ReproducibleBuildsPlugin)
  .disablePlugins(MimaPlugin) // experimental module still

lazy val httpTestkit = project("http-testkit")
  .settings(commonSettings)
  .settings(AutomaticModuleName.settings("pekko.http.testkit"))
  .settings(MetaInfLicenseNoticeCopy.settings)
  .dependsOn(http)
  .addPekkoModuleDependency("pekko-stream-testkit", "provided")
  .addPekkoModuleDependency("pekko-testkit", "provided")
  .settings(Dependencies.httpTestkit)
  .settings(
    // don't ignore Suites which is the default for the junit-interface
    testOptions += Tests.Argument(TestFrameworks.JUnit, "--ignore-runners="),
    Compile / scalacOptions ++= Seq("-language:_"),
    Test / run / mainClass := Some("org.apache.pekko.http.javadsl.SimpleServerApp"))
  .enablePlugins(BootstrapGenjavadoc, MultiNodeScalaTest, ScaladocNoVerificationOfDiagrams)
  .enablePlugins(ReproducibleBuildsPlugin)
  .disablePlugins(MimaPlugin) // testkit, no bin compat guaranteed

lazy val httpTests = project("http-tests")
  .settings(commonSettings)
  .settings(Dependencies.httpTests)
  .dependsOn(httpSprayJson, httpXml, httpJackson,
    httpTestkit % "test", httpCore % "test->test",
    httpScalafixRules % ScalafixConfig)
  .enablePlugins(NoPublish) // don't release tests
  .enablePlugins(MultiNode)
  .disablePlugins(MimaPlugin) // this is only tests
  .configs(MultiJvm)
  .settings(headerSettings(MultiJvm))
  .settings(ValidatePR / additionalTasks += MultiJvm / headerCheck)
  .addPekkoModuleDependency("pekko-stream", "provided")
  .addPekkoModuleDependency("pekko-multi-node-testkit", "test")
  .settings(
    // Fix to reenable scala-steward, see https://gitter.im/scala-steward-org/scala-steward?at=6183bb66d78911028a1b7cd8
    // Putting that jar file with the complicated name into the git tree directly breaks if something in the environment
    // has unicode path names configured wrongly. So, we wrap it into an extra zip file which is extracted before
    // tests are run.
    Test / unmanagedJars += {
      val targetDir = target.value / "extra-libs"
      val targetFile = targetDir / "i have späces.jar"
      if (!targetFile.exists) {
        val zipFile = (Test / sourceDirectory).value / "extra-libs.zip"
        IO.unzip(zipFile, targetDir)
      }
      targetFile
    })

lazy val httpJmhBench = project("http-bench-jmh")
  .settings(commonSettings)
  .dependsOn(http, http2Support % "compile->compile,test")
  .addPekkoModuleDependency("pekko-stream")
  .enablePlugins(JmhPlugin)
  .enablePlugins(NoPublish) // don't release benchs
  .disablePlugins(MimaPlugin)

lazy val httpMarshallersScala = project("http-marshallers-scala")
  .settings(commonSettings)
  .enablePlugins(NoPublish /*, AggregatePRValidation*/ )
  .disablePlugins(MimaPlugin)
  .aggregate(httpSprayJson, httpXml)

lazy val httpXml =
  httpMarshallersScalaSubproject("xml")
    .settings(AutomaticModuleName.settings("pekko.http.marshallers.scalaxml"))
    .settings(MetaInfLicenseNoticeCopy.settings)
    .addPekkoModuleDependency("pekko-stream", "provided")
    .settings(Dependencies.httpXml)

lazy val httpSprayJson =
  httpMarshallersScalaSubproject("spray-json")
    .settings(AutomaticModuleName.settings("pekko.http.marshallers.sprayjson"))
    .settings(MetaInfLicenseNoticeCopy.settings)
    .addPekkoModuleDependency("pekko-stream", "provided")
    .settings(Dependencies.httpSprayJson)

lazy val httpMarshallersJava = project("http-marshallers-java")
  .settings(commonSettings)
  .enablePlugins(NoPublish /*, AggregatePRValidation*/ )
  .disablePlugins(MimaPlugin)
  .aggregate(httpJackson)

lazy val httpJackson =
  httpMarshallersJavaSubproject("jackson")
    .settings(AutomaticModuleName.settings("pekko.http.marshallers.jackson"))
    .settings(MetaInfLicenseNoticeCopy.settings)
    .addPekkoModuleDependency("pekko-stream", "provided")
    .addPekkoModuleDependency("pekko-stream-testkit", "test")
    .dependsOn(httpTestkit % "test")
    .settings(Dependencies.httpJackson)
    .enablePlugins(ScaladocNoVerificationOfDiagrams)

lazy val httpCaching = project("http-caching")
  .settings(
    name := "pekko-http-caching")
  .settings(commonSettings)
  .settings(AutomaticModuleName.settings("pekko.http.caching"))
  .settings(MetaInfLicenseNoticeCopy.settings)
  .addPekkoModuleDependency("pekko-stream", "provided")
  .addPekkoModuleDependency("pekko-stream-testkit", "provided")
  .settings(Dependencies.httpCaching)
  .dependsOn(http, httpCore, httpTestkit % "test")
  .enablePlugins(BootstrapGenjavadoc)

def project(moduleName: String) =
  Project(id = moduleName, base = file(moduleName)).settings(
    name := s"pekko-$moduleName")

def httpMarshallersScalaSubproject(moduleName: String) =
  Project(
    id = s"http-$moduleName",
    base = file(s"http-marshallers-scala/http-$moduleName"))
    .dependsOn(http)
    .settings(
      name := s"pekko-http-$moduleName")
    .settings(commonSettings)
    .enablePlugins(BootstrapGenjavadoc)
    .enablePlugins(ReproducibleBuildsPlugin)

def httpMarshallersJavaSubproject(moduleName: String) =
  Project(
    id = s"http-$moduleName",
    base = file(s"http-marshallers-java/http-$moduleName"))
    .settings(
      name := s"pekko-http-$moduleName")
    .dependsOn(http)
    .settings(commonSettings)
    .enablePlugins(BootstrapGenjavadoc)
    .enablePlugins(ReproducibleBuildsPlugin)

lazy val httpScalafix = project("http-scalafix")
  .enablePlugins(NoPublish)
  .disablePlugins(MimaPlugin)
  .aggregate(httpScalafixRules, httpScalafixTestInput, httpScalafixTestOutput, httpScalafixTests)

lazy val httpScalafixRules =
  Project(id = "http-scalafix-rules", base = file("http-scalafix/scalafix-rules"))
    .settings(
      name := "pekko-http-scalafix-rules",
      libraryDependencies += Dependencies.Compile.scalafix)
    .disablePlugins(MimaPlugin) // tooling, no bin compat guaranteed

lazy val httpScalafixTestInput =
  Project(id = "http-scalafix-test-input", base = file("http-scalafix/scalafix-test-input"))
    .dependsOn(http)
    .addPekkoModuleDependency("pekko-stream")
    .enablePlugins(NoPublish)
    .disablePlugins(MimaPlugin, HeaderPlugin /* because it gets confused about metaheader required for tests */ )
    .settings(
      addCompilerPlugin(scalafixSemanticdb),
      scalacOptions ++= List(
        "-Yrangepos",
        "-P:semanticdb:synthetics:on"),
      scalacOptions := scalacOptions.value.filterNot(Set("-deprecation", "-Xlint").contains(_)) // we expect deprecated stuff in there
    )

lazy val httpScalafixTestOutput =
  Project(id = "http-scalafix-test-output", base = file("http-scalafix/scalafix-test-output"))
    .dependsOn(http)
    .addPekkoModuleDependency("pekko-stream")
    .enablePlugins(NoPublish)
    .disablePlugins(MimaPlugin, HeaderPlugin /* because it gets confused about metaheader required for tests */ )

lazy val httpScalafixTests =
  Project(id = "http-scalafix-tests", base = file("http-scalafix/scalafix-tests"))
    .enablePlugins(NoPublish)
    .disablePlugins(MimaPlugin)
    .settings(
      name := "pekko-http-scalafix-tests",
      publish / skip := true,
      libraryDependencies += ("ch.epfl.scala" % "scalafix-testkit" % Dependencies.scalafixVersion % Test).cross(
        CrossVersion.full),
      Compile / compile :=
        (Compile / compile).dependsOn(httpScalafixTestInput / Compile / compile).value,
      scalafixTestkitOutputSourceDirectories :=
        (httpScalafixTestOutput / Compile / sourceDirectories).value,
      scalafixTestkitInputSourceDirectories :=
        (httpScalafixTestInput / Compile / sourceDirectories).value,
      scalafixTestkitInputClasspath :=
        (httpScalafixTestInput / Compile / fullClasspath).value)
    .dependsOn(httpScalafixRules)
    .enablePlugins(ScalafixTestkitPlugin)

lazy val docs = project("docs")
  .enablePlugins(ParadoxPlugin, NoPublish, PublishRsyncPlugin)
  .disablePlugins(MimaPlugin)
  .addPekkoModuleDependency("pekko-stream", "provided", PekkoDependency.docs)
  .addPekkoModuleDependency("pekko-actor-typed", "provided", PekkoDependency.docs)
  .addPekkoModuleDependency("pekko-multi-node-testkit", "provided", PekkoDependency.docs)
  .addPekkoModuleDependency("pekko-stream-testkit", "provided", PekkoDependency.docs)
  .addPekkoModuleDependency("pekko-actor-testkit-typed", "provided", PekkoDependency.docs)
  .dependsOn(
    httpCore, http, httpXml, http2Support, httpMarshallersJava, httpMarshallersScala, httpCaching,
    httpTests % "compile;test->test", httpTestkit % "compile;test->test", httpScalafixRules % ScalafixConfig)
  .settings(Dependencies.docs)
  .settings(
    name := "pekko-http-docs",
    resolvers += Resolver.jcenterRepo,
    scalacOptions ++= Seq(
      // Make sure we don't accidentally keep documenting deprecated calls
      "-Xfatal-warnings",
      // In docs adding an unused variable can be helpful, for example
      // to show its type
      "-Xlint:-unused",
      // Does not appear to lead to problems
      "-Wconf:msg=The outer reference in this type test cannot be checked at run time:s"),
    scalacOptions --= Seq(
      // Code after ??? can be considered 'dead',  but still useful for docs
      "-Ywarn-dead-code"),
    (Compile / paradoxProcessor) := {
      import scala.concurrent.duration._
      import com.lightbend.paradox.ParadoxProcessor
      import com.lightbend.paradox.markdown.{ Reader, Writer }
      // FIXME: use `paradoxParsingTimeout` when https://github.com/lightbend/paradox/pull/447 has been released
      new ParadoxProcessor(
        reader = new Reader(maxParsingTime = 10.seconds),
        writer = new Writer(serializerPlugins = Writer.defaultPlugins(paradoxDirectives.value)))
    },
    paradoxGroups := Map("Language" -> Seq("Scala", "Java")),
    Compile / paradoxProperties ++= Map(
      "project.name" -> "Apache Pekko HTTP",
      "canonical.base_url" -> "https://pekko.apache.org/docs/akka-http/current",
      "akka.version" -> PekkoDependency.docs.version,
      "akka.minimum.version25" -> PekkoDependency.minimumExpectedPekkoVersion,
      "akka.minimum.version26" -> PekkoDependency.minimumExpectedPekko26Version,
      "jackson.xml.version" -> Dependencies.jacksonXmlVersion,
      "scalafix.version" -> _root_.scalafix.sbt.BuildInfo.scalafixVersion, // grab from scalafix plugin directly
      "extref.pekko-docs.base_url" -> s"https://pekko.apache.org/docs/pekko/current/%s",
      "javadoc.akka.http.base_url" -> {
        val v = if (isSnapshot.value) "current" else version.value
        s"https://doc.akka.io/japi/akka-http/$v"
      },
      "javadoc.akka.base_url" -> s"https://doc.akka.io/japi/akka/${PekkoDependency.docs.link}",
      "javadoc.akka.link_style" -> "direct",
      "scaladoc.akka.http.base_url" -> {
        val v = if (isSnapshot.value) "current" else version.value
        s"https://doc.akka.io/api/akka-http/$v"
      },
      "scaladoc.akka.base_url" -> s"https://doc.akka.io/api/akka/${PekkoDependency.docs.link}",
      "algolia.docsearch.api_key" -> "0ccbb8bf5148554a406fbf07df0a93b9",
      "algolia.docsearch.index_name" -> "akka-http",
      "google.analytics.account" -> "UA-21117439-1",
      "google.analytics.domain.name" -> "akka.io",
      "github.base_url" -> GitHub.url(version.value, isSnapshot.value)),
    apidocRootPackage := "org.apache.pekko",
    ValidatePR / additionalTasks += Compile / paradox,
    ThisBuild / publishRsyncHost := "akkarepo@gustav.akka.io",
    publishRsyncArtifacts := List((Compile / paradox).value -> gustavDir("docs").value))
  .settings(ParadoxSupport.paradoxWithCustomDirectives)

/*
lazy val compatibilityTests = Project("http-compatibility-tests", file("http-compatibility-tests"))
  .enablePlugins(NoPublish)
  .disablePlugins(MimaPlugin)
  .addPekkoModuleDependency("pekko-stream", "provided")
  .settings(
    libraryDependencies ++= Seq(
      "org.apache.pekko" %% "pekko-http" % MiMa.latest101Version % "provided",
    ),
    (Test / dependencyClasspath) := {
      // HACK: We'd like to use `dependsOn(http % "test->compile")` to upgrade the explicit dependency above to the
      //       current version but that fails. So, this is a manual `dependsOn` which works as expected.
      (Test / dependencyClasspath).value.filterNot(_.data.getName contains "akka") ++
      (httpTests / Test / fullClasspath).value
    }
  )
 */

lazy val billOfMaterials = Project("bill-of-materials", file("bill-of-materials"))
  .enablePlugins(BillOfMaterialsPlugin)
  .disablePlugins(MimaPlugin)
  .settings(
    name := "pekko-http-bom",
    bomIncludeProjects := userProjects)

def hasCommitsAfterTag(description: Option[GitDescribeOutput]): Boolean = description.get.commitSuffix.distance > 0
