package app.repo;

import app.domain.Loan;

public interface LoanRepository {

    Loan findById(Long id);

    Loan save(Loan loan);
}
