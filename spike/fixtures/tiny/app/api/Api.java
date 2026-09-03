package app.api;

import app.domain.Loan;
import app.repo.LoanRepository;
import app.svc.Svc;
import java.math.BigDecimal;

@Path("/loans")
public class Api {

    private LoanRepository repo;

    private Svc svc;

    @POST
    @Path("{id}/charge")
    public String addCharge(Long id, BigDecimal amount) {
        Loan loan = repo.findById(id);
        BigDecimal t = svc.apply(loan, amount);
        repo.save(loan);
        return t.toString();
    }
}
