package com.omegaup.libinteractive

import java.io._

import com.omegaup.libinteractive.idl.Parser
import com.omegaup.libinteractive.target.Options
import com.omegaup.libinteractive.target.C
import scala.io.Source

object Main {
	def main(args: Array[String]) = {
		val options = new Options
		options.sequentialIds = true

		val idl = Parser(Source.fromFile(args(0)).mkString)
		val target = new C(idl, options)
		for (output <- target.generateParent ++ target.generateChildren) {
			val out = new BufferedWriter(new FileWriter(output.filename))
			out.write(output.contents)
			out.close
		}
	}
}

/* vim: set noexpandtab: */
