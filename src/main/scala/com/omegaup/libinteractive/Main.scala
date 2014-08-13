package com.omegaup.libinteractive

import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

import com.omegaup.libinteractive.idl.IDL
import com.omegaup.libinteractive.idl.Parser
import com.omegaup.libinteractive.target._
import scala.io.Source

object Main {
	def main(args: Array[String]): Unit = {
		val optparse = new scopt.OptionParser[Options]("libinteractive") {
			head("libinteractive", "0.1")
			help("help") text("display this message")
			version("version") text("display version information")

			opt[File]("output-directory") action { (x, c) => c.copy(outputDirectory = x) } text
					("the directory in which to generate the files")
			cmd("validate") action { (_, c) => c.copy(command = Command.Verify) } text
					("only validate the .idl file") children(
				arg[File]("file") action { (x, c) => c.copy(idlFile = x) } text
						("the .idl file that describes the interfaces")
			)
			cmd("generate") action { (_, c) => c.copy(command = Command.Generate) } text
					("generate IPC shims") children(
				arg[File]("file") action { (x, c) => c.copy(idlFile = x) } text
						("the .idl file that describes the interfaces"),
				arg[String]("parent-lang") action { (x, c) => c.copy(parentLang = x) } text
						("the language in which the grader/validator is written"),
				arg[String]("child-lang") action { (x, c) => c.copy(childLang = x) } text
						("the language in which the submission is written"),
				opt[Unit]("makefile") action { (_, c) => c.copy(makefile = true) } text
						("also generate a Makefile"),
				opt[Unit]("sequential-ids") action { (_, c) => c.copy(sequentialIds = true) } text
						("use sequential (instead of random) IDs for functions"),
				opt[Unit]("verbose") action { (_, c) => c.copy(verbose = true) } text
						("add verbose logging information to the generated shims")
			)
		}

		optparse.parse(args, Options()) map { rawOptions => {
			val fileName = rawOptions.idlFile.getName
			val extPos = fileName.lastIndexOf('.')
			val options = (if (extPos == -1) {
				rawOptions.copy(moduleName = fileName)
			} else {
				rawOptions.copy(moduleName = fileName.substring(0, extPos))
			})
			val idl = Parser(Source.fromFile(options.idlFile).mkString)
			options.command match {
				case Command.Generate => {
					val parent = target(options.parentLang, idl, options)
					val child = target(options.childLang, idl, options)
					for (output <- parent.generateParent ++ child.generateChildren) {
						val targetFile = new File(options.outputDirectory, output.filename)
						val out = new BufferedWriter(new FileWriter(targetFile))
						out.write(output.contents)
						out.close
					}
				}
				case Command.Verify =>
					System.out.println("OK")
			}
		}} getOrElse {
			System.exit(1)
		}
	}

	def target(lang: String, idl: IDL, options: Options): Target = {
		lang match {
			case "c" => new C(idl, options)
			case "cpp" => new C(idl, options)
			case "java" => new Java(idl, options)
			case "pas" => new Pascal(idl, options)
			case "py" => new Python(idl, options)
			case "rb" => new Ruby(idl, options)
		}
	}
}

/* vim: set noexpandtab: */
