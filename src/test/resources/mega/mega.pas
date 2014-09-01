unit mega;

interface
type
	T_Array_3_LongInt = array of array [0..2] of LongInt;
	T_Array_LongInt = array of LongInt;
	procedure encode(n: LongInt; l: T_Array_3_LongInt);
	procedure decode(n: LongInt; l: T_Array_LongInt);
	function solve(a: SmallInt; b: LongInt; c: Int64; d: Single; e: Double): Double;
implementation

uses Main;

procedure encode(n: LongInt; l: T_Array_3_LongInt);
var
	i: LongInt;
	j: LongInt;
begin
	for i := 0 to n - 1 do begin
		for j := 0 to 2 do begin
			l[i][j] *= 2;
		end;
	end;
	Main.send(n, l);
end;

procedure decode(n: LongInt; l: T_Array_LongInt);
var
	i: LongInt;
begin
	for i := 0 to n - 1 do begin
		l[i] *= 2;
	end;
	Main.output(n, l);
end;

function solve(a: SmallInt; b: LongInt; c: Int64; d: Single; e: Double): Double;
begin
	solve := a + b + c + d + e;
end;

end.
