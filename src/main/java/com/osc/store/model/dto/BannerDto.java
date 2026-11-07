package com.osc.store.model.dto;

public record BannerDto(Long id,
                        String title,
                        String description,
                        String backgroundColor,
                        String backgroundColor2,
                        String backgroundStyle,
                        String imagePath,
                        String ctaText,
                        String linkUrl,
                        String ctaText2,
                        String linkUrl2,
                        int position,
                        boolean active) {
}
