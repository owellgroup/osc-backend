package com.osc.store.controller;

import com.osc.store.model.dto.NamedDto;
import com.osc.store.model.dto.NamedRequest;
import com.osc.store.model.dto.OptionDto;
import com.osc.store.model.dto.OptionRequest;
import com.osc.store.service.TaxonomyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Admin management of categories, brands and available options. */
@RestController
@RequestMapping("/api/admin")
public class AdminTaxonomyController {

    private final TaxonomyService taxonomy;

    public AdminTaxonomyController(TaxonomyService taxonomy) {
        this.taxonomy = taxonomy;
    }

    // ---- categories
    @GetMapping("/categories")
    public List<NamedDto> categories() {
        return taxonomy.listCategories();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public NamedDto createCategory(@Valid @RequestBody NamedRequest req) {
        return taxonomy.createCategory(req);
    }

    @PutMapping("/categories/{id}")
    public NamedDto updateCategory(@PathVariable Long id, @Valid @RequestBody NamedRequest req) {
        return taxonomy.updateCategory(id, req);
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable Long id) {
        taxonomy.deleteCategory(id);
    }

    // ---- brands
    @GetMapping("/brands")
    public List<NamedDto> brands() {
        return taxonomy.listBrands();
    }

    @PostMapping("/brands")
    @ResponseStatus(HttpStatus.CREATED)
    public NamedDto createBrand(@Valid @RequestBody NamedRequest req) {
        return taxonomy.createBrand(req);
    }

    @PutMapping("/brands/{id}")
    public NamedDto updateBrand(@PathVariable Long id, @Valid @RequestBody NamedRequest req) {
        return taxonomy.updateBrand(id, req);
    }

    @DeleteMapping("/brands/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBrand(@PathVariable Long id) {
        taxonomy.deleteBrand(id);
    }

    // ---- options
    @GetMapping("/options")
    public List<OptionDto> options() {
        return taxonomy.listOptions();
    }

    @PostMapping("/options")
    @ResponseStatus(HttpStatus.CREATED)
    public OptionDto createOption(@Valid @RequestBody OptionRequest req) {
        return taxonomy.createOption(req);
    }

    @PutMapping("/options/{id}")
    public OptionDto updateOption(@PathVariable Long id, @Valid @RequestBody OptionRequest req) {
        return taxonomy.updateOption(id, req);
    }

    @DeleteMapping("/options/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOption(@PathVariable Long id) {
        taxonomy.deleteOption(id);
    }
}