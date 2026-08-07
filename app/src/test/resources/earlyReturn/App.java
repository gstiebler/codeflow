/*
 * A method leaves by whichever `return` runs, so every one of them produces the method's value.
 * Keeping only the last would draw a graph in which the guard clauses do nothing.
 */
package test;

public class App {
    static int classify(int score) {
        if (score > 90) return 100;
        if (score > 50) return 55;

        final int floor = 10;
        final int scale = 2;
        return floor * scale;
    }

    public static void main(String[] args) {
        final int result = classify(70);
    }
}
