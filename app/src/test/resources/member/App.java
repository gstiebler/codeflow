/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package test;


class ClassX {
    public int memberX;
}

class ClassY {
    public ClassX x;
}

public class App {

    int memberA;
    public static void main(String[] args) {
        int a = 5;
        a = 6;
        memberA = a;
        int b = memberA;

        ClassY y = new ClassY();
        y.x = new ClassX();
        y.x.memberX = 8;
        ClassY y1 = y;
        int c = y1.x.memberX;
        ClassX x1 = y1.x;
    }
}
