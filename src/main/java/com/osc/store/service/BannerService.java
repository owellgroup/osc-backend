package com.osc.store.service;

import com.osc.store.config.ApiException;
import com.osc.store.model.Banner;
import com.osc.store.model.dto.BannerDto;
import com.osc.store.model.dto.BannerRequest;
import com.osc.store.repository.BannerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class BannerService {

    private static final Pattern HEX = Pattern.compile("^#([0-9A-Fa-f]{6})$");
    private static final Set<String> STYLES = Set.of(
            "solid", "linear-right", "linear-left", "linear-down", "linear-up",
            "diagonal", "radial", "glow", "wash");

    private final BannerRepository banners;
    private final FileStorageService storage;

    public BannerService(BannerRepository banners, FileStorageService storage) {
        this.banners = banners;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public List<BannerDto> listActive() {
        return banners.findByActiveTrueOrderByPositionAscIdAsc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<BannerDto> listAll() {
        return banners.findAllByOrderByPositionAscIdAsc().stream().map(this::toDto).toList();
    }

    @Transactional
    public BannerDto create(BannerRequest req) {
        if (banners.count() >= Banner.MAX_BANNERS) {
            throw ApiException.conflict("You can have up to " + Banner.MAX_BANNERS + " banners. Delete one to add another.");
        }
        Banner b = new Banner();
        apply(b, req);
        if (req.position() == null) {
            b.setPosition((int) banners.count());
        }
        return toDto(banners.save(b));
    }

    @Transactional
    public BannerDto update(Long id, BannerRequest req) {
        Banner b = banners.findById(id).orElseThrow(() -> ApiException.notFound("Banner"));
        String oldImage = b.getImagePath();
        apply(b, req);
        Banner saved = banners.save(b);
        if (oldImage != null && !oldImage.equals(saved.getImagePath())) {
            storage.deleteImageQuietly(oldImage);
        }
        return toDto(saved);
    }

    @Transactional
    public void delete(Long id) {
        Banner b = banners.findById(id).orElseThrow(() -> ApiException.notFound("Banner"));
        banners.delete(b);
        banners.flush();
        storage.deleteImageQuietly(b.getImagePath());
    }

    private void apply(Banner b, BannerRequest req) {
        String title = blankToNull(req.title());
        if (title == null) {
            throw ApiException.badRequest("Enter a title");
        }
        b.setTitle(title);
        b.setDescription(blankToNull(req.description()));
        b.setBackgroundColor(normalizeColor(req.backgroundColor(), Banner.DEFAULT_COLOR));
        b.setBackgroundColor2(normalizeColor(req.backgroundColor2(), null));
        b.setBackgroundStyle(normalizeStyle(req.backgroundStyle()));
        b.setImagePath(blankToNull(req.imagePath()));
        b.setCtaText(blankToNull(req.ctaText()));
        b.setLinkUrl(blankToNull(req.linkUrl()));
        b.setCtaText2(blankToNull(req.ctaText2()));
        b.setLinkUrl2(blankToNull(req.linkUrl2()));
        b.setActive(req.active());
        if (req.position() != null) {
            b.setPosition(req.position());
        }
    }

    private BannerDto toDto(Banner b) {
        return new BannerDto(
                b.getId(), b.getTitle(), b.getDescription(),
                b.getBackgroundColor() == null || b.getBackgroundColor().isBlank() ? Banner.DEFAULT_COLOR : b.getBackgroundColor(),
                b.getBackgroundColor2(),
                b.getBackgroundStyle() == null || b.getBackgroundStyle().isBlank() ? Banner.DEFAULT_STYLE : b.getBackgroundStyle(),
                b.getImagePath(), b.getCtaText(), b.getLinkUrl(), b.getCtaText2(), b.getLinkUrl2(),
                b.getPosition(), b.isActive());
    }

    private String normalizeColor(String raw, String fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String value = raw.trim();
        if (!value.startsWith("#") && value.length() == 6) value = "#" + value;
        if (!HEX.matcher(value).matches()) {
            throw ApiException.badRequest("Banner colour must be a hex value like #D71920");
        }
        return value.toUpperCase(Locale.ROOT);
    }

    private String normalizeStyle(String raw) {
        if (raw == null || raw.isBlank()) return Banner.DEFAULT_STYLE;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (!STYLES.contains(value)) {
            throw ApiException.badRequest("Unknown banner background style");
        }
        return value;
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
