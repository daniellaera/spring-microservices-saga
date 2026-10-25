package com.daniellaera.authservice.domain.port.in;

import com.daniellaera.authservice.domain.model.AuthTokens;

/** Two steps: the password sends a one-time code by email, the code gives the tokens. */
public interface LoginUseCase {

    // the email of the account the code was sent for
    String login(String email, String rawPassword);

    AuthTokens verifyOtp(String email, String otp);
}
