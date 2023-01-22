name := "libinteractive"

organization := "com.omegaup"

scalaVersion := "2.12.17"

ThisBuild / scalaVersion := scalaVersion.value
ThisProject / scalaVersion := scalaVersion.value

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

enablePlugins(GitVersioning)

git.useGitDescribe := true

lazy val root = (project in file("."))
	.enablePlugins(SbtTwirl)
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "com.omegaup.libinteractive"
  )

publishMavenStyle := true

Test / publishArtifact := false

publishTo := {
	val nexus = "https://oss.sonatype.org/"
	if (isSnapshot.value)
		Some("snapshots" at nexus + "content/repositories/snapshots")
	else
		Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

pomIncludeRepository := { _ => false }

pomExtra := (
	<url>https://omegaup.com/libinteractive/</url>
	<licenses>
		<license>
			<name>BSD-style</name>
			<url>http://www.opensource.org/licenses/bsd-license.php</url>
			<distribution>repo</distribution>
		</license>
	</licenses>
	<scm>
		<url>git@github.com:omegaup/libinteractive.git</url>
		<connection>scm:git:git@github.com:omegaup/libinteractive.git</connection>
	</scm>
	<developers>
		<developer>
			<id>lhchavez</id>
			<name>Luis Hector Chavez</name>
			<url>http://lhchavez.com</url>
		</developer>
	</developers>
)

TwirlKeys.templateFormats += ("code" -> "com.omegaup.libinteractive.templates.CodeFormat")

exportJars := true

Compile / packageBin / packageOptions +=
	Package.ManifestAttributes( java.util.jar.Attributes.Name.MAIN_CLASS -> "com.omegaup.libinteractive.Main" )

Compile / packageBin / mappings ++= Seq(
	(baseDirectory.value / "LICENSE") -> "LICENSE",
	(baseDirectory.value / "NOTICE") -> "NOTICE"
)

libraryDependencies ++= Seq(
	"com.github.scopt" %% "scopt" % "3.7.1",
	"org.apache.commons" % "commons-compress" % "1.8.1",
	"org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.7",
	"org.scalatest" %% "scalatest" % "3.0.4" % "test"
)

resolvers += Resolver.sonatypeRepo("public")

enablePlugins(SbtProguard)

(Proguard / proguardOptions) ++= Seq(
  "-dontskipnonpubliclibraryclasses",
  "-dontskipnonpubliclibraryclassmembers",
  "-dontoptimize",
  "-dontobfuscate",
  "-dontnote",
  "-dontwarn",
  "-keep interface scala.ScalaObject",
  "-keep class com.omegaup.libinteractive.**",
  "-keep class scala.collection.JavaConversions",
)
(Proguard / proguardOptions) += ProguardOptions.keepMain("com.omegaup.libinteractive.Main")
(Proguard / proguardInputFilter) := { file =>
  file.name match {
    case s if s.startsWith("libinteractive") => None
    case _ => Some("!META-INF/MANIFEST.MF,!META-INF/LICENSE.txt,!META-INF/NOTICE.txt,!rootdoc.txt")
  }
}
(Proguard / proguardVersion) := "7.3.1"
