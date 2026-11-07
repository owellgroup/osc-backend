package com.osc.store.model.dto;

import com.osc.store.model.ProductType;

import java.math.BigDecimal;

public record QuoteItemDto(Long id, Long productId, String name, String sku, String brandName, ProductType type,
                           String imagePath, String selectedOptions, int quantity,
                           BigDecimal unitPrice, BigDecimal lineTotal, String availability, String warranty,
                           String deliveryTimeline, String sourcing, String notes) {
}