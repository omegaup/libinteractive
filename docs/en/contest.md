# Running libinteractive problems

libinteractive is an easy way to write interactive problems. The process to
compile, run, and test programs is slightly different to the one you are used
to, but it is simple. The specific steps depend on the operating system you are
using.

First, choose your operating system and the language you will be using with the
dropdown that is shown in the problem statement. Then, follow the steps that
correspond to the operating system you are using:

### Windows

* Extract all files in the template into a folder. Open the Commandline
  (Windows+R, type `cmd` and press Return), and navigate to the folder you just
  created.
* You must have previously installed [Code::Blocks](http://www.codeblocks.org/downloads/binaries#windows)
  (make sure to install the version that says MinGW) and run it at least once.
    * If you want to use C or C++, simply open the Code::Blocks project file
      that is included in the folder you just extracted. You can ignore the
      rest of the steps. To try other test cases, try modifying the `sample.in`
      file that is included in the project.
    * If you are using Java, you must install the
      [JDK](http://www.oracle.com/technetwork/java/javase/downloads/).
    * If you want to use Pascal, you need to install
      [Lazarus](http://www.lazarus.freepascal.org/index.php?page=downloads) and
      have launched it at least once.
    * If you want to use Python, you need to install
      [Python 2.7](https://www.python.org/downloads/).
* To compile all required programs, type `run` (or `run.bat`). This will also
  run your code. Remember that the problemsetter program expects to read the
  case from stdin, so you either need to type it on the commandline or redirect
  stdin (`run < input.in`).
* To test your solution with a test case, write `test` (or `test.bat`). This is
  equivalent to running `run.bat < examples\sample.in`.

### Linux/Mac OS X

* Extract all template files to a directory, open the terminal and navigate to
  the recently created directory.
* It is recommended that you have the following packages installed: `make`,
  `gcc`, `g++`, `fpc`, `python` y `openjdk-7-jdk`.
* Write `make` to compile all needed programs and `make run` to execute your
  code. Rembember that the problemsetter program expects to read a test case
  from stdin, so either type it on the terminal or redirect stdin
  (`make run < input.in`).
* To test your solution against a test case, write `make test`. This is
  equivalent to running `make run < examples/sample.in`.

# General notes

* To correctly solve the problem, you must only submit the file that the
  website tells you. Don't send any other file or it will result in a
  compilation error.
* Some problems will have extra test inputs beside `sample.in`. Try to make
  your code run correctly with all those cases. You can try them out by running
  either `run < examples/file.in` or `make run < examples/file.in`, depending
  on your operating system.
* To get all possible points in a problem, your program must solve all possible
  test cases, even some not included in the `examples` folder. Try to think of
  additional test cases, run your code with them and verify that the result is
  correct. Good luck!
