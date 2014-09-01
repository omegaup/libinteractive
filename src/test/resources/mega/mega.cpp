#include "mega.h"

void encode(int n, int l[][3]) {
	for (int i = 0; i < n; i++) {
		for (int j = 0; j < 3; j++) {
			l[i][j] *= 2;
		}
	}
	send(n, l);
}

void decode(int n, int l[]) {
	for (int i = 0; i < n; i++) {
		l[i] *= 2;
	}
	output(n, l);
}

double solve(short a, int b, long long c, float d, double e) {
	return a + b + c + d + e;
}
