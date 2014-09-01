#include "mega.h"
#include <stdio.h>

int main(int argc, char* argv[]) {
	printf("%.2f\n", solve(1, 2, -3LL, 4.25f, 5.75));
	const int n = 3;
	int l[n][3];
	int counter = 0;
	for (int i = 0; i < n; i++) {
		for (int j = 0; j < 3; j++) {
			l[i][j] = counter++;
		}
	}
	encode(n, l);
}

void send(int n, int l[][3]) {
	int summed[n];
	for (int i = 0; i < n; i++) {
		summed[i] = 0;
		for (int j = 0; j < 3; j++) {
			summed[i] += l[i][j];
		}
	}
	decode(n, summed);
}

void output(int n, int l[]) {
	for (int i = 0; i < n; i++) {
		printf("%d\n", l[i]);
	}
}
