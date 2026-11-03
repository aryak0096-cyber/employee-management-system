package com.example.ems.dto;

import com.example.ems.model.AttendanceStatus;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record AttendanceRequest(
        @NotNull Long employeeId, @NotNull LocalDate date, @NotNull AttendanceStatus status) {}
