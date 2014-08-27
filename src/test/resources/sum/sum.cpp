#include "sum.h"

long long solve(int n, int l[]) {
	long long total = 0;
	for (int i = 0; i < n; i++) {
		total += l[i];
	}
	return total;
}
