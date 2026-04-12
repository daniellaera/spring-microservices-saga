package com.daniellaera.authservice.dto;

public record RegisterRequest(String firstName, String lastName, String email, String password) {
}
