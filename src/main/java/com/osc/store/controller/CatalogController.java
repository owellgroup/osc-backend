package com.osc.store.controller;

import com.osc.store.model.ProductType;
import com.osc.store.model.dto.*;
import com.osc.store.service.BannerService;
import com.osc.store.service.ProductService;
import com.osc.store.service.TaxonomyService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Public, read-only storefront API. No prices are ever exposed here. */
@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final ProductService products;
    private final TaxonomyService taxonomy;
    private final BannerService banners;

    public CatalogController(ProductService products, TaxonomyService taxonomy, BannerService banners) {
        this.products = products;
        this.taxonomy = taxonomy;
        this.banners = banners;
    }

    @GetMapping("/products")
    public PageResponse<ProductDto> products(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) ProductType type,
                                             @RequestParam(required = false) Long categoryId,
                                             @RequestParam(required = false) Long brandId,
                                             @RequestParam(required = false) Boolean featured,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "24") int size) {
        return products.search(q, type, categoryId, brandId, featured, false, page, size);
    }

    @GetMapping("/products/{id}")
    public ProductDto product(@PathVariable Long id) {
        return products.get(id, false);
    }

    @GetMapping("/categories")
    public List<NamedDto> categories() {
        return taxonomy.listCategories();
    }

    @GetMapping("/brands")
    public List<NamedDto> brands() {
        return taxonomy.listBrands();
    }

    @GetMapping("/banners")
    public List<BannerDto> banners() {
        return banners.listActive();
    }
}