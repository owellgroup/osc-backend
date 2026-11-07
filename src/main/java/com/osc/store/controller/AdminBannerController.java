package com.osc.store.controller;

import com.osc.store.model.dto.BannerDto;
import com.osc.store.model.dto.BannerRequest;
import com.osc.store.service.BannerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/banners")
public class AdminBannerController {

    private final BannerService banners;

    public AdminBannerController(BannerService banners) {
        this.banners = banners;
    }

    @GetMapping
    public List<BannerDto> list() {
        return banners.listAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BannerDto create(@Valid @RequestBody BannerRequest req) {
        return banners.create(req);
    }

    @PutMapping("/{id}")
    public BannerDto update(@PathVariable Long id, @Valid @RequestBody BannerRequest req) {
        return banners.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        banners.delete(id);
    }
}