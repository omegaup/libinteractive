# Cómo funciona

libinteractive lee un archivo .idl, que es una descripción textual de las
funciones implementadas por el programa del juez y del concursante que se
pueden invocar uno desde el otro. A partir de este archivo, se pueden generar
todas las reglas necesarias para compilar los archivos y permitir que ambos
programas se comuniquen entre sí como si hubieran sido compilados en un solo
programa.

Una ventaja de libinteractive es que separa los códigos del concursante y el
juez en procesos distintos, así que ya no es necesario proteger la memoria
y la entrada estándar para evitar trampas. También es posible escribir ambas
implementaciones en lenguajes de programación distintos. Eso quiere decir que
tú como juez solo necesitas escribir una implementación y libinteractive se
encarga del resto.

# Detalles de implementación

Al leer el archivo .idl, libinteractive genera un Makefile (o un archivo .bat
en Windows) y el código necesario para que los programas del juez y del
concursante puedan invocar las funciones del otro sin problemas. Las funciones
que genera libinteractive serializan y deserializan los parámetros de la
función, emite un mensaje dirigido al otro proceso, y espera la respuesta (si
es que la función regresa algo) para regresarla como resultado de la función.

libinteractive utiliza [tuberías nombradas](http://es.wikipedia.org/wiki/Tuber%C3%ADa_(inform%C3%A1tica))
(named pipes) para lograr la comunicación entre procesos y la sincronización.
Los mensajes se componen de un identificador de función (autogenerado para cada
una de las funciones), los parámetros de la función serializados, seguido de un
cookie de verificación que se debe regresar tal cual para validar que el
mensaje fue transmitido correctamente. El formato binario específico que se
utiliza se muestra a continuación:

	message = function-id *field cookie
	function-id = int ; un entero de 32-bits que identifica la función a llamar
	cookie = int ; un entero de 32-bits que se utiliza como sentinela
	field = byte | short | int | float | long | double | array
	byte = UNSIGNED_CHAR
	short = SHORT
	int = INT
	float = INT ; IEEE-754 binary32
	double = LONG ; IEEE-754 binary64
	long = LONG
	array = *byte ; tantos bytes como sea necesario para representar el arreglo

Todos los enteros utilizan la codificación little-endian (la nativa en x86),
por lo que la implementación en C simplemente necesita invocar la llamada de
sistema `write` con un apuntador al parámetro a escribir con
`sizeof(parametro)` como longitud.

Todos los arreglos en el .idl deben ser arreglos válidos en C. Esto es, todas
las dimensiones del arreglo, excepto quizás la primera, deben ser constantes
numéricas en tiempo de compilación. La primer dimensión puede ser variable, en
cuyo caso debe pasarse como uno de los parámetros de tipo `int` a la función.

Como el orden y el tipo de los parámetros está determinado en el .idl (y por
ende, en tiempo de compilación), no es necesario escribir en el mensaje la
longitud del mismo, ya que el decodificador está generado de tal manera que
solo leerá la cantidad correcta de bytes de la tubería.

Finalmente, libinteractive también genera un Makefile/.bat para que los
concursantes puedan compilar el código sin preocuparse de los detalles, y
probar sus implementaciones con casos de ejemplo proveídos por el juez.
Uno de los archivos que compila el Makefile es un pequeño programa llamado
`run`, que genera los archivos necesarios para establecer las tuberías, ejecuta
los programas, redirige la entrada estándar al programa del juez (si es
necesario), y finalmente imprime la salida/error estándar de todos los
programas de manera ordenada para poder identificar de qué proceso viene.
Por último, al terminar todos los programas, imprime un resumen con la
cantidad de memoria residente máxima utilizada y el tiempo total consumido
por el código del concursante (user time).

# Desempeño

Al no compilar directamente ambos programas del juez y concursante en un mismo
proceso, lo que antes era una llamada de función (0-10 ciclos de CPU si la
solución está escrita en C, depende de si es posible realizar _inlining_ de la
función o no) ahora requiere realizar dos llamadas de sistema y dos cambios de
proceso que agrega 7-10 microsegundos al tiempo total de ejecución (wall time),
y alrededor de 2-3 microsegundos al tiempo de ejecución del concursante (user
time). Eso quiere decir que si un problema requiere hacer más de 400,000
llamadas a función entre los programas del juez y concursante, los programas
generados por libinteractive van a ocasionar que los envíos excedan los tiempos
límite. Estos tiempos extra son causados por el sistema operativo y no hay mucho
que se pueda hacer al respecto.

Si el problema no requiere un gran volumen de llamadas, libinteractive es una
excelente opción para hacer problemas interactivos.

# Escenarios soportados

Otros escenarios que son bastante eficientes hacerlos bajo libinteractive son:

* Pasar arreglos gigantes de millones de elementos entre el programa del juez y
	concursante es bastante más eficiente que leerlo de entrada estándar, así que
	eso disminuirá el tiempo del concursante.
* Tener problemas de varias fases (como Parrots en la IOI 2011) era bastante
  complicado antes pero es un escenario considerado en libinteractive.
* Autogenerar la entrada del concursante. Por ejemplo, una técnica utilizada
	comúnmente en el Facebook Hacker Cup es pedirle al concursante que genere la
	entrada utilizando un [generador de números aleatorios con congruencia linear](http://en.wikipedia.org/wiki/Linear_congruential_generator).
	Esto evita desperdiciar tiempo leyendo archivos gigantes de entrada estándar.
* Al ser separados los procesos, no es necesario esconder/encriptar la memoria.
  Esto permite que el programa del juez a su vez sea el validador y regrese la
	calificación.
