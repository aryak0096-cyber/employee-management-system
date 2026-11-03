package com.example.ems.service;

import com.example.ems.dto.AttendanceRequest;
import com.example.ems.dto.AttendanceResponse;
import com.example.ems.dto.AttendanceSummary;
import com.example.ems.exception.BadRequestException;
import com.example.ems.exception.DuplicateResourceException;
import com.example.ems.exception.ResourceNotFoundException;
import com.example.ems.model.AttendanceRecord;
import com.example.ems.model.AttendanceStatus;
import com.example.ems.model.Employee;
import com.example.ems.repository.AttendanceRepository;
import com.example.ems.repository.EmployeeRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AttendanceService {

    private static final int DEFAULT_RANGE_DAYS = 30;
    private static final int MAX_RANGE_DAYS = 366;

    private final AttendanceRepository attendance;
    private final EmployeeRepository employees;

    public AttendanceService(AttendanceRepository attendance, EmployeeRepository employees) {
        this.attendance = attendance;
        this.employees = employees;
    }

    /** Admin: create or overwrite the attendance entry for an employee on a given day. */
    @Transactional
    public AttendanceResponse mark(AttendanceRequest request) {
        if (request.date().isAfter(LocalDate.now())) {
            throw new BadRequestException("Attendance cannot be recorded for a future date.");
        }
        Employee employee = employees
                .findById(request.employeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee " + request.employeeId() + " not found."));

        AttendanceRecord record = attendance
                .findByEmployeeIdAndAttendanceDate(employee.getId(), request.date())
                .orElseGet(() -> new AttendanceRecord(employee, request.date(), request.status()));
        record.setStatus(request.status());
        return AttendanceResponse.from(attendance.save(record));
    }

    /** Employee: mark themselves present for today. Idempotent; refuses to override leave/absence. */
    @Transactional
    public AttendanceResponse checkIn(String username) {
        Employee employee = employees
                .findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found."));
        LocalDate today = LocalDate.now();

        AttendanceRecord existing = attendance
                .findByEmployeeIdAndAttendanceDate(employee.getId(), today)
                .orElse(null);
        if (existing != null) {
            if (existing.getStatus() == AttendanceStatus.PRESENT) {
                return AttendanceResponse.from(existing);
            }
            throw new DuplicateResourceException(
                    "Today is already recorded as " + existing.getStatus() + ". Ask an admin to change it.");
        }
        return AttendanceResponse.from(attendance.save(new AttendanceRecord(employee, today, AttendanceStatus.PRESENT)));
    }

    /** Admin listing. {@code employeeId} may be null to list everyone. */
    public List<AttendanceResponse> list(Long employeeId, LocalDate from, LocalDate to) {
        LocalDate[] range = resolveRange(from, to);
        List<AttendanceRecord> records = employeeId == null
                ? attendance.findInRange(range[0], range[1])
                : attendance.findForEmployeeInRange(employeeId, range[0], range[1]);
        return records.stream().map(AttendanceResponse::from).toList();
    }

    public List<AttendanceResponse> listForUsername(String username, LocalDate from, LocalDate to) {
        return list(idOf(username), from, to);
    }

    public AttendanceSummary summary(Long employeeId, LocalDate from, LocalDate to) {
        if (!employees.existsById(employeeId)) {
            throw new ResourceNotFoundException("Employee " + employeeId + " not found.");
        }
        LocalDate[] range = resolveRange(from, to);
        List<AttendanceRecord> records = attendance.findForEmployeeInRange(employeeId, range[0], range[1]);

        long present = records.stream().filter(r -> r.getStatus() == AttendanceStatus.PRESENT).count();
        long absent = records.stream().filter(r -> r.getStatus() == AttendanceStatus.ABSENT).count();
        long leave = records.stream().filter(r -> r.getStatus() == AttendanceStatus.LEAVE).count();
        long total = records.size();
        double percentage = total == 0 ? 0.0 : Math.round(present * 1000.0 / total) / 10.0;
        return new AttendanceSummary(range[0], range[1], present, absent, leave, total, percentage);
    }

    public AttendanceSummary summaryForUsername(String username, LocalDate from, LocalDate to) {
        return summary(idOf(username), from, to);
    }

    private Long idOf(String username) {
        return employees
                .findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found."))
                .getId();
    }

    private static LocalDate[] resolveRange(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_RANGE_DAYS);
        if (start.isAfter(end)) {
            throw new BadRequestException("'from' must not be after 'to'.");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) {
            throw new BadRequestException("Date range must not exceed " + MAX_RANGE_DAYS + " days.");
        }
        return new LocalDate[] {start, end};
    }
}
