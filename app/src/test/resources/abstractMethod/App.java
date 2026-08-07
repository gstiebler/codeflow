package abstractMethod;

interface Source {
    int read(int seed);
}

class Doubling implements Source {
    @Override
    public int read(int seed) {
        return seed + seed;
    }
}

public class App {
    public static void main(String[] args) {
        Source source = new Doubling();
        int viaInterface = source.read(3);
        Doubling direct = new Doubling();
        int viaClass = direct.read(4);
    }
}
