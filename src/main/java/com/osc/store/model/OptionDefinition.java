package com.osc.store.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * An "available option" an admin can attach to products, e.g. Colour: Black / White,
 * or Memory: 8GB / 16GB / 32GB. Customers pick one value per option when adding to the basket.
 */
@Entity
@Table(name = "option_definitions")
@Getter
@Setter
@NoArgsConstructor
public class OptionDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @ElementCollection
    @CollectionTable(name = "option_values", joinColumns = @JoinColumn(name = "option_id"))
    @OrderColumn(name = "position")
    @Column(name = "option_value", nullable = false)
    private List<String> optionValues = new ArrayList<>();
}
