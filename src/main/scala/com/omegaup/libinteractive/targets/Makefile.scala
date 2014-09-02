// Copyright (c) 2014 The omegaUp Contributors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.omegaup.libinteractive.target

import java.nio.file.Path
import java.nio.file.Paths

import com.omegaup.libinteractive.idl.IDL
import com.omegaup.libinteractive.idl.Interface

class Makefile(idl: IDL, rules: Iterable[MakefileRule],
		commands: Iterable[ExecDescription], options: Options)
		extends Target(idl, options) {
	override def generate() = {
		List(generateMakefileContents, generateRunDriver)
	}

	private def generateMakefileContents() = {
		val builder = new StringBuilder
		val allRules = (rules ++ List(
				MakefileRule(
					Paths.get("run"),
					List(Paths.get("run.c")),
					"/usr/bin/gcc -std=c99 -o $@ -lrt $^ -O2 -D_XOPEN_SOURCE=600 " +
					"-D_BSD_SOURCE -Wall"))).map(resolve)
		val allExecutables = allRules.map(_.target).mkString(" ")

		builder ++= s"""# $message

all: $allExecutables

${allRules.map(
	rule => s"${rule.target}: ${rule.requisites.mkString(" ")}\n\t${rule.command}\n"
).mkString("\n")}
run: $allExecutables
	@${options.root.relativize(options.outputDirectory.resolve(Paths.get("run")))}
"""

		OutputMakefile(options.root.resolve("Makefile"), builder.mkString)
	}

	private def resolve(rule: MakefileRule) = {
		new MakefileRule(
			options.root.relativize(options.outputDirectory.resolve(rule.target)),
			rule.requisites.map(path => options.root.relativize(
					options.outputDirectory.resolve(path))),
			rule.command)
	}

	private def generateRunDriver() = {
		val builder = new StringBuilder
		val numProcesses = commands.foldLeft(0)((length, _) => length + 1)
		val maxCommandLength = commands.foldLeft(0)((length, exec) =>
				Math.max(length, exec.args.length)) + 1
		val maxEnvLength = commands.foldLeft(0)((length, exec) =>
				Math.max(length, exec.env.size)) + 1
		val maxNameLength = idl.allInterfaces.foldLeft(0)((length, interface) =>
			Math.max(length, interface.name.length))
		builder ++= s"""/* $message */
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/resource.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

const char* interfaces[] = {
	${idl.allInterfaces.map('"' + _.name + '"').mkString(", ")}
};
const char* pipeNames[] = {
	${idl.allInterfaces.map('"' + pipeFilename(_) + '"').mkString(", ")}
};
char* const commands[][$maxCommandLength] = {
${commands.map(command =>
	"\t{" + command.args.map('"' + _ + '"')
		.padTo(maxCommandLength, "NULL")
		.mkString(", ") +
	"}").mkString(",\n")}
};
char* const envs[][$maxEnvLength] = {
${commands.map(command =>
	"\t{" + command.env.map(env => {'"' + env._1 + '=' + env._2 + '"'}).toList
		.padTo(maxEnvLength, "NULL")
		.mkString(", ") +
	"}").mkString(",\n")}
};

static void writefull(int fd, const void* buf, size_t count) {
	ssize_t bytes;
	while (count > 0) {
		bytes = write(fd, buf, count);
		if (bytes <= 0) {
			fprintf(stderr, "Incomplete message missing %zu bytes\\n", count);
			exit(1);
		}
		buf = bytes + (char*)buf;
		count -= bytes;
	}
}

typedef struct {
	int fd;
	int closed;
	int pos;
	char buf[1024];
} buffer;

buffer buffers[${2 * numProcesses}];
int pids[$numProcesses] = {0};

void execute(int i) {
	int localpipes[4];
	if (pipe(localpipes) == -1) {
		perror("pipe");
		return;
	}
	if (pipe(localpipes + 2) == -1) {
		perror("pipe");
		return;
	}

	int pid = vfork();
	if (pid == -1) {
		perror("fork");
	} else if (pid > 0) {
		// Close write end of the pipes.
		close(localpipes[1]);
		close(localpipes[3]);

		pids[i] = pid;
		buffers[2*i].fd = localpipes[0];
		buffers[2*i+1].fd = localpipes[2];
	} else {
		// Close read ends of local pipes.
		close(localpipes[0]);
		close(localpipes[2]);

		// Close stdout,stderr and redirect them to the pipes.
		if (dup2(localpipes[1], 1) == -1) {
			perror("dup2");
		}
		if (dup2(localpipes[3], 2) == -1) {
			perror("dup2");
		}

		// Close duplicated ends.
		close(localpipes[1]);
		close(localpipes[3]);

		if (i != 0) {
			// Close stdin
			close(0);
		}

		if (execve(commands[i][0], commands[i], envs[i]) == -1) {
			perror("execve");
			_exit(1);
		}
	}
}

int main(int argc, char* argv[]) {
	long maxrss = 0;
	long long utime = 0;

	int retval = 0;
	struct stat st;
	for (int i = 0; i < $numProcesses; i++) {
		if (stat(pipeNames[i], &st) != -1) {
			if (S_ISFIFO(st.st_mode)) {
				// Pipe already exists.
				continue;
			} else {
				if  (unlink(pipeNames[i]) == -1) {
					perror("unlink");
					retval = 1;
					goto cleanup;
				}
			}
		} else if (errno != ENOENT) {
			perror("stat");
			retval = 1;
			goto cleanup;
		}
		if (mknod(pipeNames[i], 0664 | S_IFIFO, 0) == -1) {
			perror("mknod");
			retval = 1;
			goto cleanup;
		}
	}

	memset(buffers, 0, sizeof(buffers));

	// Execute the children
	for (int i = 0; i < $numProcesses; i++) {
		execute(i);
	}

	// Redirect children's outputs to screen
	fd_set readfds, writefds, exceptfds;
	FD_ZERO(&writefds);
	FD_ZERO(&exceptfds);
	while (1) {
		FD_ZERO(&readfds);
		int nfds = 0;
		for (int i = 0; i < ${2 * numProcesses}; i++) {
			if (buffers[i].closed) continue;
			FD_SET(buffers[i].fd, &readfds);
			if (nfds < buffers[i].fd) {
				nfds = buffers[i].fd;
			}
		}

		if (nfds == 0) {
			// All children are done writing.
			break;
		}

		int ready = select(nfds + 1, &readfds, &writefds, &exceptfds, NULL);

		if (ready == -1) {
			perror("select");
			break;
		}

		for (int i = 0; i < ${2 * numProcesses}; i++) {
			if (!FD_ISSET(buffers[i].fd, &readfds)) continue;
			ssize_t nbytes = read(buffers[i].fd,
					buffers[i].buf + buffers[i].pos,
					sizeof(buffers[i].buf) - buffers[i].pos);
			if (nbytes == -1) {
				perror("read");
			} else if (nbytes > 0) {
				buffers[i].pos += nbytes;
				int off = 0;
				for (int j = 0; j < buffers[i].pos; j++) {
					if (buffers[i].buf[j] == '\\n') {
						buffers[i].buf[j] = '\\0';
						if (i == 0) {
							if (isatty(1) && isatty(2)) {
								fprintf(stderr, "\\033[1m[%${maxNameLength}s]\\033[0m %s\\n",
									interfaces[i / 2], buffers[i].buf + off);
							} else if (isatty(1)) {
								fprintf(stdout, "\\033[1m[%${maxNameLength}s]\\033[0m %s\\n",
									interfaces[i / 2], buffers[i].buf + off);
							} else {
								fprintf(stdout, "%s\\n", buffers[i].buf + off);
							}
						} else {
							fprintf(stderr, "[%${maxNameLength}s] %s\\n",
								interfaces[i / 2], buffers[i].buf + off);
						}
						off = j + 1;
					}
				}
				if (off != 0) {
					for (int j = off; j < buffers[i].pos; j++) {
						buffers[i].buf[j - off] = buffers[i].buf[j];
					}
					buffers[i].pos -= off;
				} else if (buffers[i].pos == sizeof(buffers[i].buf)) {
					writefull(i == 0 ? 1 : 2, buffers[i].buf, sizeof(buffers[i].buf));
					buffers[i].pos = 0;
				}
			} else {
				buffers[i].closed = 1;
			}
		}
	}

	// Wait for children
	for (int i = 0; i < $numProcesses; i++) {
		int status;
		struct rusage usage;
		if (wait4(pids[i], &status, 0, &usage) == -1) {
			perror("wait4");
		} else if (i != 0) {
			if (maxrss < usage.ru_maxrss) {
				maxrss = usage.ru_maxrss;
			}
			utime += usage.ru_utime.tv_sec * 1000000LL + usage.ru_utime.tv_usec;
		}
	}

cleanup:
	for (int i = 0; i < $numProcesses; i++) {
		if (unlink(pipeNames[i]) == -1) {
			perror("unlink");
		}
	}

	fprintf(stderr, "\\nMemory: %7.3f MB\\n", maxrss / 1024.0f);
	fprintf(stderr, "Time:   %7.3f s\\n", utime / 1e6);

	return retval;
}
"""

		OutputFile(Paths.get("run.c"), builder.mkString)
	}

	override def extension() = ???
	override def generateMakefileRules() = ???
	override def generateRunCommands() = ???
	protected def generateTemplates(moduleName: String,
			interfacesToImplement: Iterable[Interface], callableModuleName: String,
			callableInterfaces: Iterable[Interface], input: Path) = ???
}

/* vim: set noexpandtab: */
