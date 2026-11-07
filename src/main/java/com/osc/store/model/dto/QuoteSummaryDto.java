package com.osc.store.model.dto;

import com.osc.store.model.CustomerType;
import com.osc.store.model.QuoteStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record QuoteSummaryDto(Long id, String reference, CustomerType customerType, String customerName,
                              String email, QuoteStatus status, int productCount, int solutionCount,
                              int totalQuantity, BigDecimal total, Instant createdAt, Instant processedAt) {
}