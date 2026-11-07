package com.osc.store.repository;

import com.osc.store.model.Quote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface QuoteRepository extends JpaRepository<Quote, Long>, JpaSpecificationExecutor<Quote> {

    Optional<Quote> findByReference(String reference);

    long countByStatus(com.osc.store.model.QuoteStatus status);
}
