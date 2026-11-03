package com.example.ems.dto;

import com.example.ems.model.Role;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateEmployeeRequest(
        @NotBlank
                @Size(min = 3, max = 50)
                @Pattern(
                        regexp = "^[A-Za-z0-9._-]+$",
                        message = "may only contain letters, digits, '.', '_' and '-'")
                String username,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotNull Role role,
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Email @Size(max = 150) String email,
        @Size(max = 80) String department,
        @Size(max = 80) String jobTitle,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal salary,
        @NotNull LocalDate joinDate) {}
