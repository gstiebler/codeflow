package cast;

class Shape {
    int sides() {
        return 0;
    }
}

class Square extends Shape {
    int edges = 4;

    @Override
    int sides() {
        return edges;
    }
}

public class App {
    public static void main(String[] args) {
        Shape shape = new Square();
        Square square = (Square) shape;
        int count = square.sides();
        int fromField = ((Square) shape).edges;
        double ratio = 7.9;
        int whole = (int) ratio;
    }
}
