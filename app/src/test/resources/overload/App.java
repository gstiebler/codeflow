/*
 * Two methods with one name, told apart by their parameters.
 *
 * Resolution by name cannot see the difference, so both call sites inlined whichever body was
 * registered last and the diagram showed a value flowing through code that never ran on it.
 */
package test;

public class App {
    public static void main(String[] args) {
        App app = new App();
        app.run();
    }

    private void run() {
        int number = 5;
        int doubled = twice(number);
        int measured = twice("abc");
    }

    private int twice(int value) {
        int scaled = value * 2;
        return scaled;
    }

    private int twice(String text) {
        int counted = text.length();
        return counted;
    }
}
