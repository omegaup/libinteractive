#!/usr/bin/python

import Main

def encode(n, l):
	for i in xrange(n):
		for j in xrange(3):
			l[i][j] *= 2
	Main.send(n, l)

def decode(n, l):
	for i in xrange(n):
		l[i] *= 2
	Main.output(n, l)

def solve(a, b, c, d, e):
	return a + b + c + d + e
