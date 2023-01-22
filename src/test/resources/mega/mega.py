#!/usr/bin/python

"""The Python contestant code for mega."""

import Main

def encode(num_rows, matrix):
    """Encodes the matrix."""
    for i in range(num_rows):
        for j in range(num_rows):
            matrix[i][j] *= 2
    Main.send(num_rows, matrix)

def decode(num_rows, encoded):
    """Decodes the matrix."""
    for i in range(num_rows):
        encoded[i] *= 2
    Main.output(num_rows, encoded)

def solve(num_a, num_b, num_c, num_d, num_e):
    """Sums a bunch of numbers."""
    return num_a + num_b + num_c + num_d + num_e

# vim: set ts=4 sw=4 et :
