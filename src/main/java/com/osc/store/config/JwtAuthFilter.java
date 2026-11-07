package com.osc.store.config;

import com.osc.store.repository.AdminUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AdminUserRepository admins;

    public JwtAuthFilter(JwtService jwtService, AdminUserRepository admins) {
        this.jwtService = jwtService;
        this.admins = admins;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            // Look the admin up on every request so deleted admins lose access immediately
            // and role changes apply without waiting for the token to expire.
            jwtService.parse(header.substring(7))
                    .flatMap(claims -> admins.findByEmailIgnoreCase(claims.getSubject()))
                    .ifPresent(admin -> {
                        var auth = new UsernamePasswordAuthenticationToken(
                                admin.getEmail(), null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + admin.getRole().name())));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    });
        }
        chain.doFilter(request, response);
    }
}
