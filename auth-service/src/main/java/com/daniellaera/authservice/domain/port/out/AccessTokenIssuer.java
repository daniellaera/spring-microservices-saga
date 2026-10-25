package com.daniellaera.authservice.domain.port.out;

import com.daniellaera.authservice.domain.model.Role;

/** The short-lived access token (a signed JWT) the gateway checks on every request. */
public interface AccessTokenIssuer {

    String issue(String email, Role role);
}
