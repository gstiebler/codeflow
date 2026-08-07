package nestedClass;

public class App {
    static class Helper {
        int twice(int v) {
            return v + v;
        }
    }

    public static void main(String[] args) {
        Helper helper = new Helper();
        int seed = 21;
        int doubled = helper.twice(seed);
    }
}
