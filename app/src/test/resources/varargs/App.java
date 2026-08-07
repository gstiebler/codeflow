package varargs;

class Adder {
    static int total(int base, int... rest) {
        int sum = base;
        for (int part : rest) {
            sum = sum + part;
        }
        return sum;
    }
}

public class App {
    public static void main(String[] args) {
        int out = Adder.total(1, 2, 3);
    }
}
