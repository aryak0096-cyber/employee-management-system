package com.example.ems.dto;

import com.example.ems.model.AttendanceRecord;
import com.example.ems.model.AttendanceStatus;
import java.time.LocalDate;

public record AttendanceResponse(
        Long id, Long employeeId, String employeeName, LocalDate date, AttendanceStatus status) {

    public static AttendanceResponse from(AttendanceRecord record) {
        return new AttendanceResponse(
                record.getId(),
                record.getEmployee().getId(),
                record.getEmployee().getFullName(),
                record.getAttendanceDate(),
                record.getStatus());
    }
}
