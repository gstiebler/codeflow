package test;

class Base {
    int baseMember;

    public Base(int init) {
        this.baseMember = init;
    }
}

class Derived extends Base {
    int derivedMember;

    public Derived() {
        super(10);
        this.derivedMember = 20;
    }

    public int total() {
        return baseMember + derivedMember;
    }
}

public class App {
    public static void main(String[] args) {
        Derived derived = new Derived();
        int result = derived.total();
    }
}
