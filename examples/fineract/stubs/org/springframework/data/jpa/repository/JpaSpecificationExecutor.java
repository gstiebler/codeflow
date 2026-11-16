package org.springframework.data.jpa.repository;

import java.util.List;
import java.util.Optional;

/** Signatures only, for codeflow; see JpaRepository beside it. The specification is left untyped. */
public interface JpaSpecificationExecutor<T> {
    Optional<T> findOne(Object specification);
    List<T> findAll(Object specification);
    long count(Object specification);
    boolean exists(Object specification);
}
