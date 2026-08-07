/*
 * `super.f(...)` inside the override, which must NOT dispatch.
 *
 * The receiver of a `super` call is the same object as `this`, so dispatching on the object would
 * send `super.f(x)` straight back to `Sub.f` - the method it is written inside. `isBeingInlined`
 * would catch the recursion and draw an opaque box, so the failure looks like a limit of the
 * sources rather than like the analysis having gone wrong. `Base.f` is what runs and `+` is what
 * must be on the diagram.
 */
package overriddenSuper;

class Base {
    int f(int x) {
        return x + 1;
    }
}

class Sub extends Base {
    @Override
    int f(int x) {
        return super.f(x) * 100;
    }
}

public class App {
    public static void main(String[] args) {
        Base b = new Sub();
        int out = b.f(7);
    }
}
