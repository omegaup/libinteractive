#!/usr/bin/python

import mega
import sys

def send(n, l):
	print>>sys.stderr, l
	summed = [sum(row) for row in l]
	mega.decode(n, summed)

def output(n, l):
	for i in xrange(n):
		print(l[i])

if __name__ == '__main__':
	print("%.2f" % mega.solve(1, 2, -3, 4.25, 5.75))
	n = 3
	l = [range(3*row, 3*(row+1)) for row in xrange(n)]
	print>>sys.stderr, l
	mega.encode(n, l);
