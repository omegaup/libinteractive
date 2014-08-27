unit sum;

interface
  function solve(n: Integer; l: array of Integer): Int64;
implementation

uses Main;

function solve(n: Integer; l: array of Integer): Int64;
var
	i: Integer;
  total: Int64;
begin
	total := 0;
	for i := 0 to n - 1 do begin
		total += l[i];
	end;
	solve := total; 
end;

end.
