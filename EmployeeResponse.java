package com.example.ems.dto;

import com.example.ems.model.Employee;
import com.example.ems.model.Role;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Public view of an employee. The password hash is never exposed. */
public record EmployeeResponse(
        Long id,
        String username,
        Role role,
        String fullName,
        String email,
        String department,
        String jobTitle,
        BigDecimal salary,
        LocalDate joinDate,
        boolean enabled) {

    public static EmployeeResponse from(Employee e) {
        return new EmployeeResponse(
                e.getId(),
                e.getUsername(),
                e.getRole(),
                e.getFullName(),
                e.getEmail(),
                e.getDepartment(),
                e.getJobTitle(),
                e.getSalary(),
                e.getJoinDate(),
                e.isEnabled());
    }
}
