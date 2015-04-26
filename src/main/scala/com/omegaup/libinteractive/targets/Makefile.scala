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
			case OS.Unix => generateUnixIdeProject ++
					List(generateMakefileUnixContents, generateRunDriver)
			case OS.Windows => generateWindowsIdeProject ++
					generateBatchFileContents ++
					List(generateMakefileWindowsContents, generateRunDriverWindows)
		}
	}

	private def generateCodeBlocksUnixProject(extension: String): OutputFile = {
		OutputFile(
			path = options.rootResolve(s"${options.moduleName}.cbp"),
			contents = templates.code.codeblocks_unix(message, this,
				runPath = options.relativeToRoot("run"),
				debugExecutable = rules.find(_.debug).map(_.target).get,
				resolvedLinks = resolvedLinks,
				sampleFiles = options.sampleFiles,
				parentFile = Paths.get(s"${idl.main.name}.${options.parentLang}"),
				moduleName = options.moduleName,
				extension = extension).toString
			)
	}

	private def generateCodeBlocksWindowsProject(extension: String): OutputFile = {
		OutputFile(
			path = options.rootResolve(s"${options.moduleName}.cbp"),
			contents = templates.code.codeblocks_windows(message, this,
				runPath = options.relativeToRoot("run.exe"),
				makefilePath = options.relativeToRoot("Makefile"),
				debugExecutable = rules.find(_.debug).map(_.target).get,
				resolvedLinks = resolvedLinks,
				sampleFiles = options.sampleFiles,
				parentFile = Paths.get(s"${idl.main.name}.${options.parentLang}"),
				moduleName = options.moduleName,
				extension = extension).toString
			)
	}

	private def generateLazarusUnixProject(extension: String): OutputFile = {
		OutputFile(
			path = options.rootResolve(s"${options.moduleName}.lpi"),
			contents = templates.code.lazarus_unix(message, this,
				runPath = options.relativeToRoot("run"),
				makefilePath = Paths.get("Makefile"),
				sampleFiles = options.sampleFiles,
				parentFile = Paths.get(s"${idl.main.name}.${options.parentLang}"),
				moduleName = options.moduleName,
				extension = extension).toString
			)
	}

	private def generateLazarusWindowsProject(extension: String): OutputFile = {
		OutputFile(
			path = options.rootResolve(s"${options.moduleName}.lpi"),
			contents = templates.code.lazarus_windows(message, this,
				runPath = options.resolve("run.exe"),
				makefilePath = options.relativize(options.resolve("Makefile")),
				sampleFiles = options.sampleFiles,
				parentFile = Paths.get(s"${idl.main.name}.${options.parentLang}"),
				moduleName = options.moduleName,
				extension = extension).toString
		)
	}

	private def generateUnixIdeProject(): Iterable[OutputFile] = {
		options.childLang match {
			case "c" => List(generateCodeBlocksUnixProject("c"))
			case "cpp" => List(generateCodeBlocksUnixProject("cpp"))
			case "pas" => List(generateLazarusUnixProject("pas"))
			case _ => List()
		}
	}

	private def generateWindowsIdeProject(): Iterable[OutputFile] = {
		options.childLang match {
			case "c" => List(generateCodeBlocksWindowsProject("c"))
			case "cpp" => List(generateCodeBlocksWindowsProject("cpp"))
			case "pas" => List(generateLazarusWindowsProject("pas"))
			case _ => List()
		}
	}

	private def generateMakefileUnixContents() = {
		val allRules = (rules ++ List(
			MakefileRule(
				options.relativeToRoot("run"),
				List(options.relativeToRoot("run.c")),
				Compiler.Gcc, "-std=c99 -o $@ -lrt $^ -O2 -D_XOPEN_SOURCE=600 " +
				"-D_BSD_SOURCE -Wall")))
		val makefile = templates.code.makefile_unix(message,
			allRules = allRules,
			allExecutables = allRules.map(_.target).mkString(" "),
			runPath = options.relativeToRoot("run"),
			sampleFiles = options.sampleFiles)

		OutputFile(options.rootResolve("Makefile"), makefile.toString)
	}

	private def generateMakefileWindowsContents() = {
		val allRules = (rules ++ List(
			MakefileRule(
				options.relativeToRoot("run.exe"),
				List(options.relativeToRoot("run.c")),
				Compiler.Gcc, "-std=c99 -o $@ $^ -O2 -lpsapi -Wall"))
		)
		val makefile = templates.code.makefile_windows(message,
			allRules = allRules,
			allExecutables = allRules.map(_.target).mkString(" "),
			runPath = options.relativeToRoot("run.exe"),
			resolvedLinks = resolvedLinks.map(
				link => new ResolvedOutputLink(
					options.relativize(link.link),
					options.relativize(link.target)
				)
			),
			sampleFiles = options.sampleFiles)

		OutputFile(options.resolve("Makefile"), makefile.toString)
	}

	private def generateBatchFileContents() = {
		val allRules = (rules ++ List(
				MakefileRule(
					options.relativeToRoot("run.exe"),
					List(options.relativeToRoot("run.c")),
					Compiler.Gcc, "-std=c99 -o $@ $^ -O2 -lpsapi -Wall")))

		val runbat = templates.code.runbat(this, message,
			allRules = allRules,
			resolvedLinks = resolvedLinks.map(
				link => new ResolvedOutputLink(
					options.relativize(link.link),
					options.relativize(link.target)
				)
			),
			options
		)

		List(
			OutputFile(options.rootResolve("run.bat"), runbat.toString),
			OutputFile(options.rootResolve("test.bat"), s"@ECHO OFF\r\nREM $message\r\n\r\n" +
				s"""run.bat ${options.sampleFiles.map("\"" + _ + "\"").mkString(" ")}""")
		)
	}

	private def generateRunDriver() = {
		val rundriver = templates.code.rundriver_unix(this, options, message, idl, commands,
			numProcesses = commands.foldLeft(0)((length, _) => length + 1),
			maxCommandLength = commands.foldLeft(0)((length, exec) =>
				Math.max(length, exec.args.length)) + 1,
			maxDebugCommandLength = commands.foldLeft(0)((length, exec) =>
				Math.max(length, exec.debug_args.getOrElse(exec.args).length)) + 1,
			maxEnvLength = commands.foldLeft(0)((length, exec) =>
				Math.max(length, exec.env.size)) + 1,
			maxNameLength = idl.allInterfaces.foldLeft(0)((length, interface) =>
				Math.max(length, interface.name.length)))

		OutputFile(options.resolve("run.c"), rundriver.toString)
	}

	private def generateRunDriverWindows() = {
		val rundriver = templates.code.rundriver_windows(this, message, idl, commands,
			numProcesses = commands.foldLeft(0)((length, _) => length + 1),
			maxNameLength = idl.allInterfaces.foldLeft(0)((length, interface) =>
				Math.max(length, interface.name.length)))

		OutputFile(options.resolve("run.c"), rundriver.toString)
	}

	override def extension() = ???
	override def generateMakefileRules() = ???
	override def generateRunCommands() = ???
	protected def generateTemplates(moduleName: String,
			interfacesToImplement: Iterable[Interface], callableModuleName: String,
			callableInterfaces: Iterable[Interface], input: Path) = ???
}

/* vim: set noexpandtab: */
