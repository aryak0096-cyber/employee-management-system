package com.example.ems.controller;

import com.example.ems.dto.AttendanceResponse;
import com.example.ems.dto.AttendanceSummary;
import com.example.ems.dto.ChangePasswordRequest;
import com.example.ems.dto.EmployeeResponse;
import com.example.ems.service.AttendanceService;
import com.example.ems.service.EmployeeService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for the signed-in user (any role). They only ever touch the caller's own data. */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final EmployeeService employeeService;
    private final AttendanceService attendanceService;

    public MeController(EmployeeService employeeService, AttendanceService attendanceService) {
        this.employeeService = employeeService;
        this.attendanceService = attendanceService;
    }

    @GetMapping
    public EmployeeResponse profile(Authentication authentication) {
        return employeeService.getByUsername(authentication.getName());
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(Authentication authentication, @Valid @RequestBody ChangePasswordRequest request) {
        employeeService.changePassword(authentication.getName(), request);
    }

    @GetMapping("/attendance")
    public List<AttendanceResponse> attendance(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return attendanceService.listForUsername(authentication.getName(), from, to);
    }

    @GetMapping("/attendance/summary")
    public AttendanceSummary attendanceSummary(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return attendanceService.summaryForUsername(authentication.getName(), from, to);
    }

    @PostMapping("/attendance/check-in")
    public AttendanceResponse checkIn(Authentication authentication) {
        return attendanceService.checkIn(authentication.getName());
    }
}
