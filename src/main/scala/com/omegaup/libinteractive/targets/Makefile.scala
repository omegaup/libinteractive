// Copyright (c) 2014 The omegaUp Contributors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.omegaup.libinteractive.target

import java.nio.file.Path
import java.nio.file.Paths

import com.omegaup.libinteractive.idl.IDL
import com.omegaup.libinteractive.idl.Interface
import com.omegaup.libinteractive.templates

class Makefile(idl: IDL, rules: Iterable[MakefileRule],
		commands: Iterable[ExecDescription], resolvedLinks: Iterable[ResolvedOutputLink],
		options: Options) extends Target(idl, options) {
	override def generate() = {
		options.os match {
			case OS.Unix => List(generateMakefileContents, generateRunDriver)
			case OS.Windows => generateBatchFileContents ++ List(generateRunDriverWindows)
		}
	}

	private def generateMakefileContents() = {
		val allRules = (rules ++ List(
			MakefileRule(
				Paths.get("run"),
				List(Paths.get("run.c")),
				Compiler.Gcc, "-std=c99 -o $@ -lrt $^ -O2 -D_XOPEN_SOURCE=600 " +
				"-D_BSD_SOURCE -Wall"))).map(resolve)
		val makefile = templates.code.makefile(message,
			allRules = allRules,
			allExecutables = allRules.map(_.target).mkString(" "),
			runPath = relativeToRoot(options.outputDirectory.resolve(Paths.get("run"))))

		OutputFile(options.root.resolve("Makefile"), makefile.toString, false)
	}

	private def generateBatchFileContents() = {
		val allRules = (rules ++ List(
				MakefileRule(
					Paths.get("run.exe"),
					List(Paths.get("run.c")),
					Compiler.Gcc, "-std=c99 -o $@ $^ -O2 -lpsapi -Wall"))).map(resolve)

		val runbat = templates.code.runbat(this, message, allRules, resolvedLinks, options)

		List(
			OutputFile(options.root.resolve("run.bat"), runbat.toString, false),
			OutputFile(options.root.resolve("test.bat"), s"@ECHO OFF\nREM $message\n\n" +
				"run.bat < examples\\sample.in", false)
		)
	}

	private def resolve(rule: MakefileRule) = {
		new MakefileRule(
			relativeToRoot(options.outputDirectory.resolve(rule.target)),
			rule.requisites.map(path => relativeToRoot(
					options.outputDirectory.resolve(path))),
			rule.compiler, rule.params)
	}

	private def generateRunDriver() = {
		val rundriver = templates.code.rundriver_unix(this, message, idl, commands,
			numProcesses = commands.foldLeft(0)((length, _) => length + 1),
			maxCommandLength = commands.foldLeft(0)((length, exec) =>
				Math.max(length, exec.args.length)) + 1,
			maxEnvLength = commands.foldLeft(0)((length, exec) =>
				Math.max(length, exec.env.size)) + 1,
			maxNameLength = idl.allInterfaces.foldLeft(0)((length, interface) =>
				Math.max(length, interface.name.length)))

		OutputFile(Paths.get("run.c"), rundriver.toString)
	}

	private def generateRunDriverWindows() = {
		val rundriver = templates.code.rundriver_windows(this, message, idl, commands,
			numProcesses = commands.foldLeft(0)((length, _) => length + 1),
			maxNameLength = idl.allInterfaces.foldLeft(0)((length, interface) =>
				Math.max(length, interface.name.length)))

		OutputFile(Paths.get("run.c"), rundriver.toString)
	}

	override def extension() = ???
	override def generateMakefileRules() = ???
	override def generateRunCommands() = ???
	protected def generateTemplates(moduleName: String,
			interfacesToImplement: Iterable[Interface], callableModuleName: String,
			callableInterfaces: Iterable[Interface], input: Path) = ???
}

/* vim: set noexpandtab: */
