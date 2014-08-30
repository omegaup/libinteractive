unit det;

interface
type
	T2Integer = array of array [0..2] of Integer;
  function det(n: LongInt; mat: T2Integer): Int64;
implementation

uses Main;

function det(n: LongInt; mat: T2Integer): Int64;
var
  total: Int64;
begin
	total := 0;
	total := total + mat[0][0] * mat[1][1] * mat[2][2];
	total := total + mat[0][1] * mat[1][2] * mat[2][0];
	total := total + mat[0][2] * mat[1][0] * mat[2][1];
	total := total - mat[0][2] * mat[1][1] * mat[2][0];
	total := total - mat[0][0] * mat[1][2] * mat[2][1];
	total := total - mat[0][1] * mat[1][0] * mat[2][2];
	det := total; 
end;

end.
