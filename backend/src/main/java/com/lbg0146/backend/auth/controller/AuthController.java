package com.lbg0146.backend.auth.controller;

import com.lbg0146.backend.auth.AuthService;
import com.lbg0146.backend.auth.LoginResult;
import com.lbg0146.backend.auth.controller.dto.LoginRequest;
import com.lbg0146.backend.auth.controller.dto.LoginResponse;
import com.lbg0146.backend.auth.controller.dto.RegisterRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request.username(), request.password(), request.nickname());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = authService.login(request.username(), request.password());
        return new LoginResponse(result.userId(), result.username(), result.nickname(), result.token());
    }
}
