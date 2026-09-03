package app.svc;

import app.domain.Loan;
import java.math.BigDecimal;
import java.util.List;

public class Svc {

    private BigDecimal total;

    private List<String> rules;

    public BigDecimal apply(Loan loan, BigDecimal amount) {
        this.total = amount;
        BigDecimal t = this.total;
        loan.setAmount(t);
        return t;
    }

    // No `throw` anywhere in here, and it throws NoSuchElementException. This is the shape of the
    // one new failure in Fineract 542da12e6.
    public String pick() {
        return rules.stream().findFirst().get();
    }

    // A fixed point in miniature: whatever collapses this has to collapse the EMI solver too. The
    // loop is the point - straight-line code that assigns a from b and then b from a is not a cycle,
    // it is two statements in a row.
    public int spin() {
        int a = 1;
        int b = 1;
        for (int i = 0; i < 10; i++) {
            a = b + 1;
            b = a + 1;
        }
        return a;
    }
}
