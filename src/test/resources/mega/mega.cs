class Encoder
{
	public static void encode(int n, int[,] l)
	{
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < 3; j++) {
				l[i, j] *= 2;
			}
		}
		Main.send(n, l);
	}
}

class Decoder
{
	public static void decode(int n, int[] l)
	{
		for (int i = 0; i < n; i++) {
			l[i] *= 2;
		}
		Main.output(n, l);
	}
}

class Types
{
	public static double solve(short a, int b, long c, float d, double e)
	{
		return a + b + c + d + e;
	}
}
