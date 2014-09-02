libinteractive
==============

A tiny IDL translator that creates IPC shims to easily create interactive problems.

You can grab the latest `.jar` from https://omegaup.com/libinteractive-1.0.jar

Let's say your problem's name is `sum`. if you have your validator in `Main.cpp` and your solution in `sum.py`, you can do

``` console
$ ls
Main.cpp sum.py sum.idl input

$ cat Main.cpp
#include <stdio.h>
#include "sum.h"

int main(int argc, char* argv[]) {
    int a, b;
    scanf("%d %d\n", &a, &b);
    printf("%d\n", sum(a, b));
}

$ cat sum.py
def sum(a, b):
    print 'Hello, world!'
    return a + b
    
$ cat sum.idl
interface Main {
}

interface sum {
    int sum(int a, int b);
}

$ java -jar libinteractive.jar generate sum.idl cpp py --makefile
$ make run < input
[Main] 3
[ sum] Hello, world!

Memory:   5.023 MB
Time:     0.011 s
```

You can also add a --verbose flag to libinteractive to print a message every time an IPC call is made.

How it works
------------

libinteractive parses the .idl file, which is a description of how the problemsetter and contestant's code
are to be compiled, and which functions can be used to interact between them. It then generates all the
necessary code that allows the code to communicate correctly. libinteractive uses Unix named pipes to
send binary messages between the processes, which is much faster than reading and parsing text files.

One advantage of libinteractive is that since it separates the problemsetter and contestant's codes in
different processes, they can be written in different programming languages. This means that you as a
problemsetter will only need to write one program that reads the input and interacts with the contestant's
code, and libinteractive will handle serializing and de-serializing the function calls.

libinteractive will also produce an executable called `run`, which will create the necessary pipes for
communication, execute all the programs, relay the input to the Main program (if needed), and print out
any output from the programs. Finally, it will print a summary of the maximum memory and total (user) time
consumed by the contestant's code.

Since libinteractive was built with omegaUp in mind, all problems built using libinteractive will
be omegaUp-compliant.

## Conventions

* The first interface must be called `Main`, and the problemsetter's program must be in a file called
  `Main.extension`, where `extension` is one of `c`, `cpp`, `rb`, `py`, `java`, `pas`.
* The name of the .idl file must match the name of the contestant's code.
* Each interface will produce a separate binary and will run in a different process, which means that
  they will not be able to share anything.
* It is only possible to communicate between all the interfaces and Main, but not amongst them.
* Arrays are allowed, but the rules of array bounds in C must be followed: all array dimensions (except
  the first one) must be integer constants.
* Arrays may declare the first dimension to be a variable, but it must be a parameter of the function,
  and must appear before in the parameter list.

## IDL Grammar

IDL is almost a subset of [WebIDL](http://www.w3.org/TR/2012/WD-WebIDL-20120207/), but with some
syntax to help programming contests.

    letter
        = "a" | "b" | ... | "y" | "z"
        | "A" | "B" | ... | "Y" | "Z"
        ;
    
    digit
        = "0" | "1" | ... | "8" | "9"
        ;
    
    ident
        = (letter | "_"), { letter | digit | "_" }
        ;
    
    number
        = digit, { digit }
        ;

    interface-list
        = interface, { interface }
        ;
    
    interface
        = "interface", ident, "{", { function }, "}", ";"
        ;
    
    function
        = return-type, ident, "(", param-list , ")", ";"
        ;
    
    param-list
        = [ param, { ",", param } ]
        ;
    
    type
        = array | primitive
        ;
    
    primitive
        = "bool" | "int" | "short" | "float"
        | "char" | "string" | "long"
        ;
    
    array
        = primitive, "[", expr, "]", { "[", expr, "]" }
        ;
    
    return-type
        = primitive | "void"
        ;
    
    expr
        = ident | number
        ;
    
    param
        = { param-attribute }, type, ident
        ;
    
    param-attribute
        = "[", range-attribute, "]"
        ;
    
    range-attribute
        = "Range", "(", expression, ",", expression, ")"
        ;
