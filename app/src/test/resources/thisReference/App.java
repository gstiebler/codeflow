package thisReference;

class Wrapper {
    int held;

    Wrapper(Source origin) {
        held = origin.value;
    }
}

class Source {
    int value;

    Source(int seed) {
        value = seed;
    }

    int wrapAndRead() {
        Wrapper wrapper = new Wrapper(this);
        return wrapper.held;
    }
}

public class App {
    public static void main(String[] args) {
        Source source = new Source(7);
        int out = source.wrapAndRead();
    }
}
