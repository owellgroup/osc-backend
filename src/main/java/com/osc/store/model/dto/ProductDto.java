package com.osc.store.model.dto;

import com.osc.store.model.ProductType;

import java.util.List;

public record ProductDto(Long id, String name, String sku, ProductType type, String summary, String description,
                         Long categoryId, String categoryName, Long brandId, String brandName,
                         List<String> images, List<SpecDto> specifications, List<OptionDto> options,
                         boolean featured, boolean active) {
}