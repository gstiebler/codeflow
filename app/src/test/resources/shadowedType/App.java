/*
 * One name, two types: `total` is an int field of Holder and a Holder local in run().
 *
 * Whether a variable holds a value or a reference used to be recorded in a map keyed by bare name
 * with no scope at all, so the last declaration seen decided it for every `total` in the sources.
 */
package test;

class Holder {
    int total;
}

public class App {
    public static void main(String[] args) {
        App app = new App();
        app.run();
    }

    private void run() {
        Holder total = new Holder();
        total.total = 3;
        int copied = total.total;
    }
}
