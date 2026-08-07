package voidReturn;

class Sink {
    int held;

    void store(int seed) {
        if (seed < 0) {
            return;
        }
        held = seed;
    }
}

public class App {
    public static void main(String[] args) {
        Sink sink = new Sink();
        sink.store(5);
        int out = sink.held;
    }
}
