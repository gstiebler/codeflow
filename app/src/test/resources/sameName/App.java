/*
 * The same method name on two unrelated classes.
 *
 * Keyed by name, both collapsed into one entry, so a call on an Account was drawn with
 * Connection's body - a graph of code that cannot run at that call site.
 */
package test;

class Account {
    int amount;

    int close() {
        int settled = amount - 1;
        return settled;
    }
}

class Connection {
    int amount;

    int close() {
        int flushed = amount + 2;
        return flushed;
    }
}

public class App {
    public static void main(String[] args) {
        Account account = new Account();
        account.amount = 10;
        int fromAccount = account.close();

        Connection connection = new Connection();
        connection.amount = 20;
        int fromConnection = connection.close();
    }
}
