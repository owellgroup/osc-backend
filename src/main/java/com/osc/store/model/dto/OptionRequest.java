package com.osc.store.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record OptionRequest(@NotBlank(message = "Option name is required") String name,
                            @NotEmpty(message = "Add at least one value") List<String> values) {
}