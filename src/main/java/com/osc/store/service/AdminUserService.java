package com.osc.store.service;

import com.osc.store.config.ApiException;
import com.osc.store.model.AdminRole;
import com.osc.store.model.AdminUser;
import com.osc.store.model.dto.AdminDto;
import com.osc.store.model.dto.AdminRequest;
import com.osc.store.repository.AdminUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** The super admin can add, update and delete other admins. The super admin account itself is protected. */
@Service
public class AdminUserService {

    private static final int MIN_PASSWORD = 8;

    private final AdminUserRepository admins;
    private final PasswordEncoder encoder;

    public AdminUserService(AdminUserRepository admins, PasswordEncoder encoder) {
        this.admins = admins;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public List<AdminDto> list() {
        return admins.findAll().stream()
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .map(AdminUserService::toDto).toList();
    }

    @Transactional
    public AdminDto create(AdminRequest req) {
        String email = req.email().trim().toLowerCase();
        if (admins.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An admin with that email already exists");
        }
        if (req.password() == null || req.password().length() < MIN_PASSWORD) {
            throw ApiException.badRequest("Password must be at least " + MIN_PASSWORD + " characters");
        }
        AdminUser u = new AdminUser();
        u.setName(req.name().trim());
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(req.password()));
        u.setRole(AdminRole.ADMIN);
        return toDto(admins.save(u));
    }

    @Transactional
    public AdminDto update(Long id, AdminRequest req) {
        AdminUser u = admins.findById(id).orElseThrow(() -> ApiException.notFound("Admin"));
        if (u.isSuperAdmin()) {
            throw ApiException.forbidden("The super admin account cannot be edited");
        }
        String email = req.email().trim().toLowerCase();
        if (!u.getEmail().equalsIgnoreCase(email) && admins.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An admin with that email already exists");
        }
        u.setName(req.name().trim());
        u.setEmail(email);
        if (req.password() != null && !req.password().isBlank()) {
            if (req.password().length() < MIN_PASSWORD) {
                throw ApiException.badRequest("Password must be at least " + MIN_PASSWORD + " characters");
            }
            u.setPasswordHash(encoder.encode(req.password()));
        }
        return toDto(admins.save(u));
    }

    @Transactional
    public void delete(Long id) {
        AdminUser u = admins.findById(id).orElseThrow(() -> ApiException.notFound("Admin"));
        if (u.isSuperAdmin()) {
            throw ApiException.forbidden("The super admin account cannot be deleted");
        }
        admins.delete(u);
    }

    public static AdminDto toDto(AdminUser u) {
        return new AdminDto(u.getId(), u.getName(), u.getEmail(), u.getRole(), u.isSuperAdmin(), u.getCreatedAt());
    }
}
