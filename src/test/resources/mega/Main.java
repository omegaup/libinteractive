public class Main {
	public static void main(String[] args) {
		System.out.printf("%.2f\n", Types.solve((short)1, 2, -3L, 4.25f, 5.75));
		int n = 3;
		int[][] l = new int[n][3];
		int counter = 0;
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < 3; j++) {
				l[i][j] = counter++;
			}
		}
		Encoder.encode(n, l);
	}

	public static void send(int n, int[][] l) {
		int[] summed = new int[n];
		for (int i = 0; i < n; i++) {
			summed[i] = 0;
			for (int j = 0; j < 3; j++) {
				summed[i] += l[i][j];
			}
		}
		Decoder.decode(n, summed);
	}

	public static void output(int n, int[] l) {
		for (int i = 0; i < n; i++) {
			System.out.println(l[i]);
		}
	}
}
