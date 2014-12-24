// Copyright (c) 2014 The omegaUp Contributors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.omegaup.libinteractive.target

import java.nio.file.Path
import java.nio.file.Paths

import com.omegaup.libinteractive.idl.IDL
import com.omegaup.libinteractive.idl.Interface

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
		val builder = new StringBuilder
		val allRules = (rules ++ List(
				MakefileRule(
					Paths.get("run"),
					List(Paths.get("run.c")),
					Compiler.Gcc, "-std=c99 -o $@ -lrt $^ -O2 -D_XOPEN_SOURCE=600 " +
					"-D_BSD_SOURCE -Wall"))).map(resolve)
		val allExecutables = allRules.map(_.target).mkString(" ")

		builder ++= s"""# $message

all: $allExecutables

${allRules.map(
	rule => s"${rule.target}: ${rule.requisites.mkString(" ")}\n" +
			s"\t${rule.compiler} ${rule.params}\n"
).mkString("\n")}
.PHONY: run
run: $allExecutables
	@${relativeToRoot(options.outputDirectory.resolve(Paths.get("run")))}

.PHONY: test
test: $allExecutables
	@${relativeToRoot(options.outputDirectory.resolve(Paths.get("run")))} < examples/sample.in
"""

		OutputFile(options.root.resolve("Makefile"), builder.mkString, false)
	}

	private def generateWindowsBuildRule(rule: MakefileRule) = {
		s"""
SET TARGET=${rule.target.toString.replace("/", "\\")}
SET SOURCES=${rule.requisites.map(_.toString.replace("/", "\\")).mkString(" ")}
call:recompile %TARGET% %SOURCES%
IF "%RECOMPILE%" == "1" (
ECHO Compiling %TARGET%
%${rule.compiler.toString.toUpperCase}% ${
		rule.params.replace("$@", "%TARGET%").replace("$^", "%SOURCES%")}
IF !ERRORLEVEL! NEQ 0 EXIT /B !ERRORLEVEL!
)"""
	}

	private def generateWindowsLink(link: ResolvedOutputLink) = {
		s"""
SET TARGET=${relativeToRoot(link.link).toString.replace("/", "\\")}
SET SOURCES=${relativeToRoot(link.target).toString.replace("/", "\\")}
call:recompile %TARGET% %SOURCES%
IF "%RECOMPILE%" == "1" (
COPY %SOURCES% %TARGET%
IF !ERRORLEVEL! NEQ 0 EXIT /B !ERRORLEVEL!
)"""
	}

	private def generateBatchFileContents() = {
		val builder = new StringBuilder
		val allRules = (rules ++ List(
				MakefileRule(
					Paths.get("run.exe"),
					List(Paths.get("run.c")),
					Compiler.Gcc, "-std=c99 -o $@ $^ -O2 -lpsapi -Wall"))).map(resolve)
		val allExecutables = allRules.map(_.target).mkString(" ")

		builder ++= s"""@ECHO OFF
REM $message
SETLOCAL EnableDelayedExpansion

REM Get all compilers/paths needed
${allRules.map(rule => s"CALL :get${rule.compiler} || EXIT /B 1").toSet.mkString("\n")}
${if (options.parentLang == "py" || options.childLang == "py") "call:getpython" else ""}

REM Update all "links"
${resolvedLinks.map(generateWindowsLink).mkString("\n")}

REM Build all binaries (if needed)
${allRules.map(generateWindowsBuildRule).mkString("\n")}

REM Run the driver
${relativeToRoot(options.outputDirectory.resolve(Paths.get("run.exe")))
		.toString.replace("/", "\\")}
IF !ERRORLEVEL! NEQ 0 EXIT /B !ERRORLEVEL!
GOTO:EOF

:getgcc
REG QUERY HKCU\\Software\\CodeBlocks /v Path 2>NUL >NUL
IF "%ERRORLEVEL%" NEQ "0" (
ECHO Please install the latest version of CodeBlocks and launch it once
ECHO http://www.codeblocks.org/downloads/binaries#windows (mingw-setup.exe)
EXIT /B 1
GOTO:EOF
)
FOR /F "tokens=2*" %%A IN ('REG QUERY HKCU\\Software\\CodeBlocks /v Path') DO SET GCC=%%B
SET PATH=%PATH%;%GXX%\\MinGW\\bin
SET GCC="%GCC%\\MinGW\\bin\\gcc.exe"
GOTO:EOF

:getg++
REG QUERY HKCU\\Software\\CodeBlocks /v Path 2>NUL >NUL
IF "%ERRORLEVEL%" NEQ "0" (
ECHO Please install the latest version of CodeBlocks and launch it once
ECHO http://www.codeblocks.org/downloads/binaries#windows (mingw-setup.exe)
EXIT /B 1
GOTO:EOF
)
FOR /F "tokens=2*" %%A IN ('REG QUERY HKCU\\Software\\CodeBlocks /v Path') DO SET G++=%%B
SET PATH=%PATH%;%G++%\\MinGW\\bin
SET G++="%G++%\\MinGW\\bin\\g++.exe"
GOTO:EOF

:getjavac
REG QUERY "HKLM\\Software\\JavaSoft\\Java Development Kit" /v CurrentVersion 2>NUL >NUL
IF "%ERRORLEVEL%" NEQ "0" (
ECHO Please install the latest version of the Java Development Kit
ECHO http://www.oracle.com/technetwork/java/javase/downloads/
EXIT /B 1
GOTO:EOF
)
FOR /F "tokens=2*" %%A IN ('REG QUERY "HKLM\\Software\\JavaSoft\\Java Development Kit" /v CurrentVersion') DO SET JAVA_VERSION=%%B
FOR /F "tokens=2*" %%A IN ('REG QUERY "HKLM\\Software\\JavaSoft\\Java Development Kit\\%JAVA_VERSION%" /v JavaHome') DO SET JAVAC=%%B
SET PATH=%PATH%;%JAVAC%\\bin
SET JAVAC="%JAVAC%\\bin\\javac.exe"
GOTO:EOF

:getpython
REG QUERY HKLM\\Software\\Python\\PythonCore\\2.7\\InstallPath /ve 2>NUL >NUL
IF "%ERRORLEVEL%" NEQ "0" (
ECHO Please install the latest version of Python 2.7
ECHO https://www.python.org/downloads/
EXIT /B 1
GOTO:EOF
)
FOR /F "tokens=2*" %%A IN ('REG QUERY HKLM\\Software\\Python\\PythonCore\\2.7\\InstallPath /ve') DO SET PYTHON=%%B
SET PATH=%PATH%;%PYTHON%
SET PYTHON="%PYTHON%\\python.exe"
GOTO:EOF

:getfpc
IF NOT EXIST "%LOCALAPPDATA%\\lazarus\\environmentoptions.xml" (
ECHO Please install the latest version of Lazarus and run it once
ECHO http://www.lazarus.freepascal.org/index.php?page=downloads
EXIT /B 1
GOTO:EOF
)
FOR /F tokens^=2^ delims^=^" %%A IN ('findstr "<CompilerFilename" "%LOCALAPPDATA%\\lazarus\\environmentoptions.xml"') DO SET FPC=%%A
SET PATH=%PATH%;%FPC\\..
SET FPC="%FPC%"
GOTO:EOF

:recompile
SET RECOMPILE=0
IF NOT EXIST %~1 (
SET RECOMPILE=1
GOTO:EOF
)
FOR /F %%i IN ('DIR /S /B %~1') DO SET TARGET=%%i
:params
SHIFT
IF "%~1" == "" GOTO endparams
FOR /F %%i IN ('DIR /S /B %~1') DO SET SOURCE=%%i
FOR /F %%i IN ('DIR /S /B /O:D %TARGET% %SOURCE%') DO SET NEWEST=%%i
IF "%NEWEST%" == "%SOURCE%" (SET RECOMPILE=1) ELSE (GOTO params)
:endparams
GOTO:EOF
"""

		List(
			OutputFile(options.root.resolve("run.bat"), builder.mkString, false),
			OutputFile(options.root.resolve("test.bat"), "@ECHO OFF\nREM $message\n\n" +
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

#define NUM_PROCESSES $numProcesses
#define LABEL_FORMAT "[%${maxNameLength}s] "

const char* interfaces[] = {
	${idl.allInterfaces.map('"' + _.name + '"').mkString(", ")}
};
const char* pipeNames[] = {
	${idl.allInterfaces.map(interface => '"' + pipeFilename(interface, interface) + '"').mkString(", ")}
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

buffer buffers[2 * NUM_PROCESSES];
int pids[NUM_PROCESSES] = {0};

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
	for (int i = 0; i < NUM_PROCESSES; i++) {
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
	for (int i = 0; i < NUM_PROCESSES; i++) {
		execute(i);
	}

	// Redirect children's outputs to screen
	fd_set readfds, writefds, exceptfds;
	FD_ZERO(&writefds);
	FD_ZERO(&exceptfds);
	while (1) {
		FD_ZERO(&readfds);
		int nfds = 0;
		for (int i = 0; i < 2 * NUM_PROCESSES; i++) {
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

		for (int i = 0; i < 2 * NUM_PROCESSES; i++) {
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
								fprintf(stderr, "\\033[1m" LABEL_FORMAT "\\033[0m%s\\n",
									interfaces[i / 2], buffers[i].buf + off);
							} else if (isatty(1)) {
								fprintf(stdout, "\\033[1m" LABEL_FORMAT "\\033[0m%s\\n",
									interfaces[i / 2], buffers[i].buf + off);
							} else {
								fprintf(stdout, "%s\\n", buffers[i].buf + off);
							}
						} else {
							fprintf(stderr, LABEL_FORMAT "%s\\n",
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
	for (int i = 0; i < NUM_PROCESSES; i++) {
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
	for (int i = 0; i < NUM_PROCESSES; i++) {
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

	private def generateRunDriverWindows() = {
		val builder = new StringBuilder
		val numProcesses = commands.foldLeft(0)((length, _) => length + 1)
		val maxNameLength = idl.allInterfaces.foldLeft(0)((length, interface) =>
			Math.max(length, interface.name.length))
		builder ++= s"""/* $message */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#define PSAPI_VERSION 1
#include <windows.h>
#include <psapi.h>

#define NUM_PROCESSES $numProcesses
#define LABEL_FORMAT "[%${maxNameLength}s] "

const char* interfaces[] = {
	${idl.allInterfaces.map('"' + _.name + '"').mkString(", ")}
};

typedef struct {
	const char* out;
	int in_count;
	const char* in[NUM_PROCESSES - 1];
} pipe_description;

pipe_description pipe_descriptions[NUM_PROCESSES] = {
	{ "${pipeFilename(idl.main, idl.main)}", NUM_PROCESSES - 1, { ${
		idl.interfaces.map('"' + pipeFilename(idl.main, _) + '"').mkString(", ")} } },
${idl.interfaces.map(interface =>
	"\t{ " + '"' + pipeFilename(interface, interface) + '"' + ", 1, { " +
	List('"' + pipeFilename(interface, idl.main) + '"')
		.padTo(numProcesses - 1, "NULL")
		.mkString(", ") + " } }").mkString(",\n")
}
};

char* const commands[] = {
${commands.map(command =>
	"\t\"" +
		command.args.map("\\\"" + _.replace("/", "\\").replace("\\", "\\\\") + "\\\"")
			.mkString(" ") +
	"\"").mkString(",\n")}
};
int has_stdin[NUM_PROCESSES] = {
	1, ${ idl.interfaces.map(interface => "0").mkString(", ") }
};

typedef struct {
	OVERLAPPED overlapped;
	HANDLE read_fd;
	HANDLE write_fd;
	const char* read_name;
	const char* write_name;
	int connected;
	int closed;
	int pending;
	int pos;
	char buf[1024];
	int line;
	int main_stdout;
	const char* prefix;
} buffer;

typedef struct {
	PROCESS_INFORMATION pi;
	HANDLE out;
	HANDLE err;
} process;

buffer buffers[2 * NUM_PROCESSES + 2 * (NUM_PROCESSES - 1)];
HANDLE events[2 * NUM_PROCESSES + 2 * (NUM_PROCESSES - 1)];
process processes[NUM_PROCESSES];

// Similar in spirit to perror.
static void print_error(const char* message, DWORD error) {
	char buffer[1024];
	FormatMessageA(
		FORMAT_MESSAGE_FROM_SYSTEM |
		FORMAT_MESSAGE_IGNORE_INSERTS,
		NULL,
		error,
		MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT),
		buffer,
		sizeof(buffer),
		NULL);
	fprintf(stderr, "%s: %s\\n", message, buffer);
}

// Ensure a buffer is fully written into the fd.
static void write_full(HANDLE fd, const char* buffer, DWORD bytes_read) {
	BOOL success;
	DWORD bytes_written;
	while (bytes_read) {
		success = WriteFile(
			fd,
			buffer,
			bytes_read,
			&bytes_written,
			NULL
		);
		if (!success || bytes_written == 0) {
			print_error("WriteFile", GetLastError());
			exit(1);
		}
		bytes_read -= bytes_written;
		buffer += bytes_written;
	}
}

// Once a buffer is read, send it out to its corresponding handle.
// Main's stdout will be highlighted in white instead of gray (unless
// redirected to a pipe or file). All other stdout/stderr will go to
// stderr and will have a nice label.
static void process_buffer(buffer* buf) {
	if (buf->line) {
		char label[1024];
		_snprintf(label, sizeof(label), LABEL_FORMAT, buf->prefix);
		size_t label_len = strlen(label);
		const char* last = buf->buf;
		const char* end = buf->buf + buf->pos;
		int should_label = GetFileType(buf->write_fd) == FILE_TYPE_CHAR;
		int should_color = buf->main_stdout;
		CONSOLE_SCREEN_BUFFER_INFO color_info;
		if (should_color) {
			GetConsoleScreenBufferInfo(buf->write_fd, &color_info);
		}
		for (const char* pos = buf->buf; pos < end; pos++) {
			if (*pos == '\\n') {
				if (should_label) {
					if (should_color) {
						SetConsoleTextAttribute(
							buf->write_fd,
							FOREGROUND_INTENSITY | FOREGROUND_RED | FOREGROUND_GREEN | FOREGROUND_BLUE
						);
					}
					write_full(buf->write_fd, label, label_len);
					if (should_color) {
						SetConsoleTextAttribute(
							buf->write_fd,
							color_info.wAttributes
						);
					}
				}
				write_full(buf->write_fd, last, pos - last + 1);
				last = pos + 1;
			}
		}
		if (last == buf->buf) {
			// Nothing written, force flush
			if (should_label) {
				write_full(buf->write_fd, label, label_len);
			}
			write_full(buf->write_fd, buf->buf, buf->pos);
			write_full(buf->write_fd, "\\r\\n", 2);
			buf->pos = 0;
		} else {
			char* wpos = buf->buf;
			for (const char* rpos = last; rpos < end; rpos++, wpos++) {
				*wpos++ = *rpos;
			}
			buf->pos = end - last;
		}
	} else {
		write_full(buf->write_fd, buf->buf, buf->pos);
		buf->pos = 0;
	}
}

// A wrapper for CreateProcess. Also redirects stdio.
void execute(int i) {
	STARTUPINFOA si;

    ZeroMemory(&si, sizeof(si));
    si.cb = sizeof(si);
	si.hStdInput = has_stdin[i] ? GetStdHandle(STD_INPUT_HANDLE): INVALID_HANDLE_VALUE;
	si.hStdOutput = processes[i].out;
	si.hStdError = processes[i].err;
	si.dwFlags = STARTF_USESTDHANDLES;
	
	BOOL success = CreateProcessA(
		NULL,
		commands[i],
		NULL,
		NULL,
		TRUE,
		0,
		NULL,
		NULL,
		&si,
		&processes[i].pi
	);
	if (!success) {
		print_error("CreateProcess", GetLastError());
		exit(1);
	}
}

// Similar to mknod for a pipe.
static HANDLE create_pipe(const char* name, int read) {
	HANDLE named_pipe = CreateNamedPipeA(
		name,
		read ?
			(PIPE_ACCESS_INBOUND | FILE_FLAG_OVERLAPPED) :
			PIPE_ACCESS_OUTBOUND,
		PIPE_TYPE_BYTE | PIPE_WAIT,
		PIPE_UNLIMITED_INSTANCES,
		1024,
		1024,
		0,
		NULL
	);
	if (named_pipe == INVALID_HANDLE_VALUE) {
		print_error("CreateNamedPipe", GetLastError());
		exit(1);
	}
	return named_pipe;
}

// This function should be similar to CreatePipe, but allowing one
// end (the reading end) of the pipe to have overlapped I/O.
static unsigned long long pipe_counter = 0;
static void create_pipe_pair(HANDLE* read, HANDLE* write) {
	char pipe_name_buffer[1024];
	_snprintf(pipe_name_buffer, sizeof(pipe_name_buffer),
	          "\\\\\\\\.\\\\pipe\\\\libinteractive_%I64u", pipe_counter++);
	SECURITY_ATTRIBUTES sa;
	ZeroMemory(&sa, sizeof(sa));
	sa.nLength = sizeof(sa);
	sa.bInheritHandle = TRUE;
	
	*read = CreateNamedPipeA(
		pipe_name_buffer,
		PIPE_ACCESS_INBOUND | FILE_FLAG_OVERLAPPED,
		PIPE_TYPE_BYTE | PIPE_WAIT,
		1,
		1024,
		1024,
		0,
		&sa
	);
	
	if (!*read || !SetHandleInformation(*read, HANDLE_FLAG_INHERIT, 0)) {
		print_error("CreateNamedPipe", GetLastError());
		exit(1);
	}
	
	*write = CreateFileA(
		pipe_name_buffer,
		GENERIC_WRITE,
		0,
		&sa,
		OPEN_EXISTING,
		FILE_ATTRIBUTE_NORMAL,
		NULL
	);
	
	if (!*write) {
		print_error("CreateFile", GetLastError());
		exit(1);
	}
}

int main(int argc, char* argv[]) {
	long maxrss = 0;
	long long utime = 0;

	int retval = 0;
	memset(buffers, 0, sizeof(buffers));
	size_t num_buffers = 0;

	// Create stdout/stderr pipes.
	memset(buffers, 0, sizeof(buffers));	
	for (int i = 0; i < NUM_PROCESSES; i++) {
		create_pipe_pair(&buffers[num_buffers].read_fd, &processes[i].out);
		BOOL success = DuplicateHandle(
			GetCurrentProcess(),
			i ?
				GetStdHandle(STD_ERROR_HANDLE) :
				GetStdHandle(STD_OUTPUT_HANDLE),
			GetCurrentProcess(),
			&buffers[num_buffers].write_fd,
			0,
			FALSE,
			DUPLICATE_SAME_ACCESS
		);
		buffers[num_buffers].read_name = interfaces[i];
		buffers[num_buffers].write_name = i ? "stderr" : "stdout";
		if (!success) {
			print_error("DuplicateHandle", GetLastError());
			exit(1);
		}
		buffers[num_buffers].line = 1;
		buffers[num_buffers].main_stdout = i == 0;
		buffers[num_buffers].prefix = interfaces[i];
		events[num_buffers] = CreateEvent(
			NULL,
			TRUE,
			TRUE,
			NULL
		);
		buffers[num_buffers].overlapped.hEvent = events[num_buffers];
		num_buffers++;
		
		create_pipe_pair(&buffers[num_buffers].read_fd, &processes[i].err);
		success = DuplicateHandle(
			GetCurrentProcess(),
			GetStdHandle(STD_ERROR_HANDLE),
			GetCurrentProcess(),
			&buffers[num_buffers].write_fd,
			0,
			FALSE,
			DUPLICATE_SAME_ACCESS
		);
		if (!success) {
			print_error("DuplicateHandle", GetLastError());
			exit(1);
		}
		buffers[num_buffers].read_name = interfaces[i];
		buffers[num_buffers].write_name = "stderr";
		buffers[num_buffers].line = 1;
		buffers[num_buffers].prefix = interfaces[i];
		events[num_buffers] = CreateEvent(
			NULL,
			TRUE,
			TRUE,
			NULL
		);
		buffers[num_buffers].overlapped.hEvent = events[num_buffers];
		num_buffers++;
	}

	// Create RPC pipes.
	for (int i = 0; i < NUM_PROCESSES; i++) {
		HANDLE out = create_pipe(pipe_descriptions[i].out, 0);
		for (int j = 0; j < pipe_descriptions[i].in_count; j++) {
			events[num_buffers] = CreateEvent(
				NULL,
				TRUE,
				TRUE,
				NULL
			);
			buffers[num_buffers].overlapped.hEvent = events[num_buffers];
			buffers[num_buffers].read_name = pipe_descriptions[i].out;
			buffers[num_buffers].write_name = pipe_descriptions[i].in[j];
			buffers[num_buffers].read_fd =
				create_pipe(pipe_descriptions[i].in[j], 1);
			if (j == 0) {
				buffers[num_buffers].write_fd = out;
			} else {
				BOOL success = DuplicateHandle(
					GetCurrentProcess(),
					out,
					GetCurrentProcess(),
					&buffers[num_buffers].write_fd,
					0,
					FALSE,
					DUPLICATE_SAME_ACCESS
				);
				
				if (!success) {
					print_error("DuplicateHandle", GetLastError());
					exit(1);
				}
			}
			if (ConnectNamedPipe(buffers[num_buffers].read_fd,
			                     &buffers[num_buffers].overlapped)) {
				print_error("ConnectNamedPipe", GetLastError());
				exit(1);
			}
			switch (GetLastError()) {
				case ERROR_IO_PENDING:
					buffers[num_buffers].connected = 0;
					break;
				case ERROR_PIPE_CONNECTED:
					buffers[num_buffers].connected = 1;
					break;
				default:
					print_error("ConnectNamedPipe", GetLastError());
					exit(1);
			}
			
			num_buffers++;
		}
	}

	// Execute the children.
	for (int i = 0; i < NUM_PROCESSES; i++) {
		execute(i);
	}

	// Wait for the children to connect their ends of the pipes.
	for (int i = 2 * NUM_PROCESSES; i < num_buffers; i++) {
		DWORD _;
		BOOL success = GetOverlappedResult(
			buffers[i].read_fd,
			&buffers[i].overlapped,
			&_,
			TRUE
		);
		if (!success) {
			print_error("GetOverlappedResult", GetLastError());
			exit(1);
		}
	}
	
	// Release our copies of the children's ends of the pipes.
	for (int i = 0; i < NUM_PROCESSES; i++) {
		CloseHandle(processes[i].out);
		CloseHandle(processes[i].err);
	}
	
	// Process I/O on all the pipes until they are all closed.
	int alive = num_buffers;
	while (alive) {
		int i = WaitForMultipleObjects(
			num_buffers,
			events,
			FALSE,
			INFINITE
		) - WAIT_OBJECT_0;
		
		if (i < 0 || i > (num_buffers - 1)) {
			print_error("WaitForMultipleObjects", GetLastError());
			exit(1);
		}
		
		if (buffers[i].closed) {
			continue;
		}
		
		BOOL success;
		DWORD bytes_read;
		if (buffers[i].pending) {
			success = GetOverlappedResult(
				buffers[i].read_fd,
				&buffers[i].overlapped,
				&bytes_read,
				FALSE
			);
			buffers[i].pending = 0;
			if (!success && bytes_read <= 0) {
				buffers[i].closed = 1;
				CloseHandle(buffers[i].read_fd);
				CloseHandle(buffers[i].write_fd);
				ResetEvent(events[i]);
				alive--;
				continue;
			}
			buffers[i].pos += bytes_read;
			process_buffer(&buffers[i]);
		}
		
		while (1) {
			success = ReadFile(
				buffers[i].read_fd,
				buffers[i].buf + buffers[i].pos,
				sizeof(buffers[i].buf) - buffers[i].pos,
				&bytes_read,
				&buffers[i].overlapped
			);
			
			if (success && bytes_read > 0) {
				buffers[i].pending = 0;
				buffers[i].pos += bytes_read;
				process_buffer(&buffers[i]);
				continue;
			}
			
			DWORD last_error = GetLastError();
			if (!success && last_error == ERROR_IO_PENDING) {
				buffers[i].pending = 1;
				break;
			}
			
			buffers[i].closed = 1;
			CloseHandle(buffers[i].read_fd);
			CloseHandle(buffers[i].write_fd);
			ResetEvent(events[i]);
			alive--;
			break;
		}
	}

	// Close all processes and grab execution data.
	for (int i = 0; i < NUM_PROCESSES; i++) {
		WaitForSingleObject(processes[i].pi.hProcess, INFINITE);
		FILETIME creation, exit, kernel, user;
		GetProcessTimes(
			processes[i].pi.hProcess,
			&creation,
			&exit,
			&kernel,
			&user
		);
		ULARGE_INTEGER usertime;
		usertime.LowPart = user.dwLowDateTime;
		usertime.HighPart = user.dwHighDateTime;
		utime += usertime.QuadPart / 10;
		
		PROCESS_MEMORY_COUNTERS counters;
		ZeroMemory(&counters, sizeof(counters));
		counters.cb = sizeof(counters);
		GetProcessMemoryInfo(
			processes[i].pi.hProcess,
			&counters,
			sizeof(counters)
		);
		maxrss = max(maxrss, counters.PeakWorkingSetSize);
		DWORD exit_code = 1;
		GetExitCodeProcess(
			processes[i].pi.hProcess,
			&exit_code
		);
		retval = max(retval, exit_code);
			
		CloseHandle(processes[i].pi.hProcess);
		CloseHandle(processes[i].pi.hThread);
	}

	fprintf(stderr, "\\nMemory: %7.3f MB\\n", maxrss / 1048576.0f);
	fprintf(stderr, "Time:   %7.3f s\\n", utime / 1e6);
	fclose(stdout);
	fclose(stderr);

	return retval;
}"""

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
