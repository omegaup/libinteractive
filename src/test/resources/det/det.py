def det(n, mat):
    total = 0
    total += mat[0][0] * mat[1][1] * mat[2][2]
    total += mat[0][1] * mat[1][2] * mat[2][0]
    total += mat[0][2] * mat[1][0] * mat[2][1]
    total -= mat[0][2] * mat[1][1] * mat[2][0]
    total -= mat[0][0] * mat[1][2] * mat[2][1]
    total -= mat[0][1] * mat[1][0] * mat[2][2]
    return total
