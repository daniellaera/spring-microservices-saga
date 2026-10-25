package com.daniellaera.authservice.domain.port.in;

import com.daniellaera.authservice.domain.model.User;

public interface UpdateProfileUseCase {

    // a name that is null is left unchanged
    User updateProfile(String email, String firstName, String lastName);
}
