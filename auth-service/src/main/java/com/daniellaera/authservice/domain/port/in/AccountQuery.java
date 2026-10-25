package com.daniellaera.authservice.domain.port.in;

import com.daniellaera.authservice.domain.model.User;

public interface AccountQuery {

    boolean isEmailAvailable(String email);

    User getProfile(String email);
}
