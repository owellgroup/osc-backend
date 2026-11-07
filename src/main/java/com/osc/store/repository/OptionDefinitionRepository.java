package com.osc.store.repository;

import com.osc.store.model.OptionDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OptionDefinitionRepository extends JpaRepository<OptionDefinition, Long> {

    List<OptionDefinition> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
