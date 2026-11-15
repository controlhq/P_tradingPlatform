package com.ex.tradingPlatform.services;

import com.ex.tradingPlatform.dto.RegisterRequest;
import com.ex.tradingPlatform.dto.UserResponse;
import com.ex.tradingPlatform.entities.Account;
import com.ex.tradingPlatform.entities.User;
import com.ex.tradingPlatform.exceptions.BusinessException;
import com.ex.tradingPlatform.repositories.AccountRepository;
import com.ex.tradingPlatform.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    static final String EMAIL_TAKEN = "EMAIL_TAKEN";
    private static final String EMAIL_UNIQUE_CONSTRAINT = "uq_users_email";

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            throw emailTaken();
        }

        User user = new User(email, passwordEncoder.encode(request.password()));
        try {
            // flush now so a concurrent duplicate hits uq_users_email here, not at commit
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            if (isEmailUniqueViolation(ex)) {
                throw emailTaken();
            }
            throw ex;
        }

        accountRepository.save(new Account(user));
        return UserResponse.from(user);
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
}
