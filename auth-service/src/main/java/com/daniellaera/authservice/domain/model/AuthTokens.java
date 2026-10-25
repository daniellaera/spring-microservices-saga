package com.daniellaera.authservice.domain.model;

/** What a successful registration, OTP verification or refresh gives: an access token and a refresh token. */
public record AuthTokens(String accessToken, String refreshToken) {
}
