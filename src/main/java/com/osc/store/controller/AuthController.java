package com.osc.store.controller;

import com.osc.store.model.dto.AdminDto;
import com.osc.store.model.dto.LoginRequest;
import com.osc.store.model.dto.LoginResponse;
import com.osc.store.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req);
    }

    @GetMapping("/me")
    public AdminDto me(Authentication authentication) {
        return auth.me(authentication.getName());
    }
}