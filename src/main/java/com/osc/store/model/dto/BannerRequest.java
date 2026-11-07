package com.osc.store.model.dto;

public record BannerRequest(String title,
                            String description,
                            String backgroundColor,
                            String backgroundColor2,
                            String backgroundStyle,
                            String imagePath,
                            String ctaText,
                            String linkUrl,
                            String ctaText2,
                            String linkUrl2,
                            Integer position,
                            boolean active) {
}
