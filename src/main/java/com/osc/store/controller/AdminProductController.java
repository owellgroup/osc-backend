package com.osc.store.controller;

import com.osc.store.model.ProductType;
import com.osc.store.model.dto.PageResponse;
import com.osc.store.model.dto.ProductDto;
import com.osc.store.model.dto.ProductRequest;
import com.osc.store.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {

    private final ProductService products;

    public AdminProductController(ProductService products) {
        this.products = products;
    }

    @GetMapping
    public PageResponse<ProductDto> list(@RequestParam(required = false) String q,
                                         @RequestParam(required = false) ProductType type,
                                         @RequestParam(required = false) Long categoryId,
                                         @RequestParam(required = false) Long brandId,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return products.search(q, type, categoryId, brandId, null, true, page, size);
    }

    @GetMapping("/{id}")
    public ProductDto get(@PathVariable Long id) {
        return products.get(id, true);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDto create(@Valid @RequestBody ProductRequest req) {
        return products.create(req);
    }

    @PutMapping("/{id}")
    public ProductDto update(@PathVariable Long id, @Valid @RequestBody ProductRequest req) {
        return products.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        products.delete(id);
    }
}