package com.daniellaera.authservice.application;

import com.daniellaera.authservice.domain.model.AuthTokens;
import com.daniellaera.authservice.domain.model.AuthenticationFailedException;
import com.daniellaera.authservice.domain.model.OtpLimitExceededException;
import com.daniellaera.authservice.domain.model.RefreshToken;
import com.daniellaera.authservice.domain.model.Role;
import com.daniellaera.authservice.domain.model.User;
import com.daniellaera.authservice.domain.model.UserNotFoundException;
import com.daniellaera.authservice.domain.port.in.AccountQuery;
import com.daniellaera.authservice.domain.port.in.LoginUseCase;
import com.daniellaera.authservice.domain.port.in.RefreshTokensUseCase;
import com.daniellaera.authservice.domain.port.in.RegisterUseCase;
import com.daniellaera.authservice.domain.port.in.UpdateProfileUseCase;
import com.daniellaera.authservice.domain.port.out.AccessTokenIssuer;
import com.daniellaera.authservice.domain.port.out.AuditTrail;
import com.daniellaera.authservice.domain.port.out.CredentialsVerifier;
import com.daniellaera.authservice.domain.port.out.PasswordHasher;
import com.daniellaera.authservice.domain.port.out.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/** Registration, the two login steps, token rotation and logout, and the profile. Not transactional itself. */
public class AuthenticationService implements RegisterUseCase, LoginUseCase, RefreshTokensUseCase, AccountQuery,
        UpdateProfileUseCase {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final CredentialsVerifier credentialsVerifier;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenService refreshTokenService;
    private final OtpService otpService;
    private final AuditTrail auditTrail;

    public AuthenticationService(UserRepository userRepository, PasswordHasher passwordHasher,
                                 CredentialsVerifier credentialsVerifier, AccessTokenIssuer accessTokenIssuer,
                                 RefreshTokenService refreshTokenService, OtpService otpService,
                                 AuditTrail auditTrail) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.credentialsVerifier = credentialsVerifier;
        this.accessTokenIssuer = accessTokenIssuer;
        this.refreshTokenService = refreshTokenService;
        this.otpService = otpService;
        this.auditTrail = auditTrail;
    }

    @Override
    public AuthTokens register(String email, String rawPassword) {
        User user = User.register(email, passwordHasher.hash(rawPassword), LocalDateTime.now());
        userRepository.save(user);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.email());
        auditTrail.userRegistered(user.email());
        return new AuthTokens(accessTokenIssuer.issue(user.email(), user.role()), refreshToken.token());
    }

    @Override
    public String login(String email, String rawPassword) {
        credentialsVerifier.verify(email, rawPassword);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        try {
            otpService.initiateOtp(user.email());
        } catch (OtpLimitExceededException e) {
            auditTrail.loginFailed(user.email(), e.auditReason());
            throw e;
        }
        auditTrail.loginInitiated(user.email());

        return user.email();
    }

    @Override
    public AuthTokens verifyOtp(String email, String otp) {
        try {
            otpService.verifyOtp(email, otp);
        } catch (OtpLimitExceededException e) {
            auditTrail.loginFailed(email, e.auditReason());
            throw e;
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthenticationFailedException("User not found"));

        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.email());
        auditTrail.loggedIn(user.email());

        return new AuthTokens(accessTokenIssuer.issue(user.email(), user.role()), refreshToken.token());
    }

    // three transactions, as when the controller did it: validate, revoke, create
    @Override
    public AuthTokens refresh(String refreshToken) {
        RefreshToken current = refreshTokenService.validateRefreshToken(refreshToken);

        String userEmail = current.userEmail();
        Role role = current.userRole();

        refreshTokenService.revokeRefreshToken(refreshToken);
        RefreshToken next = refreshTokenService.createRefreshToken(userEmail);

        String accessToken = accessTokenIssuer.issue(userEmail, role);

        return new AuthTokens(accessToken, next.token());
    }

    @Override
    public void logout(String refreshToken) {
        refreshTokenService.revokeRefreshToken(refreshToken);
        auditTrail.loggedOut(refreshToken);
    }

    @Override
    public boolean isEmailAvailable(String email) {
        return !userRepository.existsByEmail(email);
    }

    @Override
    public User getProfile(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + email));
    }

    @Override
    public User updateProfile(String email, String firstName, String lastName) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + email));

        User saved = userRepository.save(user.withNames(firstName, lastName));
        log.info("=== Profile updated for {}", email);
        auditTrail.profileUpdated(email, firstName, lastName);
        return saved;
    }
}
