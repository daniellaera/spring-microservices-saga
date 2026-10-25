package com.daniellaera.authservice.config;

import com.daniellaera.authservice.application.AuthenticationService;
import com.daniellaera.authservice.application.OtpService;
import com.daniellaera.authservice.application.RefreshTokenService;
import com.daniellaera.authservice.domain.port.out.AccessTokenIssuer;
import com.daniellaera.authservice.domain.port.out.AuditTrail;
import com.daniellaera.authservice.domain.port.out.CredentialsVerifier;
import com.daniellaera.authservice.domain.port.out.OtpAttempts;
import com.daniellaera.authservice.domain.port.out.OtpEventPublisher;
import com.daniellaera.authservice.domain.port.out.OtpGenerator;
import com.daniellaera.authservice.domain.port.out.OtpRepository;
import com.daniellaera.authservice.domain.port.out.PasswordHasher;
import com.daniellaera.authservice.domain.port.out.RefreshTokenRepository;
import com.daniellaera.authservice.domain.port.out.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/** The use cases are plain classes: one bean each. RefreshTokenService's @Transactional applies through its bean. */
@Configuration
public class AuthUseCaseConfig {

    @Bean
    OtpService otpCodes(OtpGenerator otpGenerator, OtpRepository otpRepository, OtpEventPublisher otpEventPublisher,
                        OtpAttempts otpAttempts) {
        return new OtpService(otpGenerator, otpRepository, otpEventPublisher, otpAttempts);
    }

    @Bean
    RefreshTokenService refreshTokens(RefreshTokenRepository refreshTokenRepository, UserRepository userRepository,
                                      AuditTrail auditTrail,
                                      @Value("${app.jwt.refresh-expiration:604800000}") long refreshExpirationMillis) {
        return new RefreshTokenService(refreshTokenRepository, userRepository, auditTrail,
                Duration.ofMillis(refreshExpirationMillis));
    }

    @Bean
    AuthenticationService authenticationService(UserRepository userRepository, PasswordHasher passwordHasher,
                                                CredentialsVerifier credentialsVerifier,
                                                AccessTokenIssuer accessTokenIssuer,
                                                RefreshTokenService refreshTokens, OtpService otpCodes,
                                                AuditTrail auditTrail) {
        return new AuthenticationService(userRepository, passwordHasher, credentialsVerifier, accessTokenIssuer,
                refreshTokens, otpCodes, auditTrail);
    }
}
