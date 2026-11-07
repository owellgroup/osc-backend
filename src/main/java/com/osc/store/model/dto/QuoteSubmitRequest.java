package com.osc.store.model.dto;

import com.osc.store.model.CustomerType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.Map;

public record QuoteSubmitRequest(
        @NotNull(message = "Choose company or individual") CustomerType customerType,

        @Size(max = 255) String companyName,
        @Size(max = 100) String registrationNumber,
        @Size(max = 100) String vatNumber,
        @Email(message = "Enter a valid company email") String companyEmail,
        @Size(max = 60) String companyPhone,
        @Size(max = 500) String companyAddress,
        @Size(max = 255) String companyLocation,

        @NotBlank(message = "Your name is required") @Size(max = 255) String contactName,
        @NotBlank(message = "Your email is required") @Email(message = "Enter a valid email") String contactEmail,
        @Size(max = 60) String contactPhone,
        @Size(max = 255) String contactJobTitle,

        @NotBlank(message = "Delivery address is required") @Size(max = 500) String deliveryAddress,
        @Size(max = 255) String deliveryLocation,

        @Size(max = 5000) String questions,

        @NotEmpty(message = "Your quote basket is empty") @Valid List<Item> items) {

    public record Item(@NotNull Long productId,
                       @Min(value = 1, message = "Quantity must be at least 1") @Max(100000) int quantity,
                       Map<String, String> options) {
    }
}