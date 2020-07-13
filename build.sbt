name := "libinteractive"

enablePlugins(GitVersioning)

git.useGitDescribe := true

organization := "com.omegaup"

scalaVersion := "2.11.5"

scalaVersion in ThisProject := "2.11.5"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

lazy val root = (project in file("."))
	.enablePlugins(SbtTwirl)
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "com.omegaup.libinteractive"
  )

publishMavenStyle := true

publishArtifact in Test := false

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
