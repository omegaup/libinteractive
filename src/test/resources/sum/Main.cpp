#include <stdio.h>
#include <stdlib.h>
#include "sum.h"

int main(int argc, char* argv[]) {
	const int N = 1024 * 1024;
	int x[N];
	for (int i = 0; i < N; i++) {
		x[i] = i;
	}
	printf("%lld\n", solve(N, x));
}
