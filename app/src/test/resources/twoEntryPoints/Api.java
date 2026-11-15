/*
 * Two entry points that never call each other, sharing one static field. `configure` writes it
 * and `quote` reads it, so a value passed to `configure` reaches what `quote` returns - but only
 * through the field, and only if `configure` ran first. Entry points run in any order, so the
 * graph has to say so without depending on which one was named first.
 */
package twoEntryPoints;

class Settings {
    static int rate;
}

public class Api {

    public static void configure(int newRate) {
        int doubled = newRate * 2;
        Settings.rate = doubled;
    }

    public static int quote(int amount) {
        int price = amount * Settings.rate;
        return price;
    }
}
