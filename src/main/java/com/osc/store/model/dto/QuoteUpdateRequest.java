package com.osc.store.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** OSC's pricing and terms for a quote. */
public record QuoteUpdateRequest(
        @DecimalMin(value = "0", message = "Delivery fee cannot be negative") BigDecimal deliveryFee,
        @DecimalMin(value = "0", message = "Discount cannot be negative") BigDecimal discount,
        @DecimalMin(value = "0", message = "VAT cannot be negative") BigDecimal vatPercent,
        LocalDate validUntil,
        @Size(max = 1000) String warranty,
        @Size(max = 1000) String deliveryTimeline,
        @Size(max = 1000) String availability,
        @Size(max = 1000) String sourcing,
        @Size(max = 1000) String paymentTerms,
        String additionalNotes,
        @Valid List<ItemUpdate> items) {

    public record ItemUpdate(Long id,
                             @DecimalMin(value = "0", message = "Prices cannot be negative") BigDecimal unitPrice,
                             @Size(max = 255) String availability,
                             @Size(max = 255) String warranty,
                             @Size(max = 255) String deliveryTimeline,
                             @Size(max = 255) String sourcing,
                             @Size(max = 1000) String notes) {
    }
}