package com.osc.store.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** A line on a quote. Product details are snapshotted so later catalogue edits don't change old quotes. */
@Entity
@Table(name = "quote_items")
@Getter
@Setter
@NoArgsConstructor
public class QuoteItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quote_id")
    private Quote quote;

    private Long productId;

    @Column(nullable = false)
    private String name;

    private String sku;
    private String brandName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductType type;

    @Column(length = 500)
    private String imagePath;

    /** Human readable selected options, e.g. "Colour: Black, Memory: 16GB". */
    @Column(length = 1000)
    private String selectedOptions;

    @Column(nullable = false)
    private int quantity = 1;

    // ---- filled in by OSC
    @Column(precision = 14, scale = 2)
    private BigDecimal unitPrice;
    @Column(precision = 14, scale = 2)
    private BigDecimal lineTotal;
    private String availability;
    private String warranty;
    private String deliveryTimeline;
    private String sourcing;
    @Column(length = 1000)
    private String notes;
}
