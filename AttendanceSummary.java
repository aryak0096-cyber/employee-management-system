package com.example.ems.dto;

import java.time.LocalDate;

/**
 * Counts for a date range. {@code attendancePercentage} is the share of recorded days marked
 * PRESENT (0 when nothing is recorded).
 */
public record AttendanceSummary(
        LocalDate from, LocalDate to, long present, long absent, long leave, long total, double attendancePercentage) {}
