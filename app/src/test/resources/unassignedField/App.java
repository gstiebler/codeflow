package unassignedField;

class Builder {
    int filled;
    int neverSet;

    Builder fill(int seed) {
        filled = seed;
        return this;
    }
}

class Built {
    int fromFilled;
    int fromNeverSet;

    Built(Builder builder) {
        fromFilled = builder.filled;
        fromNeverSet = builder.neverSet;
    }
}

public class App {
    public static void main(String[] args) {
        Builder builder = new Builder();
        Built built = new Built(builder.fill(4));
        int out = built.fromFilled;
    }
}
