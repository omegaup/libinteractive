public class sum {
	public static long solve(int n, int[] l) {
		long total = 0;
		for (int i = 0; i < n; i++) {
			total += l[i];
		}
		return total;
	}
}
