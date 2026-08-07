package memberReference;

import java.util.List;
import java.util.function.Supplier;

public class App {
    public static void main(String[] args) {
        List<String> names = List.of("a");
        Supplier<Integer> counter = names::size;
        int out = counter.get();
    }
}
