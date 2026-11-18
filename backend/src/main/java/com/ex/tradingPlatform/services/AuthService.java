package com.ex.tradingPlatform.services;

import com.ex.tradingPlatform.dto.LoginRequest;
import com.ex.tradingPlatform.dto.RegisterRequest;
import com.ex.tradingPlatform.dto.TokenResponse;
import com.ex.tradingPlatform.dto.UserResponse;
import com.ex.tradingPlatform.entities.Account;
import com.ex.tradingPlatform.entities.RefreshToken;
import com.ex.tradingPlatform.entities.User;
import com.ex.tradingPlatform.exceptions.BusinessException;
import com.ex.tradingPlatform.repositories.AccountRepository;
import com.ex.tradingPlatform.repositories.RefreshTokenRepository;
import com.ex.tradingPlatform.repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class AuthService {

    static final String EMAIL_TAKEN = "EMAIL_TAKEN";
    static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    static final String INVALID_REFRESH_TOKEN = "INVALID_REFRESH_TOKEN";
    private static final String EMAIL_UNIQUE_CONSTRAINT = "uq_users_email";

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final Clock clock;

    /** Compared against when the email is unknown, so both failure paths cost one hash check. */
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository,
                       AccountRepository accountRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       TokenService tokenService,
                       Clock clock) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            throw emailTaken();
        }

        Instant now = clock.instant();
        User user = new User(email, passwordEncoder.encode(request.password()), now);
        try {
            // flush now so a concurrent duplicate hits uq_users_email here, not at commit
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            if (isEmailUniqueViolation(ex)) {
                throw emailTaken();
            }
            throw ex;
        }

        accountRepository.save(new Account(user, now));
        return UserResponse.from(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        Optional<User> user = userRepository.findByEmail(normalizeEmail(request.email()));

        String hash = user.map(User::getPasswordHash).orElse(dummyPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);

        if (user.isEmpty() || !passwordMatches || !user.get().isEnabled()) {
            throw invalidCredentials();
        }
        return issueTokens(user.get(), clock.instant());
    }

    /**
     * Rotates the refresh token. Presenting an already revoked token is treated as theft:
     * every active token of that user is revoked. noRollbackFor keeps that revocation
     * committed even though the request fails with 401.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public TokenResponse refresh(String rawRefreshToken) {
        Instant now = clock.instant();
        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenService.hashRefreshToken(rawRefreshToken))
                .orElseThrow(AuthService::invalidRefreshToken);
        User user = token.getUser();

        if (token.getRevokedAt() != null) {
            int revoked = refreshTokenRepository.revokeAllActiveByUserId(user.getId(), now);
            log.warn("Refresh token reuse detected for user {}; revoked {} active token(s)", user.getId(), revoked);
            throw invalidRefreshToken();
        }
        if (!token.isActive(now)) {
            throw invalidRefreshToken();
        }

        token.revoke(now);
        if (!user.isEnabled()) {
            throw invalidRefreshToken();
        }
        return issueTokens(user, now);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(tokenService.hashRefreshToken(rawRefreshToken))
                .ifPresent(token -> token.revoke(clock.instant()));
    }

    private TokenResponse issueTokens(User user, Instant now) {
        String rawRefreshToken = tokenService.generateRefreshToken();
        refreshTokenRepository.save(new RefreshToken(
                user,
                tokenService.hashRefreshToken(rawRefreshToken),
                tokenService.refreshTokenExpiry(now),
                now));

        return TokenResponse.bearer(
                tokenService.createAccessToken(user, now),
                rawRefreshToken,
                tokenService.accessTokenTtlSeconds());
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isEmailUniqueViolation(DataIntegrityViolationException ex) {
        return ex.getCause() instanceof ConstraintViolationException cve
                && EMAIL_UNIQUE_CONSTRAINT.equals(cve.getConstraintName());
    }

    private static BusinessException emailTaken() {
        return new BusinessException(HttpStatus.CONFLICT, EMAIL_TAKEN, "Email is already registered.");
    }

    private static BusinessException invalidCredentials() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS, "Invalid credentials");
    }

    private static BusinessException invalidRefreshToken() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, INVALID_REFRESH_TOKEN, "Invalid refresh token");
    }
}
