name := "libinteractive"

version := "1.6.1"

organization := "com.omegaup"

scalaVersion := "2.11.5"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

lazy val root = (project in file("."))
	.enablePlugins(SbtTwirl)

publishArtifact in (Proguard) := true

TwirlKeys.templateFormats += ("code" -> "com.omegaup.libinteractive.templates.CodeFormat")

exportJars := true

packageOptions in (Compile, packageBin) +=
	Package.ManifestAttributes( java.util.jar.Attributes.Name.MAIN_CLASS -> "com.omegaup.libinteractive.Main" )

mappings in (Compile, packageBin) ++= Seq(
	(baseDirectory.value / "LICENSE") -> "LICENSE",
	(baseDirectory.value / "NOTICE") -> "NOTICE"
)

libraryDependencies ++= Seq(
	"com.github.scopt" %% "scopt" % "3.2.0",
	"org.apache.commons" % "commons-compress" % "1.8.1",
	"org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.3",
	"org.scalatest" %% "scalatest" % "2.2.4" % "test"
)

resolvers += Resolver.sonatypeRepo("public")

proguardSettings

ProguardKeys.options in Proguard ++= Seq(
  "-dontskipnonpubliclibraryclasses",
  "-dontskipnonpubliclibraryclassmembers",
  "-dontoptimize",
  "-dontobfuscate",
  "-dontpreverify",
  "-dontnote",
  "-dontwarn",
  "-keep interface scala.ScalaObject",
  "-keep class com.omegaup.libinteractive.**",
  "-keep class scala.collection.JavaConversions",
  ProguardOptions.keepMain("com.omegaup.libinteractive.Main")
)

ProguardKeys.inputFilter in Proguard := { file =>
  file.name match {
    case s if s.startsWith("libinteractive") => None
    case _ => Some("!META-INF/MANIFEST.MF,!META-INF/LICENSE.txt,!META-INF/NOTICE.txt,!rootdoc.txt")
  }
}

ProguardKeys.proguardVersion in Proguard := "5.0"

javaOptions in (Proguard, ProguardKeys.proguard) := Seq("-Xmx2G")
