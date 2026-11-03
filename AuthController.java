package com.example.ems.controller;

import com.example.ems.dto.LoginRequest;
import com.example.ems.dto.LoginResponse;
import com.example.ems.exception.ResourceNotFoundException;
import com.example.ems.model.Employee;
import com.example.ems.repository.EmployeeRepository;
import com.example.ems.security.TokenService;
import jakarta.validation.Valid;
import java.util.Locale;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final EmployeeRepository employees;

    public AuthController(
            AuthenticationManager authenticationManager, TokenService tokenService, EmployeeRepository employees) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.employees = employees;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);

        // Throws AuthenticationException (-> 401) for unknown users, bad passwords and disabled accounts.
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(username, request.password()));

        Employee employee = employees
                .findByUsername(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found."));
        TokenService.IssuedToken token = tokenService.issue(employee.getUsername(), employee.getRole());
        return new LoginResponse(
                token.value(),
                "Bearer",
                token.expiresInSeconds(),
                employee.getUsername(),
                employee.getFullName(),
                employee.getRole());
    }
}
