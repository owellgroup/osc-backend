package com.osc.store.model.dto;

public record ProcessResponse(QuoteDetailDto quote, boolean emailSent, String emailError) {
}