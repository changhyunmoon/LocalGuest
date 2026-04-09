package com.team6.domain.auth.controller;

import com.team6.domain.auth.dto.LoginRequest;
import com.team6.domain.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody LoginRequest request) {
        // [LOG] INFO : [Auth-Controller] 로그인 요청 수신 (Email : {})
        String token = authService.login(request.getEmail(), request.getPassword());

        return ResponseEntity.ok(token);
    }
}
