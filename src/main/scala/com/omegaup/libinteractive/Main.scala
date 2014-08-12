package com.omegaup.libinteractive

import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

import com.omegaup.libinteractive.idl.Parser
import com.omegaup.libinteractive.target.Options
import com.omegaup.libinteractive.target.C
import scala.io.Source

object Main {
	def main(args: Array[String]): Unit = {
		val optparse = new scopt.OptionParser[Options]("libinteractive") {
			head("libinteractive", "0.1")
			help("help") text("display this message")
			version("version") text("display version information")

			opt[File]("output-directory") action { (x, c) => c.copy(outputDirectory = x) } text
					("the directory in which to generate the files")
			cmd("generate") action { (_, c) => c } text ("generate IPC shims") children(
				arg[File]("file") action { (x, c) => c.copy(idlFile = x) } text
						("the .idl file that describes the interfaces"),
				arg[String]("parent-lang") action { (x, c) => c } text
						("the language in which the grader/validator is written"),
				arg[String]("child-lang") action { (x, c) => c } text
						("the language in which the submission is written"),
				opt[Unit]("sequential-ids") action { (_, c) => c.copy(sequentialIds = true) } text
						("use sequential (instead of random) IDs for functions"),
				opt[Unit]("verbose") action { (_, c) => c.copy(verbose = true) } text
						("add verbose logging information to the generated shims")
			)
		}

		optparse.parse(args, Options()) map { options => {
			val idl = Parser(Source.fromFile(options.idlFile).mkString)
			val target = new C(idl, options)
			for (output <- target.generateParent ++ target.generateChildren) {
				val targetFile = new File(options.outputDirectory, output.filename)
				val out = new BufferedWriter(new FileWriter(targetFile))
				out.write(output.contents)
				out.close
			}
		}} getOrElse {
			System.exit(1)
		}
	}
}

/* vim: set noexpandtab: */
