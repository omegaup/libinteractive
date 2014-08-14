# libinteractive

A tiny IDL translator that creates IPC shims to easily create interactive problems.

You can grab the `.jar` from https://omegaup.com/libinteractive.jar

Let's say your problem's name is `test`. if you have your validator in `Main.cpp` and your solution in `test.cpp`, you can do

    $ ls
    Main.cpp test.cpp test.idl
    $ java -jar libinteractive.jar test.idl cpp cpp --makefile
    $ make
    $ ./run < input
    [Main] Hello, World!
    [Main] elapsed time: 41319ns
    
    Memory:  12.402 MB
    Time:     0.053 s 
    $

You can also add a --verbose flag to libinteractive to print a message every time an IPC call is made.
