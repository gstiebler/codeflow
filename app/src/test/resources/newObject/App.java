package newObject;

class Box {
    int held;

    Box(int v) {
        held = v;
    }
}

public class App {
    public static void main(String[] args) {
        int seed = 4;
        Box box = new Box(seed);
        StringBuilder sb = new StringBuilder("text");
        int read = box.held;
    }
}
