package com.osc.store.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Password is required when creating an admin and optional (blank = unchanged) when updating. */
public record AdminRequest(@NotBlank(message = "Name is required") String name,
                           @NotBlank(message = "Email is required") @Email(message = "Enter a valid email") String email,
                           String password) {
}