package com.jms.dto;

import com.jms.domain.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6, max = 100) String password,
            @NotBlank @Size(min = 2, max = 120) String fullName,
            @NotNull Role role,
            String companyName,
            String headline,
            String skills
    ) {}

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {}

    public record UserSummary(
            Long id,
            String email,
            String fullName,
            Role role,
            String companyName,
            String headline,
            String skills,
            Instant createdAt
    ) {}

    public record AuthResponse(
            String token,
            String tokenType,
            long expiresInMs,
            UserSummary user
    ) {}
}
