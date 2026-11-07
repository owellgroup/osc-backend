package com.osc.store.service;

import com.osc.store.config.ApiException;
import com.osc.store.model.*;
import com.osc.store.model.dto.*;
import com.osc.store.repository.*;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
public class ProductService {

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final BrandRepository brands;
    private final OptionDefinitionRepository options;
    private final FileStorageService storage;

    public ProductService(ProductRepository products, CategoryRepository categories, BrandRepository brands,
                          OptionDefinitionRepository options, FileStorageService storage) {
        this.products = products;
        this.categories = categories;
        this.brands = brands;
        this.options = options;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductDto> search(String q, ProductType type, Long categoryId, Long brandId,
                                           Boolean featured, boolean includeInactive, int page, int size) {
        Specification<Product> spec = (root, cq, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (!includeInactive) {
                ps.add(cb.isTrue(root.get("active")));
            }
            if (type != null) {
                ps.add(cb.equal(root.get("type"), type));
            }
            if (categoryId != null) {
                ps.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (brandId != null) {
                ps.add(cb.equal(root.get("brand").get("id"), brandId));
            }
            if (featured != null) {
                ps.add(cb.equal(root.get("featured"), featured));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("sku"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("summary"), "")), like)));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<Product> result = products.findAll(spec,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "featured").and(Sort.by(Sort.Direction.DESC, "createdAt"))));
        return new PageResponse<>(result.getContent().stream().map(this::toDto).toList(),
                result.getTotalElements(), safePage, safeSize, result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ProductDto get(Long id, boolean includeInactive) {
        Product p = products.findById(id).orElseThrow(() -> ApiException.notFound("Product"));
        if (!includeInactive && !p.isActive()) {
            throw ApiException.notFound("Product");
        }
        return toDto(p);
    }

    @Transactional
    public ProductDto create(ProductRequest req) {
        Product p = new Product();
        apply(p, req, null);
        return toDto(products.save(p));
    }

    @Transactional
    public ProductDto update(Long id, ProductRequest req) {
        Product p = products.findById(id).orElseThrow(() -> ApiException.notFound("Product"));
        List<String> before = new ArrayList<>(p.getImages());
        apply(p, req, id);
        p.setUpdatedAt(Instant.now());
        Product saved = products.save(p);
        before.stream().filter(img -> !saved.getImages().contains(img)).forEach(storage::deleteImageQuietly);
        return toDto(saved);
    }

    @Transactional
    public void delete(Long id) {
        Product p = products.findById(id).orElseThrow(() -> ApiException.notFound("Product"));
        List<String> images = new ArrayList<>(p.getImages());
        products.delete(p);
        products.flush();
        images.forEach(storage::deleteImageQuietly);
    }

    // ------------------------------------------------------------------

    private void apply(Product p, ProductRequest req, Long existingId) {
        String sku = req.sku() == null || req.sku().isBlank() ? null : req.sku().trim();
        if (sku != null) {
            boolean taken = existingId == null
                    ? products.existsBySkuIgnoreCase(sku)
                    : products.existsBySkuIgnoreCaseAndIdNot(sku, existingId);
            if (taken) {
                throw ApiException.conflict("SKU \"" + sku + "\" is already used by another item");
            }
        }
        p.setName(req.name().trim());
        p.setSku(sku);
        p.setType(req.type());
        p.setSummary(blankToNull(req.summary()));
        p.setDescription(blankToNull(req.description()));
        p.setFeatured(req.featured());
        p.setActive(req.active());
        p.setCategory(req.categoryId() == null ? null
                : categories.findById(req.categoryId()).orElseThrow(() -> ApiException.badRequest("Category not found")));
        p.setBrand(req.brandId() == null ? null
                : brands.findById(req.brandId()).orElseThrow(() -> ApiException.badRequest("Brand not found")));

        p.getImages().clear();
        if (req.images() != null) {
            req.images().stream().filter(s -> s != null && !s.isBlank()).forEach(p.getImages()::add);
        }

        p.getSpecifications().clear();
        if (req.specifications() != null) {
            req.specifications().stream()
                    .filter(s -> s != null && s.label() != null && !s.label().isBlank() && s.value() != null && !s.value().isBlank())
                    .forEach(s -> p.getSpecifications().add(new ProductSpec(s.label().trim(), s.value().trim())));
        }

        p.getOptions().clear();
        if (req.optionIds() != null && !req.optionIds().isEmpty()) {
            List<OptionDefinition> found = options.findAllById(new LinkedHashSet<>(req.optionIds()));
            if (found.size() != new LinkedHashSet<>(req.optionIds()).size()) {
                throw ApiException.badRequest("One of the selected options no longer exists");
            }
            p.getOptions().addAll(found);
        }
    }

    ProductDto toDto(Product p) {
        return new ProductDto(
                p.getId(), p.getName(), p.getSku(), p.getType(), p.getSummary(), p.getDescription(),
                p.getCategory() == null ? null : p.getCategory().getId(),
                p.getCategory() == null ? null : p.getCategory().getName(),
                p.getBrand() == null ? null : p.getBrand().getId(),
                p.getBrand() == null ? null : p.getBrand().getName(),
                List.copyOf(p.getImages()),
                p.getSpecifications().stream().map(s -> new SpecDto(s.getLabel(), s.getValue())).toList(),
                p.getOptions().stream().map(o -> new OptionDto(o.getId(), o.getName(), List.copyOf(o.getOptionValues()))).toList(),
                p.isFeatured(), p.isActive());
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
