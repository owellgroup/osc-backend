package com.osc.store.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductSpec {

    @Column(name = "spec_label", nullable = false)
    private String label;

    @Column(name = "spec_value", nullable = false, length = 1000)
    private String value;
}
