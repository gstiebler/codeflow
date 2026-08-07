/*
 * Three ways to reach a method declared on a superclass: through `super`, unqualified, and as an
 * inherited static. All three have a body in the analysed sources, so all three are inlined.
 */
package test;

class Parent {
    int offset;

    int shift(int amount) {
        return amount + offset;
    }

    static int scale(int factor) {
        return factor * 3;
    }
}

class Child extends Parent {
    int adjust(int input) {
        offset = 5;
        final int shifted = super.shift(input);
        final int scaled = scale(shifted);
        return scaled;
    }
}

public class App {
    public static void main(String[] args) {
        final Child child = new Child();
        final int out = child.adjust(30);
    }
}
