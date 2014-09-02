import com.omegaup.libinteractive.target._
import com.omegaup.libinteractive.idl.Parser

import java.io.IOException
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.Files
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor

import scala.io.Source
import scala.collection.mutable.ListBuffer
import org.scalatest._

class TargetSpec extends FlatSpec with Matchers with BeforeAndAfter {
  def run(parentLang: String, childLang: String, path: Path, output: String,
			optionsTemplate: Options = Options()) = {
		val moduleName = path.getName(path.getNameCount - 1).toString
		val idlFile = path.resolve(s"$moduleName.idl") 
		val root = path.resolve(s".tests/${parentLang}_${childLang}")
    val options = optionsTemplate.copy(
      parentLang = parentLang,
      childLang = childLang,
      idlFile = idlFile,
      makefile = true,
			moduleName = moduleName,
			root = root,
			outputDirectory = root.resolve("libinteractive")
    )
		val parser = new Parser
		val idl = parser.parse(Source.fromFile(idlFile.toFile).mkString)

		new OutputDirectory(Paths.get(".")).install(options.outputDirectory)
		val problemsetter = idlFile.getParent.resolve(
			s"${idl.main.name}.${options.parentLang}")
		val contestant = idlFile.getParent.resolve(
			s"${options.moduleName}.${options.childLang}")

		Generator.generate(idl, options, problemsetter, contestant).foreach(
			_.install(options.outputDirectory))

		val process = Runtime.getRuntime.exec(Array(
			"/usr/bin/make", "-s", "run", "-C", root.toString
		))

		val reader = new BufferedReader(new InputStreamReader(process.getInputStream))
		val lines = ListBuffer.empty[String]
		var line: String = null
		while ( { line = reader.readLine ; line != null } ) {
			lines += line
		}

		withClue(root.toString) {
			lines.mkString("\n") should equal (output)
			process.waitFor should be (0)
		}
	}

	def runDirectory(directory: Path) = {
		val testRoot = directory.resolve(".tests")
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
		val output = Source.fromFile(directory.resolve("output").toFile).mkString.trim
		for (lang <- List("cpp", "java", "py")) {
			run(lang, "c", directory, output)
		}
		for (lang <- List("c", "cpp", "java", "py", "pas")) {
			run("c", lang, directory, output)
		}
	}

	"libinteractive" should "support multi-process targets" in {
		runDirectory(Paths.get("src/test/resources/mega"))
	}

	"libinteractive" should "produce working templates" in {
		val directory = Paths.get("src/test/resources/templates")
		val testRoot = directory.resolve(".tests")
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
		val output = Source.fromFile(directory.resolve("output").toFile).mkString.trim
		for (lang <- List("c", "cpp", "java", "py", "pas")) {
			run("c", lang, directory, output, Options(generateTemplate = true, force = true))
		}
	}
}

/* vim: set noexpandtab: */

