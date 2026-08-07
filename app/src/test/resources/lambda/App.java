package lambda;

import java.util.function.IntUnaryOperator;

public class App {
    public static void main(String[] args) {
        int base = 3;
        IntUnaryOperator scale = value -> value * base;
        int out = scale.applyAsInt(4);
    }
}
