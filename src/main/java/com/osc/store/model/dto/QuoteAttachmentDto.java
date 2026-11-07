package com.osc.store.model.dto;

public record QuoteAttachmentDto(Long id, String name, String contentType, long sizeBytes) {
}