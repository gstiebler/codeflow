package files;

public class App {
    public static void main(String[] args) {
        ClassX x = new ClassX();
        ClassY y = new ClassY();
        x.memberX = 5;
        y.memberY = x.memberX;
    }
}
