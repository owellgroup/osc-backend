package com.osc.store.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Used for categories and brands. */
public record NamedRequest(@NotBlank(message = "Name is required") @Size(max = 150) String name,
                           @Size(max = 500) String description) {
}