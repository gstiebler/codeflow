/*
 * `record()` inside `sample()` is `this.record()`, running on the object `sample` was called on.
 * The write it makes is a write to that object, so the caller's read after `gauge.sample()` has to
 * find it - two call levels down, with no receiver written at either.
 */
package test;

class Gauge {
    int reading;

    void record() {
        reading = 18;
    }

    void sample() {
        record();
    }
}

public class App {
    public static void main(String[] args) {
        final Gauge gauge = new Gauge();
        gauge.reading = 17;

        gauge.sample();

        final int taken = gauge.reading;
    }
}
