// Copyright (c) 2014 The omegaUp Contributors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.omegaup.libinteractive

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

import com.omegaup.libinteractive.idl.IDL
import com.omegaup.libinteractive.idl.Parser
import com.omegaup.libinteractive.idl.ParseException
import com.omegaup.libinteractive.target._
import scala.io.Source
import scala.io.StdIn
import scala.collection.JavaConversions.iterableAsScalaIterable

object Main {
	def main(args: Array[String]): Unit = {
		val optparse = new scopt.OptionParser[Options]("libinteractive") {
			head(BuildInfo.name, BuildInfo.version)
			help("help") text("display this message")
			version("version") text("display version information")

			opt[File]("output-directory") action
					{ (x, c) => c.copy(root = x.toPath.toAbsolutePath) } text
					("the directory in which to generate the files")
			cmd("validate") action { (_, c) => c.copy(command = Command.Validate) } text
					("only validate the .idl file") children(
				arg[File]("file") action { (x, c) => c.copy(idlFile = x.toPath) } text
						("the .idl file that describes the interfaces"),
				opt[Unit]("metadata") action { (x, c) => c.copy(metadata = true) } text
						("print metadata about the .idl file")
			)
			cmd("generate") action { (_, c) => c.copy(command = Command.Generate) } text
					("generate IPC shims") children(
				arg[File]("file") action { (x, c) => c.copy(idlFile = x.toPath) } text
						("the .idl file that describes the interfaces"),
				arg[String]("parent-lang") action { (x, c) => c.copy(parentLang = x) } text
						("the language in which the grader/validator is written"),
				arg[String]("child-lang") action { (x, c) => c.copy(childLang = x) } text
						("the language in which the submission is written"),
				opt[Unit]("force") action { (x, c) => c.copy(force = true) } text
						("overwrites files if needed"),
				opt[File]("library-directory") action { (x, c) => c.copy(libraryDirectory = x.toPath) } text
						("the directory in which the intermediate files will be stored. Defaults to 'libinteractive'"),
				opt[Unit]("legacy") action { (x, c) => c.copy(legacyFlags = true) } text
						("use flags for older compilers"),
				opt[Unit]("makefile") action { (_, c) => c.copy(makefile = true) } text
						("also generate a Makefile"),
				opt[Unit]("pipe-dirs") action { (_, c) => c.copy(pipeDirectories = true) } text
						("use separate directories for each pipe"),
				opt[Unit]("avoid-original-sources") action { (_, c) => c.copy(preferOriginalSources = false) } text
						("do not try to use the source files located next to the .idl. Instead, expect the sources to be present in the per-interface directory"),
				opt[String]("sample-file") action { (x, c) => c.copy(sampleFiles = (c.sampleFiles ++ List(x))) } text
						("one of the automated test cases provided with the task"),
				opt[Unit]("sequential-ids") action
						{ (_, c) => c.copy(sequentialIds = true) } text
						("use sequential (instead of random) IDs for functions"),
				opt[Unit]("template") action
						{ (_, c) => c.copy(generateTemplate = true) } text
						("also generate a template for the contestant"),
				opt[Unit]("transact") action { (_, c) => c.copy(transact = true) } text
						("use shared memory and the transact kernel module (experimental)"),
				opt[Unit]("unix") action { (_, c) => c.copy(os = OS.Unix) } text
						("generates code that can be run on Linux/Mac OSX"),
				opt[Unit]("verbose") action { (_, c) => c.copy(verbose = true) } text
						("add verbose logging information to the generated shims"),
				opt[Unit]("windows") action { (_, c) => c.copy(os = OS.Windows) } text
						("generates code that can be run on Microsoft Windows")
			)
			cmd("generate-all") action { (_, c) => c.copy(command = Command.GenerateAll) } text
				("generate templates and pack them up for all language/OS combinations") children(
					arg[File]("file") action { (x, c) => c.copy(idlFile = x.toPath) } text
						("the .idl file that describes the interfaces"),
					opt[File]("package-directory") action { (x, c) => c.copy(packageDirectory = x.toPath) } text
						("the directory in which the packaged templates are to be saved"),
					opt[String]("package-prefix") action { (x, c) => c.copy(packagePrefix = x) } text
						("the prefix of the generated packages")
			)
			cmd("json") action { (_, c) => c.copy(command = Command.Json) } text
				("generate a JSON-encoded runtime metadata + output for all languages") children(
					arg[File]("file") optional() action { (x, c) => c.copy(idlFile = x.toPath) } text
						("the .idl file that describes the interfaces. Will use stdin if empty."),
					opt[String]("module-name") action { (x, c) => c.copy(moduleName = x) } text
						("the name of the .idl module if input comes from stdin"),
					opt[String]("parent-lang") action { (x, c) => c.copy(parentLang = x) } text
						("the language in which the grader/validator is written"),
					opt[Unit]("omit-debug-targets") action { (x, c) => c.copy(generateDebugTargets = false) } text
						("omit debug Makefile targets")
			)
			checkConfig { c => {
				if (c.command == Command.Json) {
					if (c.idlFile == null && (c.moduleName == null || c.parentLang == null))
						failure("Must specify both --module-name and --parent-lang when reading from stdin")
					else if (c.idlFile != null && (c.moduleName != null || c.parentLang != null))
						failure("Cannot specify both a file and --module-name or --parent-lang")
					else
						success
				} else if (c.idlFile == null) {
					failure("An .idl file must be chosen.")
				} else {
					success
				}
			}}
		}

		// To avoid always having to pass in the --windows flag in Windows.
		val defaultOS = (if (System.getProperty("os.name").startsWith("Windows")) {
			OS.Windows
		} else {
			OS.Unix
		})
		optparse.parse(args, Options(os = defaultOS)) map { rawOptions => {
			if (rawOptions.os == OS.Windows) {
				System.setProperty("line.separator", "\r\n")
			}

			if (rawOptions.parentLang == "pas") {
				System.err.println("Use of `pas' as parent language is not supported")
				System.exit(1);
			}

			var idlSource: String = null
			val options = (if (rawOptions.idlFile != null) {
				val fileName =
						rawOptions.idlFile.getName(rawOptions.idlFile.getNameCount - 1).toString
				val extPos = fileName.lastIndexOf('.')
				idlSource = Source.fromFile(rawOptions.idlFile.toFile).mkString
				if (extPos == -1) {
					rawOptions.copy(moduleName = fileName)
				} else {
					rawOptions.copy(moduleName = fileName.substring(0, extPos))
				}
			} else {
				idlSource = Iterator.continually(StdIn.readLine).takeWhile(_ != null).mkString("\n")
				rawOptions
			})

			val parser = new Parser
			val idl: IDL = try {
				parser.parse(idlSource)
			} catch {
				case e: ParseException => {
					System.err.println(e)
					System.exit(1)
					null
				}
			}
			options.command match {
				case Command.Generate => {
					val installer = new InstallVisitor
					val problemsetter = options.idlFile.resolveSibling(
						s"${idl.main.name}.${options.parentLang}").normalize
					val contestant = options.idlFile.resolveSibling(
						s"${options.moduleName}.${options.childLang}").normalize

					installer.apply(OutputDirectory(options.root))
					Generator.generate(idl, options, problemsetter, contestant)
						.foreach(installer.apply)
				}
				case Command.GenerateAll => {
					val supportedLanguages = List("c", "cpp", "java", "py", "pas")
					val candidates = supportedLanguages.map(
						lang => (lang, options.idlFile.resolveSibling(
							s"${idl.main.name}.${lang}").normalize)
					).filter(x => Files.exists(x._2))

					if (candidates.isEmpty) {
						System.err.println(
							s"""${idl.main.name}.{${supportedLanguages.mkString(",")}} not found""")
						System.exit(1)
					} else if (candidates.length > 1) {
						System.err.println(s"Multiple parent files found")
						System.exit(1)
					}

					val examplesPath = options.idlFile.resolveSibling("examples").normalize
					val examples = (if (Files.isDirectory(examplesPath)) {
						val stream = Files.newDirectoryStream(examplesPath)

						stream.flatMap(entry => {
							val name = entry.getName(entry.getNameCount - 1).toString
							if (name.endsWith(".in")) {
								Some(entry)
							} else {
								None
							}
						})
					} else {
						List()
					}).toList.sortWith(_.getFileName.toString < _.getFileName.toString)

					val finalOptions = options.copy(
						legacyFlags = true,
						parentLang = candidates(0)._1,
						parentSource = Some({
							val distribPath = options.idlFile.resolveSibling(
								s"${idl.main.name}.distrib.${candidates(0)._1}").normalize
							if (Files.exists(distribPath)) {
								distribPath
							} else {
								candidates(0)._2
							}
						}),
						sampleFiles = examples.map(entry => s"examples/${entry.getFileName}").toList
					)

					val problemsetter = Paths.get(
						s"${idl.main.name}.${finalOptions.parentLang}")
					val problemsetterSource = Source.fromFile((finalOptions.parentSource match {
							case None => {
								problemsetter
							}
							case Some(path) => path
					}).toFile).mkString

					var exampleOutputs = (if (!examples.isEmpty) {
						List(OutputDirectory(finalOptions.rootResolve("examples"))) ++
						examples.map(entry =>
							OutputFile(
								finalOptions.rootResolve("examples", entry.getFileName.toString),
								Source.fromFile(entry.toFile).mkString
							)
						)
					} else {
						List()
					})

					for (os <- OS.values) {
						for (lang <- supportedLanguages) {
							val localOptions = finalOptions.copy(
								childLang = lang,
								os = os,
								makefile = true,
								generateTemplate = true
							)
							val contestant = Paths.get(
								s"${localOptions.moduleName}.${lang}")

							val distribPath = options.idlFile.resolveSibling(
								s"${options.moduleName}.distrib.${lang}").normalize
							val replacements = (if (Files.exists(distribPath)) {
								Some(OutputFile(
									Paths.get(s"${options.moduleName}.${lang}"),
									Source.fromFile(distribPath.toFile).mkString
								))
							} else {
								None
							}).toList

							val outputs = Generator.generate(idl, localOptions,
								problemsetter, contestant) ++ exampleOutputs ++
							List(OutputFile(problemsetter, problemsetterSource))

							val visitor = os match {
								case OS.Windows => new ZipVisitor(localOptions.root,
									localOptions.packageDirectory.resolve(
										s"${localOptions.packagePrefix}windows_${lang}.zip"))
								case OS.Unix => new CompressedTarballVisitor(
									localOptions.root.resolve(localOptions.moduleName),
									localOptions.packageDirectory.resolve(
										s"${localOptions.packagePrefix}unix_${lang}.tar.bz2"))
							}
							try {
								outputs
									.flatMap(new ReplacementFilter(replacements).apply)
									.foreach(visitor.apply)
							} finally {
								visitor.close
							}
						}
					}
				}
				case Command.Json => {
					val supportedLanguages = List("c", "cpp", "java", "py", "pas")
					val (parentLang, parentSource) = (if (options.idlFile != null) {
						val candidates = supportedLanguages.map(
							lang => (lang, options.idlFile.resolveSibling(
								s"${idl.main.name}.${lang}").normalize)
						).filter(x => Files.exists(x._2))

						if (candidates.isEmpty) {
							System.err.println(
								s"""${idl.main.name}.{${supportedLanguages.mkString(",")}} not found""")
							System.exit(1)
						} else if (candidates.length > 1) {
							System.err.println(s"Multiple parent files found")
							System.exit(1)
						}
						candidates(0)
					} else {
						(options.parentLang, Paths.get("${idl.main.name}.${options.parentLang}"))
					})

					val finalOptions = options.copy(
						libraryDirectory = Paths.get(""),
						pipeDirectories = true,
						parentLang = parentLang,
						parentSource = Some(parentSource),
						preferOriginalSources = false
					)

					val problemsetter = Paths.get(s"${idl.main.name}.${finalOptions.parentLang}")
					val interfaces = idl.childInterfaces.map({interface => (interface.name, {
						supportedLanguages.map({lang => (lang, {
							val localOptions = finalOptions.copy(
								childLang = lang
							)
							val contestant = Paths.get(s"${localOptions.moduleName}.${lang}")
							Generator.generateInterface(idl, localOptions, contestant,
								lang, interface)
						})}).toMap
					})}).toMap ++ Map(idl.main.name -> Map(
						finalOptions.parentLang -> Generator.generateInterface(idl,
							finalOptions, problemsetter, finalOptions.parentLang, idl.main)))
					System.out.println(templates.code.json_metadata(
						moduleName = options.moduleName,
						main = idl.main.name,
						interfaces = interfaces,
						parentLang = finalOptions.parentLang
					))
				}
				case Command.Validate => {
					System.out.println("OK")
				}
			}
		}} getOrElse {
			System.exit(1)
		}
	}
}

/* vim: set noexpandtab: */
