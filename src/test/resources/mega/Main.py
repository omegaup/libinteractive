#!/usr/bin/python

"""The Python Main for mega."""

from __future__ import print_function

import sys
import mega

NUM_ROWS = 3

def send(num_rows, encoded):
    """Receives the encoded solution from the contestant's code."""
    print(encoded, file=sys.stderr)
    summed = [sum(row) for row in encoded]
    mega.decode(num_rows, summed)

def output(num_rows, solution):
    """Outputs the final solution."""
    for i in xrange(num_rows):
        print(solution[i])

def main():
    """Driver for the contestant's code."""
    print("%.2f" % mega.solve(1, 2, -3, 4.25, 5.75))
    matrix = [range(NUM_ROWS * row, NUM_ROWS * (row + 1))
              for row in xrange(NUM_ROWS)]
    print(matrix, file=sys.stderr)
    mega.encode(NUM_ROWS, matrix)

if __name__ == '__main__':
    main()

# vim: set ts=4 sw=4 et :
