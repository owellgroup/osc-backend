package com.osc.store.model.dto;

import com.osc.store.model.ProductType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProductRequest(@NotBlank(message = "Name is required") @Size(max = 255) String name,
                             String sku,
                             @NotNull(message = "Choose product or solution") ProductType type,
                             @Size(max = 500) String summary,
                             String description,
                             Long categoryId,
                             Long brandId,
                             List<String> images,
                             List<SpecDto> specifications,
                             List<Long> optionIds,
                             boolean featured,
                             boolean active) {
}