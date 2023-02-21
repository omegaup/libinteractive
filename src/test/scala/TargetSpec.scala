// Copyright (c) 2014 The omegaUp Contributors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import com.omegaup.libinteractive.target._
import com.omegaup.libinteractive.idl.Parser

import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.Files
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor

import scala.collection.JavaConversions.asJavaIterable
import scala.collection.mutable.ListBuffer
import scala.io.Source
import scala.util.control.Breaks._
import org.scalatest._
import org.scalatest.prop._
import org.scalatest.prop.Tables.Table
import matchers._

object Transact extends Tag("com.omegaup.libinteractive.Transact")

trait RunMatchers {
	val testRoot = Paths.get(".tests")

	val CONTROL_LIMIT = ' '
	val PRINTABLE_LIMIT = '\u007e'
	val HEX_DIGITS = Array[Char]('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

	case class LanguagePair(parentLang: String, childLang: String)

	def deploy(path: Path) = {
		val resource = getClass.getResource(path.toString)
		val deployPath = testRoot.resolve(path)
		if (resource != null) {
			val source = Source.fromURL(resource)
			if (!Files.exists(deployPath.getParent)) {
				Files.createDirectories(deployPath.getParent)
			}
			Files.write(deployPath, source.getLines.toIterable, Charset.forName("UTF-8"))
		}
		deployPath
	}

  class RunMatcher(path: Path, expectedOutput: String,
			optionsTemplate: Options = Options()) extends Matcher[LanguagePair] {
		def apply(l: LanguagePair) = {
			val moduleName = path.getName(path.getNameCount - 1).toString
			val idlFile = deploy(path.resolve(s"$moduleName.idl"))
			val root = testRoot.resolve(path).resolve(s"${l.parentLang}_${l.childLang}")
			val options = optionsTemplate.copy(
				parentLang = l.parentLang,
				childLang = l.childLang,
				idlFile = idlFile,
				makefile = true,
				moduleName = moduleName,
				root = root.toAbsolutePath,
				quiet = true
			)
			val parser = new Parser
			val idl = parser.parse(Source.fromFile(idlFile.toFile).mkString)

			val installer = new InstallVisitor
			val problemsetter = deploy(path.resolve(
				s"${idl.main.name}.${options.parentLang}")).toAbsolutePath
			val contestant = deploy(path.resolve(
				s"${options.moduleName}.${options.childLang}")).toAbsolutePath

			installer.apply(new OutputDirectory(Paths.get(".")))
			Generator.generate(idl, options, problemsetter, contestant).foreach(
				installer.apply)

			Files.copy(problemsetter, root.resolve(problemsetter.getFileName))
			Files.copy(contestant, root.resolve(contestant.getFileName))

			val process = Runtime.getRuntime.exec(Array(
				"/usr/bin/make", "-s", "run", "-C", root.toString
			))

			val readStream: (InputStream) => String = (inputStream) => {
				val result = new ByteArrayOutputStream()
				val buffer = Array.fill[Byte](1024)(0)

				breakable {
					while (true) {
						val length = inputStream.read(buffer)
						if (length == -1) break
						result.write(buffer, 0, length)
					}
				}
				result.toString("UTF-8").trim()
			};
			val stdout = readStream(process.getInputStream)
			val stderr = readStream(process.getErrorStream)

			val exitStatus = process.waitFor
			MatchResult(
				stdout == expectedOutput && exitStatus == 0,
				s"${l.parentLang} => ${l.childLang}\nexpected: ${repr(expectedOutput)} got: ${repr(stdout)}.\nexit status: ${exitStatus}.\nstderr: ${stderr}",
				s"${l.parentLang} => ${l.childLang}\nmatched expected output: ${repr(stdout)}",
			)
		}

		def repr(source: String): String = {
			if (source == null) return null

			val sb = new StringBuilder()
			val limit = source.length()
			var hexbuf: Array[Char] = null

			var pointer = 0

			sb.append('"')

			while (pointer < limit) {
				val ch = source.charAt(pointer)
				pointer += 1

				ch match {
					case '\u0000' => sb.append("\\0")
					case '\t' => sb.append("\\t")
					case '\n' => sb.append("\\n")
					case '\r' => sb.append("\\r")
					case '\"' => sb.append("\\\"")
					case '\\' => sb.append("\\\\")
					case ch if CONTROL_LIMIT <= ch && ch <= PRINTABLE_LIMIT => sb.append(ch)
					case _ => {
						sb.append("\\u")

						if (hexbuf == null)
							hexbuf = Array.fill[Char](4)(0)

						var offs = 4
						var codepoint = ch.asInstanceOf[Int]
						do {
							hexbuf(offs) = HEX_DIGITS(codepoint & 0xf)
							codepoint >>>= 4
							offs -= 1
						} while(offs > 0)

						sb.append(hexbuf, 0, 4)
					}
				}
			}

			return sb.append('"').toString()
		}
	}

	def run(path: Path, expectedOutput: String, optionsTemplate: Options =
		Options()) = new RunMatcher(path, expectedOutput, optionsTemplate)

	def languagePair(parent: String, child: String) = new LanguagePair(parent, child)
}

class TargetSpec extends FlatSpec with Matchers with BeforeAndAfterAll with TableDrivenPropertyChecks with RunMatchers {
	val parentLanguages = Table("cpp", "java", "py")

	val childLanguages =
		if (System.getProperty("os.name").toLowerCase.startsWith("mac"))
			Table("c", "cpp", "java", "py")
		else
			Table("c", "cpp", "java", "py", "pas", "cs")

	val transactSupportedLanguages =
			Set("c", "cpp", "java", "py", "cs")

	override def beforeAll() = {
		if (Files.exists(testRoot)) {
			Files.walkFileTree(testRoot, new SimpleFileVisitor[Path] {
				override def visitFile(file: Path, attrs: BasicFileAttributes):
						FileVisitResult = {
					Files.delete(file)
					FileVisitResult.CONTINUE
				}

				override def postVisitDirectory(dir: Path, exc: IOException):
						FileVisitResult = {
					Files.delete(dir)
					FileVisitResult.CONTINUE
				}
			})
		}
		Files.createDirectory(testRoot)
	}

	"libinteractive" should "support multi-process targets" in {
		val directory = Paths.get("mega")
		val output = Source.fromFile(deploy(directory.resolve("output")).toFile).mkString.trim
		forEvery(parentLanguages) { lang =>
			languagePair(lang, "c") should run (directory, output)
		}
		forEvery(childLanguages) { lang =>
			languagePair("c", lang) should run (directory, output)
		}
	}

	"libinteractive" should "produce working templates" in {
		val directory = Paths.get("templates")
		val output = Source.fromFile(deploy(directory.resolve("output")).toFile).mkString.trim
		forEvery(childLanguages) { lang =>
			languagePair("c", lang) should run (directory, output, Options(generateTemplate = true, verbose = true))
		}
	}

	"libinteractive" should "support transact" taggedAs(Transact) in {
		assume(Files.isDirectory(Paths.get("/sys/module/transact")),
			"The 'transact' module is not loaded")

		val directory = Paths.get("templates_transact")
		val output = Source.fromFile(deploy(directory.resolve("output")).toFile).mkString.trim
		forEvery(childLanguages.filter(transactSupportedLanguages)) { lang =>
			languagePair("c", lang) should run (directory, output, Options(generateTemplate = true, verbose = true, transact = true))
		}
	}
}

/* vim: set noexpandtab: */

