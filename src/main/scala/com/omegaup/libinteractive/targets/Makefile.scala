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
				debugExecutable = rules.find(_.debug).map(_.target).get.find(x => true).get,
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
				debugExecutable = rules.find(_.debug).map(_.target).get.find(x => true).get,
				resolvedLinks = resolvedLinks,
				sampleFiles = options.sampleFiles,
				parentFile = Paths.get(s"${idl.main.name}.${options.parentLang}"),
				moduleName = options.moduleName,
				extension = extension).toString
			)
	}

	private def generateVSCodeUnixProject(extension: String): List[OutputPath] = {
		List(
			OutputDirectory(
				path = options.rootResolve(".vscode")
			),
			OutputFile(
				path = options.rootResolve(".vscode/tasks.json"),
				contents = templates.code.vscode_tasks(
					message,
					allRules = allRulesUnix
				).toString
			),
			OutputFile(
				path = options.rootResolve(".vscode/launch.json"),
				contents = templates.code.vscode_launch(
					message,
					runPath = options.relativeToRoot("run"),
					debugExecutable = rules.find(_.debug).map(_.target).get.find(x => true).get
				).toString
			)
		)
	}

	private def generateVSCodeWindowsProject(extension: String): List[OutputPath] = {
		List(
			OutputDirectory(
				path = options.rootResolve(".vscode")
			),
			OutputFile(
				path = options.rootResolve(".vscode/tasks.json"),
				contents = templates.code.vscode_tasks(
					message,
					allRules = allRulesWindows
				).toString
			),
			OutputFile(
				path = options.rootResolve(".vscode/launch.json"),
				contents = templates.code.vscode_launch(
					message,
					runPath = options.relativeToRoot("run.exe"),
					debugExecutable = rules.find(_.debug).map(_.target).get.find(x => true).get
				).toString
			)
		)
	}

	private def generateReplitProject(extension: String): List[OutputPath] = {
		List(
			OutputDirectory(
				path = options.rootResolve("bin")
			),
			OutputFile(
				path = options.rootResolve("bin/dap-cpp-wrapper"),
				contents = templates.code.replit_dap_cpp_wrapper(message).toString
			),
			OutputFile(
				path = options.rootResolve("replit.nix"),
				contents = templates.code.replit_nix(message).toString
			),
			OutputFile(
				path = options.rootResolve(".replit"),
				contents = templates.code.replit(message, runPath = options.relativeToRoot("run"), debugExecutable = rules.find(_.debug).map(_.target).get.find(x => true).get).toString
			)
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

	private def generateUnixIdeProject(): Iterable[OutputPath] = {
		options.childLang match {
			case "c" => List(generateCodeBlocksUnixProject("c")) ++ generateVSCodeUnixProject("c")
			case "cpp" => List(generateCodeBlocksUnixProject("cpp")) ++ generateVSCodeUnixProject("cpp") ++ generateReplitProject("cpp")
			case "pas" => List(generateLazarusUnixProject("pas"))
			case _ => List()
		}
	}

	private def generateWindowsIdeProject(): Iterable[OutputPath] = {
		options.childLang match {
			case "c" => List(generateCodeBlocksWindowsProject("c")) ++ generateVSCodeWindowsProject("c")
			case "cpp" => List(generateCodeBlocksWindowsProject("cpp")) ++ generateVSCodeWindowsProject("cpp")
			case "pas" => List(generateLazarusWindowsProject("pas"))
			case _ => List()
		}
	}

	private def allRulesUnix = rules ++ List(
			MakefileRule(
				target = List(options.relativeToRoot("run")),
				requisites = List(options.relativeToRoot("run.c")),
				compiler = Compiler.Gcc,
				params = List("-o", "$@", "$^"),
				flags = List(
					"-std=c99", "-O2", "-D_XOPEN_SOURCE=600", "-D_DEFAULT_SOURCE",
					"-D_DARWIN_C_SOURCE", "-Wall"
				)
			)
		)

	private def allRulesWindows = rules ++ List(
			MakefileRule(
				target = List(options.relativeToRoot("run.exe")),
				requisites = List(options.relativeToRoot("run.c")),
				compiler = Compiler.Gcc,
				params = List("-o", "$@", "$^"),
				flags = List("-std=c99", "-O2", "-lpsapi", "-Wall")
			)
		)

	private def generateMakefileUnixContents() = {
		val makefile = templates.code.makefile_unix(message,
			allRules = allRulesUnix,
			allExecutables = allRulesUnix.flatMap(_.target).mkString(" "),
			runPath = options.relativeToRoot("run"),
			sampleFiles = options.sampleFiles)

		OutputFile(options.rootResolve("Makefile"), makefile.toString)
	}

	private def generateMakefileWindowsContents() = {
		val makefile = templates.code.makefile_windows(message,
			allRules = allRulesWindows,
			allExecutables = allRulesWindows.flatMap(_.target).mkString(" "),
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
		val compilebat = templates.code.compilebat(this, message,
			allRules = allRulesWindows,
			resolvedLinks = resolvedLinks.map(
				link => new ResolvedOutputLink(
					options.relativize(link.link),
					options.relativize(link.target)
				)
			),
			options
		)

		List(
			OutputFile(options.rootResolve("run.bat"),
				templates.code.runbat(message, options).toString),
			OutputFile(options.rootResolve("compile.bat"), compilebat.toString),
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
			maxNameLength = idl.allInterfaces.foldLeft("libinteractive".length)((length, interface) =>
				Math.max(length, interface.name.length)))

		OutputFile(options.resolve("run.c"), rundriver.toString)
	}

	private def generateRunDriverWindows() = {
		val rundriver = templates.code.rundriver_windows(this, message, idl, commands,
			numProcesses = commands.foldLeft(0)((length, _) => length + 1),
			maxNameLength = idl.allInterfaces.foldLeft("libinteractive".length)((length, interface) =>
				Math.max(length, interface.name.length)))

		OutputFile(options.resolve("run.c"), rundriver.toString)
	}

	override def extension() = ???
	override def generateInterface(interface: Interface) = ???
	override def generateMakefileRules() = ???
	override def generateMakefileRules(interface: Interface) = ???
	override def generateRunCommands() = ???
	override def generateRunCommand(interface: Interface) = ???
	override def generateTemplateSource() = ???
}

/* vim: set noexpandtab: */
