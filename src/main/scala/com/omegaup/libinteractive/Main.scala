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
import scala.collection.JavaConversions.iterableAsScalaIterable

object Main {
	def main(args: Array[String]): Unit = {
		val optparse = new scopt.OptionParser[Options]("libinteractive") {
			head("libinteractive", Main.getClass.getPackage.getImplementationVersion)
			help("help") text("display this message")
			version("version") text("display version information")

			opt[File]("output-directory") action
					{ (x, c) => c.copy(outputDirectory = x.toPath) } text
					("the directory in which to generate the files")
			cmd("validate") action { (_, c) => c.copy(command = Command.Validate) } text
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
				opt[Unit]("force") action { (x, c) => c.copy(force = true) } text
						("overwrites files if needed"),
				opt[Unit]("legacy") action { (x, c) => c.copy(legacyFlags = true) } text
						("use flags for older compilers"),
				opt[Unit]("makefile") action { (_, c) => c.copy(makefile = true) } text
						("also generate a Makefile"),
				opt[Unit]("pipe-dirs") action { (_, c) => c.copy(pipeDirectories = true) } text
						("use separate directories for each pipe"),
				opt[String]("sample-file") action { (x, c) => c.copy(sampleFiles = (c.sampleFiles ++ List(x))) } text
						("one of the automated test cases provided with the task"),
				opt[Unit]("sequential-ids") action
						{ (_, c) => c.copy(sequentialIds = true) } text
						("use sequential (instead of random) IDs for functions"),
				opt[Unit]("template") action
						{ (_, c) => c.copy(generateTemplate = true) } text
						("also generate a template for the contestant"),
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
			checkConfig { c => {
				if (c.idlFile == null)
					failure("An .idl file must be chosen.")
				else
					success
			}}
		}

		// To avoid always having to pass in the --windows flag in Windows.
		val defaultOS = (if (System.getProperty("os.name").startsWith("Windows")) {
			OS.Windows
		} else {
			OS.Unix
		})
		optparse.parse(args, Options(os = defaultOS)) map { rawOptions => {
			val fileName =
					rawOptions.idlFile.getName(rawOptions.idlFile.getNameCount - 1).toString
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
			val idl: IDL = try {
				parser.parse(Source.fromFile(options.idlFile.toFile).mkString)
			} catch {
				case e: ParseException => {
					System.err.println(e)
					System.exit(1)
					null
				}
			}
			options.command match {
				case Command.Generate => {
					val installer = new InstallVisitor(options.outputDirectory, options.root)
					val problemsetter = options.idlFile.resolve(
						s"../${idl.main.name}.${options.parentLang}").normalize
					val contestant = options.idlFile.resolve(
						s"../${options.moduleName}.${options.childLang}").normalize

					installer.apply(new OutputDirectory(Paths.get(".")))
					Generator.generate(idl, options, problemsetter, contestant)
						.foreach(installer.apply)
				}
				case Command.GenerateAll => {
					val supportedLanguages = List("c", "cpp", "java", "py", "pas")
					val candidates = supportedLanguages.map(
						lang => (lang, options.idlFile.resolve(
							s"../${idl.main.name}.${lang}").normalize)
					).filter(x => Files.exists(x._2))

					if (candidates.isEmpty) {
						System.err.println(
							s"""${idl.main.name}.{${supportedLanguages.mkString(",")}} not found""")
						System.exit(1)
					} else if (candidates.length > 1) {
						System.err.println(s"Multiple parent files found")
						System.exit(1)
					}

					val finalOptions = options.copy(
						legacyFlags = true,
						parentLang = candidates(0)._1,
						parentSource = Some({
							val distribPath = options.idlFile.resolve(
								s"../${idl.main.name}.distrib.${candidates(0)._1}").normalize
							if (Files.exists(distribPath)) {
								distribPath
							} else {
								candidates(0)._2
							}
						})
					)

					val problemsetter = Paths.get(
						s"${idl.main.name}.${finalOptions.parentLang}")
					val problemsetterSource = Source.fromFile((finalOptions.parentSource match {
							case None => {
								problemsetter
							}
							case Some(path) => path
					}).toFile).mkString

					val examplesPath = options.idlFile.resolve(
						s"../examples").normalize
					val examples = (if (Files.isDirectory(examplesPath)) {
						val stream = Files.newDirectoryStream(examplesPath)

						List(OutputDirectory(Paths.get("examples"), true)) ++
						stream.flatMap(entry => {
							val name = entry.getName(entry.getNameCount - 1).toString
							if (name.endsWith(".in")) {
								Some(OutputFile(
									Paths.get("examples", name),
									Source.fromFile(entry.toFile).mkString,
									false
								))
							} else {
								None
							}
						})
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
								s"${finalOptions.moduleName}.${lang}")

							val outputs = Generator.generate(idl, localOptions, problemsetter,
								contestant)

							val visitor = os match {
								case OS.Windows => new ZipVisitor(finalOptions.outputDirectory,
									finalOptions.packageDirectory.resolve(
										s"${finalOptions.packagePrefix}windows_${lang}.zip"))
								case OS.Unix => new CompressedTarballVisitor(finalOptions.outputDirectory,
									finalOptions.packageDirectory.resolve(
										s"${finalOptions.packagePrefix}unix_${lang}.tar.bz2"))
							}
							examples.foreach(visitor.apply)
							try {
								visitor.apply(OutputFile(problemsetter, problemsetterSource, false))
								outputs.foreach(visitor.apply)
							} finally {
								visitor.close
							}
						}
					}
				}
				case Command.Validate =>
					System.out.println("OK")
			}
		}} getOrElse {
			System.exit(1)
		}
	}
}

/* vim: set noexpandtab: */
