package enhancedFor;

import java.util.List;

public class App {
    public static void main(String[] args) {
        List<String> names = List.of("a");
        int total = 0;
        for (String name : names) {
            int size = name.length();
            total = total + size;
        }
    }
}
