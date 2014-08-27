package com.omegaup.libinteractive

import java.io.File
import java.nio.file.Paths

import com.omegaup.libinteractive.idl.IDL
import com.omegaup.libinteractive.idl.Parser
import com.omegaup.libinteractive.target._
import scala.io.Source

object Main {
	def main(args: Array[String]): Unit = {
		val optparse = new scopt.OptionParser[Options]("libinteractive") {
			head("libinteractive", Main.getClass.getPackage.getImplementationVersion)
			help("help") text("display this message")
			version("version") text("display version information")

			opt[File]("output-directory") action
					{ (x, c) => c.copy(outputDirectory = x.toPath) } text
					("the directory in which to generate the files")
			cmd("validate") action { (_, c) => c.copy(command = Command.Verify) } text
					("only validate the .idl file") children(
				arg[File]("file") action { (x, c) => c.copy(idlFile = x.toPath) } text
						("the .idl file that describes the interfaces")
			)
			cmd("generate") action { (_, c) => c.copy(command = Command.Generate) } text
					("generate IPC shims") children(
				arg[File]("file") action { (x, c) => c.copy(idlFile = x.toPath) } text
						("the .idl file that describes the interfaces"),
				arg[String]("parent-lang") action { (x, c) => c.copy(parentLang = x) } text
						("the language in which the grader/validator is written"),
				arg[String]("child-lang") action { (x, c) => c.copy(childLang = x) } text
						("the language in which the submission is written"),
				opt[Unit]("makefile") action { (_, c) => c.copy(makefile = true) } text
						("also generate a Makefile"),
				opt[Unit]("pipe-dirs") action { (_, c) => c.copy(pipeDirectories = true) } text
						("use separate directories for each pipe"),
				opt[Unit]("sequential-ids") action { (_, c) => c.copy(sequentialIds = true) } text
						("use sequential (instead of random) IDs for functions"),
				opt[Unit]("verbose") action { (_, c) => c.copy(verbose = true) } text
						("add verbose logging information to the generated shims")
			)
			checkConfig { c => {
				if (c.idlFile == null)
					failure("An .idl file must be chosen.")
				else
					success
			}}
		}

		optparse.parse(args, Options()) map { rawOptions => {
			val fileName = rawOptions.idlFile.getName(rawOptions.idlFile.getNameCount - 1).toString
			val extPos = fileName.lastIndexOf('.')
			val options = (if (extPos == -1) {
				rawOptions.copy(moduleName = fileName)
			} else {
				rawOptions.copy(moduleName = fileName.substring(0, extPos))
			})

			if (options.parentLang == "pas") {
				System.err.println("Use of `pas' as parent language is not supported")
				System.exit(1);
			}

			val parser = new Parser
			val idl = parser.parse(Source.fromFile(options.idlFile.toFile).mkString)
			options.command match {
				case Command.Generate => {
					new OutputDirectory(Paths.get(".")).install(options.outputDirectory)
					val problemsetter = options.idlFile.resolve(
						s"../${idl.main.name}.${options.parentLang}").normalize
					val contestant = options.idlFile.resolve(
						s"../${options.moduleName}.${options.childLang}").normalize

					Generator.generate(idl, options, problemsetter, contestant).foreach(
						_.install(options.outputDirectory))
				}
				case Command.Verify =>
					System.out.println("OK")
			}
		}} getOrElse {
			System.exit(1)
		}
	}
}

/* vim: set noexpandtab: */
