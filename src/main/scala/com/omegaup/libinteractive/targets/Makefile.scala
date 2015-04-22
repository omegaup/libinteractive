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
			path = options.root.resolve(s"${options.moduleName}.cbp"),
			contents = templates.code.codeblocks_unix(message, this,
				runPath = relativeToRoot(options.outputDirectory.resolve(Paths.get("run"))),
				debugExecutable = rules.find(_.debug).map(rule =>
					relativeToRoot(options.outputDirectory.resolve(rule.target))).get,
				resolvedLinks = resolvedLinks,
				sampleFiles = options.sampleFiles,
				parentFile = relativeToRoot(Paths.get(s"${idl.main.name}.${options.parentLang}")),
				moduleName = options.moduleName,
				extension = extension).toString,
			relative = false)
	}

	private def generateCodeBlocksWindowsProject(extension: String): OutputFile = {
		OutputFile(
			path = options.root.resolve(s"${options.moduleName}.cbp"),
			contents = templates.code.codeblocks_windows(message, this,
				runPath = relativeToRoot(options.outputDirectory.resolve(Paths.get("run.exe"))),
				makefilePath = relativeToRoot(options.outputDirectory.resolve(Paths.get("Makefile"))),
				debugExecutable = rules.find(_.debug).map(rule =>
					relativeToRoot(options.outputDirectory.resolve(rule.target))).get,
				resolvedLinks = resolvedLinks,
				sampleFiles = options.sampleFiles,
				parentFile = relativeToRoot(Paths.get(s"${idl.main.name}.${options.parentLang}")),
				moduleName = options.moduleName,
				extension = extension).toString,
			relative = false)
	}

	private def generateLazarusUnixProject(extension: String): OutputFile = {
		OutputFile(
			path = options.root.resolve(s"${options.moduleName}.lpi"),
			contents = templates.code.lazarus_unix(message, this,
				runPath = relativeToRoot(options.outputDirectory.resolve(Paths.get("run"))),
				makefilePath = relativeToRoot(Paths.get("Makefile")),
				sampleFiles = options.sampleFiles,
				parentFile = relativeToRoot(Paths.get(s"${idl.main.name}.${options.parentLang}")),
				moduleName = options.moduleName,
				extension = extension).toString,
			relative = false)
	}

	private def generateLazarusWindowsProject(extension: String): OutputFile = {
		OutputFile(
			path = options.root.resolve(s"${options.moduleName}.lpi"),
			contents = templates.code.lazarus_windows(message, this,
				runPath = relativeToRoot(options.outputDirectory.resolve(Paths.get("run.exe"))),
				makefilePath = relativeToRoot(options.outputDirectory.resolve(Paths.get("Makefile"))),
				sampleFiles = options.sampleFiles,
				parentFile = relativeToRoot(Paths.get(s"${idl.main.name}.${options.parentLang}")),
				moduleName = options.moduleName,
				extension = extension).toString,
			relative = false)
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
				Paths.get("run"),
				List(outputResolve("run.c")),
				Compiler.Gcc, "-std=c11 -o $@ -lrt $^ -O2 -D_XOPEN_SOURCE=600 " +
				"-D_BSD_SOURCE -Wall"))).map(resolve)
		val makefile = templates.code.makefile_unix(message,
			allRules = allRules,
			allExecutables = allRules.map(_.target).mkString(" "),
			runPath = relativeToRoot(options.outputDirectory.resolve(Paths.get("run"))),
			sampleFiles = options.sampleFiles)

		OutputFile(options.root.resolve("Makefile"), makefile.toString, false)
	}

	private def generateMakefileWindowsContents() = {
		val allRules = (rules ++ List(
			MakefileRule(
				Paths.get("run.exe"),
				List(outputResolve("run.c")),
				Compiler.Gcc, "-std=c11 -o $@ $^ -O2 -lpsapi -Wall"))
		).map(resolve)
		val makefile = templates.code.makefile_windows(message,
			allRules = allRules,
			allExecutables = allRules.map(_.target).mkString(" "),
			runPath = relativeToRoot(options.outputDirectory.resolve(Paths.get("run"))),
			resolvedLinks = resolvedLinks,
			sampleFiles = options.sampleFiles)

		OutputFile(Paths.get("Makefile"), makefile.toString)
	}

	private def generateBatchFileContents() = {
		val allRules = (rules ++ List(
				MakefileRule(
					Paths.get("run.exe"),
					List(outputResolve("run.c")),
					Compiler.Gcc, "-std=c99 -o $@ $^ -O2 -lpsapi -Wall"))).map(resolve)

		val runbat = templates.code.runbat(this, message, allRules, resolvedLinks, options)

		List(
			OutputFile(options.root.resolve("run.bat"), runbat.toString, false),
			OutputFile(options.root.resolve("test.bat"), s"@ECHO OFF\r\nREM $message\r\n\r\n" +
				s"""run.bat ${options.sampleFiles.map("\"" + _ + "\"").mkString(" ")}""", false)
		)
	}

	private def resolve(rule: MakefileRule) = {
		new MakefileRule(
			relativeToRoot(options.outputDirectory.resolve(rule.target)),
			rule.requisites.map(relativeToRoot),
			rule.compiler, rule.params)
	}

	private def generateRunDriver() = {
		val rundriver = templates.code.rundriver_unix(this, message, idl, commands,
			numProcesses = commands.foldLeft(0)((length, _) => length + 1),
			maxCommandLength = commands.foldLeft(0)((length, exec) =>
				Math.max(length, exec.args.length)) + 1,
			maxDebugCommandLength = commands.foldLeft(0)((length, exec) =>
				Math.max(length, exec.debug_args.getOrElse(exec.args).length)) + 1,
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
