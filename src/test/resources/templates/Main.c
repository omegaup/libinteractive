#include "templates.h"
#include <math.h>
#include <stdio.h>

int main(int argc, char* argv[]) {
	int l[3][3];
	int m[3];
	printf("%.2f\n", a(3, l));
	printf("%d\n", b(3, m));
}

double callback(int l[][3]) {
	return M_PI;
}
