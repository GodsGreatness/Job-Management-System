package com.jms.service;

import com.jms.domain.enums.BackgroundJobType;
import com.jms.domain.model.User;
import com.jms.dto.AuthDtos.*;
import com.jms.exception.DomainException;
import com.jms.repository.UserRepository;
import com.jms.security.JwtService;
import com.jms.worker.BackgroundJobQueueService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final BackgroundJobQueueService queueService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       BackgroundJobQueueService queueService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.queueService = queueService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String normalizedEmail = req.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw DomainException.conflict("An account with email " + normalizedEmail + " already exists");
        }

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setFullName(req.fullName().trim());
        user.setRole(req.role());
        user.setCompanyName(req.companyName());
        user.setHeadline(req.headline());
        user.setSkills(req.skills());
        user = userRepository.save(user);

        queueService.enqueue(
                BackgroundJobType.SEND_EMAIL,
                Map.of(
                        "to", user.getEmail(),
                        "subject", "Welcome to Job Management System",
                        "template", "WELCOME_" + user.getRole().name()
                ),
                5,
                "welcome-email-user-" + user.getId()
        );

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, "Bearer", jwtService.getExpirationMs(), toSummary(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmailIgnoreCase(req.email().trim())
                .orElseThrow(() -> DomainException.unauthorized("Invalid email or password"));
        if (!user.isActive()) {
            throw DomainException.forbidden("This account has been deactivated");
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw DomainException.unauthorized("Invalid email or password");
        }

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, "Bearer", jwtService.getExpirationMs(), toSummary(user));
    }

    @Transactional(readOnly = true)
    public UserSummary getUserSummary(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> DomainException.notFound("User not found"));
        return toSummary(user);
    }

    public static UserSummary toSummary(User user) {
        return new UserSummary(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getCompanyName(),
                user.getHeadline(),
                user.getSkills(),
                user.getCreatedAt()
        );
    }
}
