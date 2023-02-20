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

object Transact extends Tag("com.omegaup.libinteractive.Transact")

class TargetSpec extends FlatSpec with Matchers with BeforeAndAfterAll {
	val testRoot = Paths.get(".tests")

	val parentLanguages = List("cpp", "java", "py")

	val childLanguages =
		if (System.getProperty("os.name").toLowerCase.startsWith("mac"))
			List("c", "cpp", "java", "py")
		else
			List("c", "cpp", "java", "py", "pas", "cs")

	val transactSupportedLanguages =
			Set("c", "cpp", "java", "py", "cs")

	val CONTROL_LIMIT = ' '
	val PRINTABLE_LIMIT = '\u007e'
	val HEX_DIGITS = Array[Char]('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

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

  def run(parentLang: String, childLang: String, path: Path, expectedOutput: String,
			optionsTemplate: Options = Options()) = {
		val moduleName = path.getName(path.getNameCount - 1).toString
		val idlFile = deploy(path.resolve(s"$moduleName.idl"))
		val root = testRoot.resolve(path).resolve(s"${parentLang}_${childLang}")
    val options = optionsTemplate.copy(
      parentLang = parentLang,
      childLang = childLang,
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

		withClue(root.toString) {
			val exitStatus = process.waitFor
			if (stdout != expectedOutput || exitStatus != 0) {
				fail(s"expected: ${repr(expectedOutput)} got: ${repr(stdout)}.\nexit status: ${exitStatus}.\nstderr: ${stderr}")
			}
		}
	}

	def runDirectory(directory: Path) = {
		val output = Source.fromFile(deploy(directory.resolve("output")).toFile).mkString.trim
		for (lang <- parentLanguages) {
			run(lang, "c", directory, output)
		}
		for (lang <- childLanguages) {
			run("c", lang, directory, output)
		}
	}

	"libinteractive" should "support multi-process targets" in {
		runDirectory(Paths.get("mega"))
	}

	"libinteractive" should "produce working templates" in {
		val directory = Paths.get("templates")
		val output = Source.fromFile(deploy(directory.resolve("output")).toFile).mkString.trim
		for (lang <- childLanguages) {
			run("c", lang, directory, output, Options(generateTemplate = true, verbose = true))
		}
	}

	"libinteractive" should "support transact" taggedAs(Transact) in {
		assume(Files.isDirectory(Paths.get("/sys/module/transact")),
			"The 'transact' module is not loaded")

		val directory = Paths.get("templates_transact")
		val output = Source.fromFile(deploy(directory.resolve("output")).toFile).mkString.trim
		for (lang <- childLanguages.filter(transactSupportedLanguages)) {
			run("c", lang, directory, output, Options(generateTemplate = true, verbose = true, transact = true))
		}
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

/* vim: set noexpandtab: */

