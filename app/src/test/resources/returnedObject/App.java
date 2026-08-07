package returnedObject;

class Amount {
    int held;

    Amount(int seed) {
        held = seed;
    }

    static Amount of(int seed) {
        return new Amount(seed);
    }

    int read() {
        return held;
    }
}

public class App {
    public static void main(String[] args) {
        Amount amount = Amount.of(3);
        int out = amount.read();
    }
}
