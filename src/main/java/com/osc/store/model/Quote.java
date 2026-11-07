package com.osc.store.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "quotes")
@Getter
@Setter
@NoArgsConstructor
public class Quote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 40)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerType customerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuoteStatus status = QuoteStatus.NEW;

    // ---- company details (only for COMPANY requests)
    private String companyName;
    private String registrationNumber;
    private String vatNumber;
    private String companyEmail;
    private String companyPhone;
    @Column(length = 500)
    private String companyAddress;
    private String companyLocation;

    // ---- the person (individual) or the representative submitting on behalf of the company
    @Column(nullable = false)
    private String contactName;
    @Column(nullable = false)
    private String contactEmail;
    private String contactPhone;
    private String contactJobTitle;

    // ---- delivery
    @Column(length = 500, nullable = false)
    private String deliveryAddress;
    private String deliveryLocation;

    @Column(columnDefinition = "TEXT")
    private String questions;

    // ---- OSC pricing & terms
    private String currency = "NAD";
    @Column(precision = 14, scale = 2)
    private BigDecimal productsSubtotal = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2)
    private BigDecimal solutionsSubtotal = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2)
    private BigDecimal deliveryFee = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;
    @Column(precision = 5, scale = 2)
    private BigDecimal vatPercent = new BigDecimal("15.00");
    @Column(precision = 14, scale = 2)
    private BigDecimal vatAmount = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    private LocalDate validUntil;
    @Column(length = 1000)
    private String warranty;
    @Column(length = 1000)
    private String deliveryTimeline;
    @Column(length = 1000)
    private String availability;
    @Column(length = 1000)
    private String sourcing;
    @Column(length = 1000)
    private String paymentTerms;
    @Column(columnDefinition = "TEXT")
    private String additionalNotes;

    // ---- lifecycle
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    private Instant processedAt;
    private String processedBy;
    private Instant emailSentAt;
    @Column(length = 500)
    private String emailError;

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<QuoteItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<QuoteAttachment> attachments = new ArrayList<>();

    public void addItem(QuoteItem item) {
        item.setQuote(this);
        items.add(item);
    }

    public void addAttachment(QuoteAttachment attachment) {
        attachment.setQuote(this);
        attachments.add(attachment);
    }

    /** Name used to address the customer. */
    public String customerDisplayName() {
        return customerType == CustomerType.COMPANY && companyName != null && !companyName.isBlank()
                ? companyName : contactName;
    }

    /** The address the quote and acknowledgement go to: company email for companies, personal email otherwise. */
    public String recipientEmail() {
        return customerType == CustomerType.COMPANY && companyEmail != null && !companyEmail.isBlank()
                ? companyEmail : contactEmail;
    }
}
