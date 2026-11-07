package com.osc.store.model.dto;

import java.util.List;

public record OptionDto(Long id, String name, List<String> values) {
}