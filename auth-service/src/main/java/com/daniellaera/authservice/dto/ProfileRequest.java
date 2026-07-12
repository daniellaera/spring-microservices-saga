package com.daniellaera.authservice.dto;

import jakarta.validation.constraints.Size;

public record ProfileRequest(
    @Size(max = 50, message = "First name too long")
    String firstName,

    @Size(max = 50, message = "Last name too long")
    String lastName
) {}
