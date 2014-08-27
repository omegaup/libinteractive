public class Main {
	public static void main(String[] args) {
		int n = 1024 * 1024;
		int[] l = new int[n];
		for (int i = 0; i < n; i++) {
			l[i] = i;
		}
		System.out.println(sum.solve(n, l));
	}
}
