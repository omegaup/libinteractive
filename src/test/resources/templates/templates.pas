unit templates;

{
	unit Main;
	function callback(mat: T_3_3_LongInt): Double;
}

interface
type
	T_3_3_LongInt = array [0..2] of array [0..2] of LongInt;
	T_Array_3_LongInt = array of array [0..2] of LongInt;
	T_Array_LongInt = array of LongInt;
	function a(n: LongInt; l: T_Array_3_LongInt): Double;
	function b(n: LongInt; m: T_Array_LongInt): LongInt;
implementation

uses Main;

function a(n: LongInt; l: T_Array_3_LongInt): Double;
begin
	// FIXME
	a := 0.0;
end;

function b(n: LongInt; m: T_Array_LongInt): LongInt;
begin
	// FIXME
	b := 0;
end;

end.

