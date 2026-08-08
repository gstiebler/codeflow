/**
 * The entry point for the inherited-field fixture. `Report` is where the interesting part is.
 */
public class App {

    public static void main(String[] args) {
        Report first = new Report();
        Report second = new Report();
        String description = first.describe(second);
    }
}
