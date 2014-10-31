# Ejecutar programas de libinteractive

libinteractive es una manera sencilla de hacer problemas interactivos. El
proceso para compilar, ejecutar y probar los programas es ligeramente distinto
al que estás acostumbrado, pero es sencillo. Los pasos específicos dependen del
sistema operativo que estés usando:

### Windows

* Extrae todos los archivos a una carpeta, abre la consola y dirígete a la
  carpeta donde extrajiste los archivos.
* Debes tener instalado [CodeBlocks](http://www.codeblocks.org/downloads/binaries#windows)
  (asegúrate de bajar la versión que dice MinGW) y haberlo ejecutado al menos
  una vez.
    * Si quieres usar Java, debes tener instalado el
      [JDK](http://www.oracle.com/technetwork/java/javase/downloads/).
    * Si quieres usar Pascal, debes tener instalado
      [Lazarus](http://www.lazarus.freepascal.org/index.php?page=downloads) y
      haberlo ejecutado al menos una vez.
    * Si quieres usar Python, debes tener instalado [Python 2.7](https://www.python.org/downloads/)
* Escribe `run` (o `run.bat`) para compilar todos los programas que necesitas y
  posteriormente ejecutar tu código. Recuerda que el validador espera una
  entrada en la consola, así que debes ya sea escribirla o pasársela como
  archivo (`run < entrada.in`).
* Para probar tu solución con un caso de prueba, escribe `test` (o `test.bat`).
  Esto es equivalente a correr `run.bat < examples\sample.in`.

### Linux/Mac OS X

* Extrae todos los archivos a una carpeta y abre la terminal y dirígete a la
  carpeta donde extrajiste los archivos.
* Se recomienda que tengas instalados los siguientes paquetes: `make`, `gcc`,
  `g++`, `fpc`, `python` y `openjdk-7-jdk`.
* Escribe `make` para compilar todos los programas que necesitas y `make run`
  para ejecutar tu código. Recuerda que el validador espera una entrada en la
  consola, así que debes ya sea escribirla o pasársela como archivo (`make run
  < entrada.in`).
* Para probar tu solución con un caso de prueba, escribe `make test`. Esto es
  equivalente a correr `make run < examples/sample.in`.

# Notas generales

* Para resolver el problema, debes enviar únicamente el archivo que te indica
  la página. No envíes ningún otro archivo o resultará en un error de
  compilación.
* Algunos problemas tendrán más casos de ejemplo en la carpeta `examples` además
  de `sample.in`. Intenta hacer que tu código pase con todos esos casos. Puedes
  probarlo corriendo ya sea `run < examples/archivo.in` o `make run < examples/archivo.in`,
  depende de tu sistema operativo.
* Para obtener todos los puntos de un problema, tu programa debe poder resolver
  todos los casos posibles. Intenta pensar en casos de ejemplo adicionales a
  los que se encuentren en la plantilla, corre tu código con ellos y verifica
  que de el resultado correcto. Mucha suerte!
