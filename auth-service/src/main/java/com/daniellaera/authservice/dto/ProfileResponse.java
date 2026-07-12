package com.daniellaera.authservice.dto;

public record ProfileResponse(
    String email,
    String firstName,
    String lastName,
    String displayName,
    String role
) {}
