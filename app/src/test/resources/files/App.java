package files;

public class App {
    public static void main(String[] args) {
        ClassX x = new ClassX();
        ClassY y = new ClassY();
        ClassX zClass = new ClassX();
        zClass.memberX = 6;
        x.memberX = 5;
        y.memberY = x.memberX;
        ClassY y2 = y;
        int z = y2.memberY;
    }
}
