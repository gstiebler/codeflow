/*
 * A type parameter is a type like any other: the value put in through the constructor is the one
 * the getter returns, whatever T was bound to.
 */
package test;

class Holder<T> {
    private T held;

    Holder(T initial) {
        held = initial;
    }

    T get() {
        return held;
    }
}

public class App {
    public static void main(String[] args) {
        final Holder<String> holder = new Holder<>("payload");
        final String taken = holder.get();
        final int size = taken.length();
    }
}
