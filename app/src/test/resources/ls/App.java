class A {
    int _a;
}

class Main {
    public void main() {
        A a = new A();
        a._a = 5;
        A a2 = a;
        int b = a2._a;
    }
}