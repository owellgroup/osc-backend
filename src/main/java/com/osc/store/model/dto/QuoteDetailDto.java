package com.osc.store.model.dto;

import com.osc.store.model.CustomerType;
import com.osc.store.model.QuoteStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record QuoteDetailDto(
        Long id, String reference, CustomerType customerType, QuoteStatus status,
        String companyName, String registrationNumber, String vatNumber, String companyEmail, String companyPhone,
        String companyAddress, String companyLocation,
        String contactName, String contactEmail, String contactPhone, String contactJobTitle,
        String deliveryAddress, String deliveryLocation, String questions,
        String currency, BigDecimal productsSubtotal, BigDecimal solutionsSubtotal, BigDecimal deliveryFee,
        BigDecimal discount, BigDecimal vatPercent, BigDecimal vatAmount, BigDecimal total,
        LocalDate validUntil, String warranty, String deliveryTimeline, String availability, String sourcing,
        String paymentTerms, String additionalNotes,
        Instant createdAt, Instant processedAt, String processedBy, Instant emailSentAt, String emailError,
        List<QuoteItemDto> items, List<QuoteAttachmentDto> attachments) {
}