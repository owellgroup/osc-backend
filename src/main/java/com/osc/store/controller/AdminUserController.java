package com.osc.store.controller;

import com.osc.store.model.dto.AdminDto;
import com.osc.store.model.dto.AdminRequest;
import com.osc.store.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Super admin only (enforced in SecurityConfig). */
@RestController
@RequestMapping("/api/admin/admins")
public class AdminUserController {

    private final AdminUserService admins;

    public AdminUserController(AdminUserService admins) {
        this.admins = admins;
    }

    @GetMapping
    public List<AdminDto> list() {
        return admins.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminDto create(@Valid @RequestBody AdminRequest req) {
        return admins.create(req);
    }

    @PutMapping("/{id}")
    public AdminDto update(@PathVariable Long id, @Valid @RequestBody AdminRequest req) {
        return admins.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        admins.delete(id);
    }
}