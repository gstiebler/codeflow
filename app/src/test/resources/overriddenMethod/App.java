/*
 * A concrete method overridden by a subclass, reached through a variable of the superclass type.
 *
 * javac resolves `b.f(7)` to `Base.f`, because that is what the declared type of `b` offers. What
 * runs is `Sub.f`, because that is what `b` *is*. Inlining javac's answer drew `out` receiving
 * `7 + 1` for a program that returns 700 - complete, readable and wrong, with no EXTERNAL node and
 * no warning to say so.
 */
package overriddenMethod;

class Base {
    int f(int x) {
        return x + 1;
    }
}

class Sub extends Base {
    @Override
    int f(int x) {
        return x * 100;
    }
}

public class App {
    public static void main(String[] args) {
        Base b = new Sub();
        int out = b.f(7);
    }
}
