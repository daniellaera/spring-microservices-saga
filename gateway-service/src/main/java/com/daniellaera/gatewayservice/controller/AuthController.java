package com.daniellaera.gatewayservice.controller;

import com.daniellaera.gatewayservice.dto.LoginRequest;
import com.daniellaera.gatewayservice.dto.TokenResponse;
import com.daniellaera.gatewayservice.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request) {
        // hardcoded user for now - replace with DB later
        if ("admin".equals(request.username()) && "password".equals(request.password())) {
            String token = jwtUtil.generateToken(request.username());
            return ResponseEntity.ok(new TokenResponse(token));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
