package enumConstant;

enum Size {
    SMALL, LARGE;

    static Size fromFlag(boolean big) {
        return big ? LARGE : Size.SMALL;
    }
}

public class App {
    public static void main(String[] args) {
        boolean big = true;
        Size chosen = Size.fromFlag(big);
    }
}
