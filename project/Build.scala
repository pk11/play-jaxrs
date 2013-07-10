import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName = "jax-rs"
  val appVersion = "0.6"

  val appDependencies = Seq(
    // Add your project dependencies here,
    "javax.ws.rs" % "jsr311-api" % "1.1-ea",
    "org.scalatest" % "scalatest_2.10" % "1.9.1" % "test",
    javaCore)

  val main = play.Project(appName, appVersion, appDependencies).settings(
    unmanagedSourceDirectories in Compile <++= baseDirectory { base =>
       Seq(base / "src/main/scala") },
	
    organization := "org.pk11",

    publishMavenStyle := true,
    
    publishTo <<= (version) { version: String =>
      Some(Resolver.file("file", new File("/Users/phausel/workspace/pk11-scratch")))
    },

    mappings in (Compile, packageBin) ~= { (ms: Seq[(File, String)]) =>
      if (System.getProperty("jaxrs") != null)
        ms filter {
          case (file, toPath) => toPath startsWith ("org/pk11/jaxrs")
        }
      else ms
    })

}
