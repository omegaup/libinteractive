# Cómo escribir problemas

Primero debes escribir un archivo .idl con la descripción de las interfaces
(funciones definidas en el programa del juez y concursante). También debes
escribir un programa (de preferencia en C/C++, por eficiencia) que lea el caso
de entrada estándar, invoque las funciones que el concursante debe implementar
y finalmente escriba en salida estándar ya sea la salida del programa o el
puntaje final que el concursante obtuvo en ese caso (si se utiliza el validador
`literal` en omegaUp). Finalmente, es necesario correr libinteractive para que
genere todos los archivos necesarios para poder probar la solución, de esta manera:

    java -jar libinteractive.jar generate <archivo.idl> <lenguaje del juez> <lenguaje de la solución> --makefile

Donde el lenguaje es uno de `c`, `cpp`, `pas`, `py`, `java`. `pas`
no está soportado como lenguaje para el programa del juez, solo
del concursante

Es necesario crear un directorio llamado `examples` (en minúsculas) y colocar
un caso de ejemplo llamado `sample.in` en ese directorio. De ser necesario, es
posible colocar más casos de ejemplo en ese directorio y se copiarán
automáticamente a las plantillas para que los concursantes puedan descargarlas.

Por último, hay que implementar la solución y correr `make test` para verificar
que todo funciona. De ser necesario generar una implementación alternativa del
programa del juez que se pueda distribuir a los concursantes (por si el
programa del juez contiene la solución o pistas que divulgan cómo llegar a la
solución), se puede agregar un segundo archivo con `.distrib` antes de la
extensión. Por ejemplo, si el programa del juez está en el archivo `Main.cpp`,
el programa público del juez se puede colocar en el archivo `Main.distrib.cpp`
y únicamente este se distribuiría en las plantillas a los concursantes.

Una vez que se está contento con el problema, se debe convertir a formato
omegaUp para subirlo. Para hacerlo, deben existir las siguientes carpetas:

* `cases`, con los casos oficiales. Las entradas deben estar en archivos con
	extensión `.in` y las salidas en archivos con el mismo nombre pero
	terminación `.out`.
* `statements` con las redacciones. Las redacciones se deben escribir en formato
	Markdown, y debe haber una por lenguaje. Por ejemplo, para un problema en
	inglés y español, deben existir dos archivos: `es.markdown` y `en.markdown`.
	Para incluir el control para descargar las plantillas, los archivos `.markdown`
	deben contener la cadena `{{libinteractive:download}}` en un renglón, sin texto
	extra.
* `interactive` con el problema interactivo. Aquí solo debe estar el archivo .idl,
	el programa del juez (`Main.cpp`) y la carpeta `examples` con `sample.in` y
	cualquier otro caso de entrada necesario. Opcionalmente puede estar el
	archivo `Main.distrib.cpp` si `Main.cpp` tiene parte de la solución. No se debe
	incluir el Makefile, la solución de prueba o la carpeta libinteractive.

Por último, esas tres carpetas deben guardarse en un archivo .zip sin carpetas
intermedias. Las plantillas que se distribuirán a los concursantes se generarán
de manera automática.

# Convenciones

* La primer interfaz en el .idl (el programa del juez) debe llamarse `Main`. El
  programa del juez, entonces, debe estar en un archivo llamado `Main.cpp` (o
  la extensión que utilice el lenguaje de programación en el que se escribe).
* El programa del concursante debe estar en un archivo con el mismo nombre que
	el archivo .idl, pero con la extensión apropiada para ese lenguaje de
	programación. Por ejemplo, para el problema `sumas`, la solución del
	concursante en `sumas.cpp`.
* Cada interfaz producirá un ejecutable distinto, y se correrán en procesos
	separados. Eso quiere decir que ninguna variable se puede compartir, así que
	es necesario pasarlas como parámetros a funciones.
* Todas las interfaces se pueden comunicar con Main, y Main se puede comunicar
	con todas las demás interfaces, pero dos interfaces del concursante no se
	pueden comunicar entre sí.
* Los arreglos como tipos de parámetros de función son permitidos, pero sus
  dimensiones deben obedecer las reglas de los arreglos en C: todas las
	dimensiones (excepto la primera) deben ser constantes enteras.
* Los arreglos pueden declarar su primera dimensión como una variable, pero
  esta variable debe aparecer como parámetro en la misma función, y debe
	aparecer antes en la lista de parámetros.
* Los parámetros que sean utilizados como dimensiones de arreglos deben
  declarar el atributo `Range`, con los valores mínimo y máximo que puede
	tomar ese parámetro. Esto se utiliza para saber de antemano qué tan grande
	puede ser la memoria que es necesario alojar para que quepa el arreglo
	entero.

# Ejemplos de archivos .idl

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

# Gramática IDL

IDL es casi un subconjunto de WebIDL, pero con un poco de sintaxis extra para
concursos de programación:

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
