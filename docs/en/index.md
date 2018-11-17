# libinteractive

A tiny IDL translator that creates IPC shims to easily create interactive
problems.

To avoid having to write a program in each of the supported languages and
making sure that none of them has bugs or undefined behavior that might
cause the wrong output/veredict to be emitted, libinteractive allows you
to have both the problemsetter and contestants' implementations in different
processes and possibly languages.

### Example

Let's say you have a problem called `sums`. You, as problemsetter, implement
your part of the problem in a file called `Main.cpp` and the contestants writes
their solution in a file called `sums.py`, so you can do:

``` console
$ ls
Main.cpp sums.py sums.idl input

$ cat Main.cpp
#include <stdio.h>
#include "sums.h"

int main(int argc, char* argv[]) {
    int a, b;
    scanf("%d %d\n", &a, &b);
    printf("%d\n", sums(a, b));
}

$ cat sums.py
def sums(a, b):
    print 'Hello, world!'
    return a + b

$ cat sums.idl
interface Main {
};

interface sums {
    int sums(int a, int b);
};

$ java -jar libinteractive.jar generate sums.idl cpp py --makefile
$ make run < input
[Main] 3
[ sum] Hello, world!

Memory:   5.023 MB
Time:     0.011 s
```

You can also add a --verbose flag to libinteractive to print a message every
time an IPC call is made.

# Download

You can grab the latest `.jar` from the [Releases page](https://github.com/omegaup/libinteractive/releases)
