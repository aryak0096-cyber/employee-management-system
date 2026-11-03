package com.example.ems.controller;

import com.example.ems.dto.AttendanceRequest;
import com.example.ems.dto.AttendanceResponse;
import com.example.ems.dto.AttendanceSummary;
import com.example.ems.service.AttendanceService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Attendance management for admins. */
@RestController
@RequestMapping("/api/attendance")
@PreAuthorize("hasRole('ADMIN')")
public class AttendanceController {

    private final AttendanceService service;

    public AttendanceController(AttendanceService service) {
        this.service = service;
    }

    /** Creates or overwrites the entry for an employee on a date. */
    @PostMapping
    public AttendanceResponse mark(@Valid @RequestBody AttendanceRequest request) {
        return service.mark(request);
    }

    @GetMapping
    public List<AttendanceResponse> list(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.list(employeeId, from, to);
    }

    @GetMapping("/summary")
    public AttendanceSummary summary(
            @RequestParam Long employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.summary(employeeId, from, to);
    }
}
