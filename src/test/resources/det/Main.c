#include <stdio.h>
#include "det.h"

int main(int argc, char* argv[]) {
	int mat[3][3] = {
		{ 1, 2, 3 },
		{ 4, 5, 6 },
		{ 7, 8, 10 }
	};
	printf("%lld\n", det(3, mat));
}
