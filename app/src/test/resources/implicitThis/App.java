package test;

class Counter {
    int value;
    int step;

    public Counter(int initial) {
        this.value = initial;
        this.step = 3;
    }

    // Both fields are read without an explicit `this`, and were written in the constructor,
    // which is not in this method's block parent chain.
    public int advance() {
        return value + step;
    }
}

public class App {
    public static void main(String[] args) {
        Counter counter = new Counter(10);
        int result = counter.advance();
    }
}
