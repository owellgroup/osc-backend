package com.osc.store.service;

import com.osc.store.config.JwtService;
import com.osc.store.model.AdminUser;
import com.osc.store.model.dto.AdminDto;
import com.osc.store.model.dto.LoginRequest;
import com.osc.store.model.dto.LoginResponse;
import com.osc.store.repository.AdminUserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AdminUserRepository admins;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(AdminUserRepository admins, PasswordEncoder encoder, JwtService jwt) {
        this.admins = admins;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    public LoginResponse login(LoginRequest req) {
        AdminUser user = admins.findByEmailIgnoreCase(req.email().trim())
                .filter(u -> encoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Bad credentials"));
        return new LoginResponse(jwt.generate(user), AdminUserService.toDto(user));
    }

    public AdminDto me(String email) {
        return admins.findByEmailIgnoreCase(email).map(AdminUserService::toDto)
                .orElseThrow(() -> new BadCredentialsException("Unknown admin"));
    }
}
