/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package test;

class ClassX {
    public int memberX;
}

public class App {
    public static void main(String[] args) {
        final int x = 5;
        final int y = methodA(x, 8);
        App app = new App();
        int e = app.methodB();
    }

    public static void methodA(int a, int b) {
        final int c = a + b;
        return c;
    }

    public int methodB() {
        int d = methodC(11);
        int f = methodC(13);
        return d;
    }

    public int methodC(int paramH) {
        int g = 6 / paramH;
        ClassX X1 = new ClassX();
        X1.memberX = g;
        ClassX X2 = X1;
        return X2.memberX;
    }
}
