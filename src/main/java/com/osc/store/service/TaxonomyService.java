package com.osc.store.service;

import com.osc.store.config.ApiException;
import com.osc.store.model.Brand;
import com.osc.store.model.Category;
import com.osc.store.model.OptionDefinition;
import com.osc.store.model.Product;
import com.osc.store.model.dto.NamedDto;
import com.osc.store.model.dto.NamedRequest;
import com.osc.store.model.dto.OptionDto;
import com.osc.store.model.dto.OptionRequest;
import com.osc.store.repository.BrandRepository;
import com.osc.store.repository.CategoryRepository;
import com.osc.store.repository.OptionDefinitionRepository;
import com.osc.store.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;

/** Categories, brands and available options. All are created and controlled by admins. */
@Service
public class TaxonomyService {

    private final CategoryRepository categories;
    private final BrandRepository brands;
    private final OptionDefinitionRepository options;
    private final ProductRepository products;

    public TaxonomyService(CategoryRepository categories, BrandRepository brands,
                           OptionDefinitionRepository options, ProductRepository products) {
        this.categories = categories;
        this.brands = brands;
        this.options = options;
        this.products = products;
    }

    // ---------------- categories

    @Transactional(readOnly = true)
    public List<NamedDto> listCategories() {
        return categories.findAllByOrderByNameAsc().stream().map(this::toDto).toList();
    }

    @Transactional
    public NamedDto createCategory(NamedRequest req) {
        String name = req.name().trim();
        if (categories.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("A category called \"" + name + "\" already exists");
        }
        Category c = new Category();
        c.setName(name);
        c.setDescription(blankToNull(req.description()));
        return toDto(categories.save(c));
    }

    @Transactional
    public NamedDto updateCategory(Long id, NamedRequest req) {
        Category c = categories.findById(id).orElseThrow(() -> ApiException.notFound("Category"));
        String name = req.name().trim();
        if (!c.getName().equalsIgnoreCase(name) && categories.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("A category called \"" + name + "\" already exists");
        }
        c.setName(name);
        c.setDescription(blankToNull(req.description()));
        return toDto(categories.save(c));
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category c = categories.findById(id).orElseThrow(() -> ApiException.notFound("Category"));
        if (products.existsByCategoryId(id)) {
            throw ApiException.conflict("This category still has products. Move or delete them first.");
        }
        categories.delete(c);
    }

    // ---------------- brands

    @Transactional(readOnly = true)
    public List<NamedDto> listBrands() {
        return brands.findAllByOrderByNameAsc().stream().map(this::toDto).toList();
    }

    @Transactional
    public NamedDto createBrand(NamedRequest req) {
        String name = req.name().trim();
        if (brands.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("A brand called \"" + name + "\" already exists");
        }
        Brand b = new Brand();
        b.setName(name);
        b.setDescription(blankToNull(req.description()));
        return toDto(brands.save(b));
    }

    @Transactional
    public NamedDto updateBrand(Long id, NamedRequest req) {
        Brand b = brands.findById(id).orElseThrow(() -> ApiException.notFound("Brand"));
        String name = req.name().trim();
        if (!b.getName().equalsIgnoreCase(name) && brands.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("A brand called \"" + name + "\" already exists");
        }
        b.setName(name);
        b.setDescription(blankToNull(req.description()));
        return toDto(brands.save(b));
    }

    @Transactional
    public void deleteBrand(Long id) {
        Brand b = brands.findById(id).orElseThrow(() -> ApiException.notFound("Brand"));
        if (products.existsByBrandId(id)) {
            throw ApiException.conflict("This brand still has products. Move or delete them first.");
        }
        brands.delete(b);
    }

    // ---------------- options

    @Transactional(readOnly = true)
    public List<OptionDto> listOptions() {
        return options.findAllByOrderByNameAsc().stream().map(this::toDto).toList();
    }

    @Transactional
    public OptionDto createOption(OptionRequest req) {
        String name = req.name().trim();
        if (options.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("An option called \"" + name + "\" already exists");
        }
        OptionDefinition o = new OptionDefinition();
        o.setName(name);
        o.setOptionValues(cleanValues(req.values()));
        return toDto(options.save(o));
    }

    @Transactional
    public OptionDto updateOption(Long id, OptionRequest req) {
        OptionDefinition o = options.findById(id).orElseThrow(() -> ApiException.notFound("Option"));
        String name = req.name().trim();
        if (!o.getName().equalsIgnoreCase(name) && options.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("An option called \"" + name + "\" already exists");
        }
        o.setName(name);
        o.getOptionValues().clear();
        o.getOptionValues().addAll(cleanValues(req.values()));
        return toDto(options.save(o));
    }

    @Transactional
    public void deleteOption(Long id) {
        OptionDefinition o = options.findById(id).orElseThrow(() -> ApiException.notFound("Option"));
        // detach from every product that uses it, then remove
        for (Product p : products.findByOptionsId(id)) {
            p.getOptions().removeIf(x -> x.getId().equals(id));
        }
        options.delete(o);
    }

    // ------------------------------------------------------------------

    private List<String> cleanValues(List<String> values) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                unique.add(v.trim());
            }
        }
        if (unique.isEmpty()) {
            throw ApiException.badRequest("Add at least one value");
        }
        return new java.util.ArrayList<>(unique);
    }

    private NamedDto toDto(Category c) {
        return new NamedDto(c.getId(), c.getName(), c.getDescription());
    }

    private NamedDto toDto(Brand b) {
        return new NamedDto(b.getId(), b.getName(), b.getDescription());
    }

    private OptionDto toDto(OptionDefinition o) {
        return new OptionDto(o.getId(), o.getName(), List.copyOf(o.getOptionValues()));
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
