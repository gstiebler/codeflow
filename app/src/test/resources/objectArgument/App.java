package objectArgument;

class Source {
    int value;

    Source(int seed) {
        value = seed;
    }
}

class Reader {
    static int read(Source origin) {
        return origin.value;
    }
}

public class App {
    public static void main(String[] args) {
        Source source = new Source(7);
        int out = Reader.read(source);
    }
}
