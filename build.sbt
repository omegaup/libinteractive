name := "libinteractive"

version := "1.0"

organization := "omegaup"

scalaVersion := "2.10.3"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

exportJars := true

packageOptions in (Compile, packageBin) +=
    Package.ManifestAttributes( java.util.jar.Attributes.Name.MAIN_CLASS -> "com.omegaup.libinteractive.Main" )

mappings in (Compile, packageBin) ++= Seq(
	(baseDirectory.value / "LICENSE") -> "LICENSE",
	(baseDirectory.value / "NOTICE") -> "NOTICE"
)

libraryDependencies ++= Seq(
	"com.github.scopt" %% "scopt" % "3.2.0",
	"org.scalatest" %% "scalatest" % "2.2.1" % "test"
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
  "-keep class com.omegaup.**",
  "-keep class scala.collection.JavaConversions",
  ProguardOptions.keepMain("com.omegaup.libinteractive.Main")
)

ProguardKeys.inputFilter in Proguard := { file =>
  file.name match {
    case s if s.startsWith("libinteractive") => None
    case _ => Some("!META-INF/MANIFEST.MF,!rootdoc.txt")
  }
}

javaOptions in (Proguard, ProguardKeys.proguard) := Seq("-Xmx2G")
