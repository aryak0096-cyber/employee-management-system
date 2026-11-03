package com.example.ems.dto;

import com.example.ems.model.Role;

public record LoginResponse(
        String token, String tokenType, long expiresInSeconds, String username, String fullName, Role role) {}
