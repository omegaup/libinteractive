# libinteractive

Un pequeño traductor de IDL que crea adaptadores que hacen llamadas entre
procesos para crear problemas interactivos fácilmente.

Para evitar tener que escribir un programa en cada uno de los lenguajes
soportados y asegurarse que no tengan bugs y no tengan comportamiento
indefinido que pueda hacer que se emita un veredicto incorrecto, libinteractive
te permite tener ambas implementaciones en procesos (y lenguajes) separados.

### Ejemplo

Digamos que tienes un problema llamado `sumas`. Tú como juez implementas un
validador en un archivo `Main.cpp` y el competidor codifica la solución en
`sumas.py`, puedes hacer:

``` console
$ ls
Main.cpp sumas.py sumas.idl input

$ cat Main.cpp
#include <stdio.h>
#include "sumas.h"

int main(int argc, char* argv[]) {
    int a, b;
    scanf("%d %d\n", &a, &b);
    printf("%d\n", sumas(a, b));
}

$ cat sumas.py
def sumas(a, b):
    print 'Hola, mundo!'
    return a + b
    
$ cat sumas.idl
interface Main {
}

interface sumas {
    int sumas(int a, int b);
}

$ java -jar libinteractive.jar generate sumas.idl cpp py --makefile
$ make run < input
[Main] 3
[ sum] Hola, mundo!

Memory:   5.023 MB
Time:     0.011 s
```

También puedes agregar la bandera `--verbose` a libinteractive para imprimir
mensajes de depuración cada vez que se realiza una llamada entre los procesos.

# Descarga

Puedes descargar el .jar más reciente de la [página de descargas](https://github.com/omegaup/libinteractive/releases).
