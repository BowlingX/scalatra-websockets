import sbt._
import Keys._
import org.scalatra.sbt._
import org.scalatra.sbt.PluginKeys._
import com.mojolly.scalate.ScalatePlugin._
import ScalateKeys._

object WebsocketscalatraBuild extends Build {
  val Organization = "com.bowlingx"
  val Name = "websocket"
  val Version = "0.1.0-SNAPSHOT"
  val ScalaVersion = "2.10.2"
  val ScalatraVersion = "2.2.1"
  val AtmosphereVersion = "2.0.0.RC1"
  val ShiroVersion = "1.2.2"
  val HazelcastVersion = "2.6"

  lazy val project = Project(
    "websocket-scalatra",
    file("."),
    settings = Defaults.defaultSettings ++ ScalatraPlugin.scalatraWithJRebel ++ scalateSettings ++ Seq(
      organization := Organization,
      name := Name,
      version := Version,
      scalaVersion := ScalaVersion,
      resolvers += Classpaths.typesafeReleases,
      //moduleConfigurations += ModuleConfiguration("org.atmosphere", "Sonatype Nexus Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots"),
      libraryDependencies ++= Seq(
        "org.scalatra" %% "scalatra" % ScalatraVersion,
        "org.scalatra" %% "scalatra-scalate" % ScalatraVersion,
        "org.scalatra" %% "scalatra-specs2" % ScalatraVersion % "test",
        "org.scalatra" %% "scalatra-json" % ScalatraVersion,
        "org.scalatra" %% "scalatra-commands" % ScalatraVersion,
        "org.atmosphere" % "atmosphere-runtime" % AtmosphereVersion,
        "org.json4s" %% "json4s-ext" % "3.1.0",
        "org.json4s" %% "json4s-jackson" % "3.1.0",
        "com.hazelcast" % "hazelcast" % HazelcastVersion,
        "org.apache.shiro" % "shiro-core" % ShiroVersion,
        "org.apache.shiro" % "shiro-web" % ShiroVersion,
        "ch.qos.logback" % "logback-classic" % "1.0.6" % "runtime",
        "org.eclipse.jetty" % "jetty-webapp" % "8.1.8.v20121106" % "container",
        "org.eclipse.jetty" % "jetty-websocket" % "8.1.8.v20121106",

        "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "container;provided;test" artifacts (Artifact("javax.servlet", "jar", "jar"))
      ),
      scalateTemplateConfig in Compile <<= (sourceDirectory in Compile) {
        base =>
          Seq(
            TemplateConfig(
              base / "webapp" / "WEB-INF" / "templates",
              Seq.empty, /* default imports should be added here */
              Seq(
                Binding("context", "_root_.org.scalatra.scalate.ScalatraRenderContext", importMembers = true, isImplicit = true)
              ), /* add extra bindings here */
              Some("templates")
            )
          )
      }
    )
  )
}
