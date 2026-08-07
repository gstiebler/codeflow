/*
 * An unqualified call inside a superclass method, which MUST dispatch.
 *
 * `step(x)` inside `Base.template` is written with no receiver, so it runs on whatever object
 * `template` was entered on - and that is a `Sub`. One object is one MemPos across the whole chain,
 * which is what makes the subclass's `step` reachable from a method that never mentions `Sub`.
 */
package templateMethod;

class Base {
    int template(int x) {
        return step(x) + 1;
    }

    int step(int x) {
        return x;
    }
}

class Sub extends Base {
    @Override
    int step(int x) {
        return x * 100;
    }
}

public class App {
    public static void main(String[] args) {
        Base b = new Sub();
        int out = b.template(7);
    }
}
