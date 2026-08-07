package test;

public class App {

    static int fact(int n) {
        if (n == 0) {
            return 1;
        }
        return n * fact(n - 1);
    }

    public static void main(String[] args) {
        int seed = 5;
        int out = fact(seed);
    }
}
