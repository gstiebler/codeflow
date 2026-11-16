package org.springframework.data.jpa.repository;

import java.util.List;
import java.util.Optional;

/**
 * Signatures only, for codeflow: Spring Data is not in Fineract's sources, so without this javac
 * cannot resolve `repository.save(x)` at all and the call is an anonymous EXTERNAL box. Declared here
 * (all on one interface, which the real library spreads over CrudRepository and friends) and named
 * `--boundary`, each one is drawn as `call save`, in order, like every other boundary. No body is
 * ever read, so none is written.
 */
public interface JpaRepository<T, ID> {
    <S extends T> S save(S entity);
    <S extends T> S saveAndFlush(S entity);
    <S extends T> List<S> saveAll(Iterable<S> entities);
    <S extends T> List<S> saveAllAndFlush(Iterable<S> entities);
    Optional<T> findById(ID id);
    List<T> findAll();
    List<T> findAllById(Iterable<ID> ids);
    T getReferenceById(ID id);
    boolean existsById(ID id);
    long count();
    void delete(T entity);
    void deleteById(ID id);
    void deleteAll(Iterable<? extends T> entities);
    void flush();
}
