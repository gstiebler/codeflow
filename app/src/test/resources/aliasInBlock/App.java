/*
 * Two names for one object. A write through the alias inside a nested block is a write to the
 * object, so the read after the block finds it and not the value the object was given before.
 */
package test;

class Box {
    int value;
}

public class App {
    public static void main(String[] args) {
        final Box box = new Box();
        box.value = 1;

        {
            final Box alias = box;
            alias.value = 7;
        }

        final int read = box.value;
    }
}
