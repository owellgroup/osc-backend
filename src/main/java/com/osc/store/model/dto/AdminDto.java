package com.osc.store.model.dto;

import com.osc.store.model.AdminRole;

import java.time.Instant;

public record AdminDto(Long id, String name, String email, AdminRole role, boolean protectedAccount, Instant createdAt) {
}