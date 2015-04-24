# How to write problems

First you need to write an .idl file with the description of the interfaces
(functions defined in both the problemsetter and contestant code). You must
also write a program (if possible in C/C++, for bestperformance) that reads a
case from stdin, invokes the functions that the contestant must implement, and
finally write either the final output or a number between 0 and 1 (inclusive)
that represents the score of the test case to stdout (if you use the `literal`
validator in omegaUp). Once this is done, you need to run libinteractive to
generate all necessary files to be able to test the solution, like so:

    java -jar libinteractive.jar generate <file.idl> <problemsetter language> <solution language> --makefile

Where the languages are one of `c`, `cpp`, `pas`, `py`, `java`. `pas` is not
supported as a problemsetter language, so it will fail if you try.

You must also create a directory called `examples` (in lowercase) and place a
sample input in a file called `sample.in` in that directory. If needed, you can
add more test cases in that directory, and they will be automatically copied to
the templates that the contestants can download.

Finally, the actual solution must be implemented. You can run `make test` to
verify that everything works correctly. If the problemsetter program contains a
valid solution to the problem or enough details can be leaked that would lead
to a solution, you can write a second implementation that would only be
distributed to the contestants in the template files by adding a file with
`.distrib` before the extension. For instance, if the problemsetter program is
in `Main.cpp`, the second, public version can be placed in `Main.distrib.cpp`
and it will be the one that will be copied into the templates.

Once you are happy with the problem, it must be converted to omegaUp format to
upload it. To do so, the following folders must be present:

* `cases`, with the official cases. The inputs must be saved in files with the
  `.in` extension, and the outputs in files with the same name but with `.out`
  extension.
* `statements` with the problem statements. The statements must be written in
  Markdown and there must be one per language. For instance, for a problem that
  will be in both english and spanish, there must be two files: `es.markdown`
  and `en.markdown`. To include the control to download the libinteractive
  templates, the `.markdown` files must include the string
  `{{libinteractive:download}}` in a line of its own, with no extra text.
* `interactive` with the interactive problem. This should **only** contain the
  .idl file, the problemsetter problem (`Main.cpp`), and the `examples` folder
  with `sample.in` and any other sample input. Optionally, the
  `Main.distrib.cpp` file may be present if `Main.cpp` has implementation
  details that should not be distributed. In particular, the Makefile, the
  sample solution and the `libinteractive` generated directory should not be
  present in the .zip.

These three folders must be saved in a .zip file, without any intermediate
folders. The templates that will be distributed to the contestants will be
automatically generated.

# Conventions

* The first interface in the .idl file is the problemsetter program and
  **must** be called `Main`. The problemsetter program must then be placed in a
  file called `Main.cpp` (probably with a different extension, depending of the
  programming language it is written on).
* The contestant's program(s) must be saved in a file with the same name as the
  .idl file, but with the correct extension for the programming language it is
  written on. For instance, for the problem `sums.idl`, the solution must be
  placed in the file `sums.cpp`.
* Each interface will be compiled into a different executable, and will be run
  in separate processes. This means that no variables may be shared, so it will
  be necessary to communicate any state using functions.
* All interfaces may call the functions in the Main interface, and Main may
  call any function in any other interface. Other interfaces cannot call each
  other's functions.
* Arrays as parameter types are allowed, but their dimensions must obey the
  rules for C arrays: all dimensions (except the first one) must be integer
  constantes.
* Arrays may declare its first dimension as a variable, but this variable
  must be of type `int`, it must be passed as a parameter to the function, and
  it must come before the array in the parameter list.
* The parameters that are used as array dimensions must declare the `Range`
  attribute, with the minimum and maximum values that the parameter might take.
  This is used to know the maximum size in bytes that the message needs
  allocated so that the arrays fit in them.
* If you expect that a function might legitimately exit the process (because it
  is the function that is called when the correct answer is reached, or because
  it might detect an error on the contestant's data), the function must possess
  the `NoReturn` attribute to avoid causing the other process to get confused
  about exiting when it stops receiving information mid-call.
* If you are using the `transact` kernel module to speed up IPC calls, you can
  add the ShmSize attribute to the interface to explicitly specify the size of
  the shared memory region if you need a value different from the 64k default.

# Sample .idl files

[Las jarras de agua](https://omegaup.com/arena/problem/jarras/)
    
    interface Main {
     void verterAgua(int fuente, int destino);
    };
    
    interface jarras {
        void programa(int objetivo, int capacidadJarra1, int capacidadJarra2);
    };

[Factories](http://cms.ioi-jp.org/open-2014/data/2014-open-d1-factories-en.pdf)

    interface Main {
    };
    
    interface factories {
        void Init([Range(2, 500000)] int N, int[N] A, int[N] B, int[N] D);
        long Query([Range(1, 499999)] int S, int[S] X, [Range(1, 499999)] int T, int[T] Y);
    };

[Parrots](http://www.ioi2011.or.th/hsc/tasks/EN/parrots.pdf)

    interface Main {
        void send([Range(0, 65535)] int n);
        void output([Range(0, 255)] int n);
    };
    
    interface encoder {
        void encode([Range(0, 64)] int N, int[N] M);
    };
    
    interface decoder {
        void decode([Range(0, 64)] int N, [Range(0, 320)] int L, int[L] X);
    };

[Cave](http://www.ioi2013.org/wp-content/uploads/tasks/day2/cave/cave.pdf)

    interface Main {
        [NoReturn] int tryCombination([Range(0, 5000)] int N, int[N] S);
        [NoReturn] void answer([Range(0, 5000)] int N, int[N] S, int[N] D);
    };

    interface cave {
        void exploreCave(int N);
    };

# IDL Grammar

IDL is almost a subset of [WebIDL](http://www.w3.org/TR/2012/WD-WebIDL-20120207/),
but with some syntax to help programming contests.

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
        = { interface-attribute }, "interface", ident, "{", { function }, "}", ";"
        ;
    
    function
        = { function-attribute }, return-type, ident,
          "(", param-list , ")", ";"
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
    
    interface-attribute
        = "[", shmsize-attribute, "]"
        ;
    
    shmsize-attribute
        = "ShmSize", "(", number, ")"
        ;
    

    function-attribute
        = "[", noreturn-attribute, "]"
        ;
    
    noreturn-attribute
        = "NoReturn"
        ;

    param-attribute
        = "[", range-attribute, "]"
        ;
    
    range-attribute
        = "Range", "(", expression, ",", expression, ")"
        ;
