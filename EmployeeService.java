package com.example.ems.service;

import com.example.ems.dto.ChangePasswordRequest;
import com.example.ems.dto.CreateEmployeeRequest;
import com.example.ems.dto.EmployeeResponse;
import com.example.ems.dto.PageResponse;
import com.example.ems.dto.UpdateEmployeeRequest;
import com.example.ems.exception.BadRequestException;
import com.example.ems.exception.DuplicateResourceException;
import com.example.ems.exception.ResourceNotFoundException;
import com.example.ems.model.Employee;
import com.example.ems.repository.AttendanceRepository;
import com.example.ems.repository.EmployeeRepository;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EmployeeService {

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("id", "username", "fullName", "email", "department", "joinDate");
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_PASSWORD_BYTES = 72; // BCrypt ignores/rejects anything longer

    private final EmployeeRepository employees;
    private final AttendanceRepository attendance;
    private final PasswordEncoder passwordEncoder;

    public EmployeeService(
            EmployeeRepository employees, AttendanceRepository attendance, PasswordEncoder passwordEncoder) {
        this.employees = employees;
        this.attendance = attendance;
        this.passwordEncoder = passwordEncoder;
    }

    public PageResponse<EmployeeResponse> search(
            String search, String department, int page, int size, String sortBy, String direction) {
        if (!SORTABLE_FIELDS.contains(sortBy)) {
            throw new BadRequestException("Cannot sort by '" + sortBy + "'. Allowed: " + SORTABLE_FIELDS);
        }
        Sort.Direction dir = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(dir, sortBy).and(Sort.by("id")));
        return PageResponse.from(employees
                .search(normalizeFilter(search), normalizeFilter(department), pageable)
                .map(EmployeeResponse::from));
    }

    public EmployeeResponse get(Long id) {
        return EmployeeResponse.from(find(id));
    }

    public EmployeeResponse getByUsername(String username) {
        return EmployeeResponse.from(employees
                .findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found.")));
    }

    @Transactional
    public EmployeeResponse create(CreateEmployeeRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (employees.existsByUsername(username)) {
            throw new DuplicateResourceException("Username '" + username + "' is already taken.");
        }
        if (employees.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("Email '" + email + "' is already registered.");
        }
        requireBcryptSafe(request.password());

        Employee employee = new Employee(
                username,
                passwordEncoder.encode(request.password()),
                request.role(),
                request.fullName().trim(),
                email);
        employee.setDepartment(blankToNull(request.department()));
        employee.setJobTitle(blankToNull(request.jobTitle()));
        employee.setSalary(request.salary());
        employee.setJoinDate(request.joinDate());
        return EmployeeResponse.from(employees.save(employee));
    }

    @Transactional
    public EmployeeResponse update(Long id, UpdateEmployeeRequest request, String actingUsername) {
        Employee employee = find(id);
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (employees.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new DuplicateResourceException("Email '" + email + "' is already registered.");
        }
        boolean editingSelf = employee.getUsername().equals(actingUsername);
        if (editingSelf && (request.role() != employee.getRole() || !request.enabled())) {
            // Stops an admin from locking themselves (and possibly everyone) out.
            throw new BadRequestException("You cannot change your own role or disable your own account.");
        }

        employee.setRole(request.role());
        employee.setFullName(request.fullName().trim());
        employee.setEmail(email);
        employee.setDepartment(blankToNull(request.department()));
        employee.setJobTitle(blankToNull(request.jobTitle()));
        employee.setSalary(request.salary());
        employee.setJoinDate(request.joinDate());
        employee.setEnabled(request.enabled());
        if (request.newPassword() != null && !request.newPassword().isBlank()) {
            requireBcryptSafe(request.newPassword());
            employee.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        }
        return EmployeeResponse.from(employees.save(employee));
    }

    @Transactional
    public void delete(Long id, String actingUsername) {
        Employee employee = find(id);
        if (employee.getUsername().equals(actingUsername)) {
            throw new BadRequestException("You cannot delete your own account.");
        }
        attendance.deleteByEmployeeId(id);
        employees.delete(employee);
    }

    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        Employee employee = employees
                .findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found."));
        if (!passwordEncoder.matches(request.currentPassword(), employee.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect.");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new BadRequestException("New password must be different from the current password.");
        }
        requireBcryptSafe(request.newPassword());
        employee.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        employees.save(employee);
    }

    private static void requireBcryptSafe(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new BadRequestException("Password is too long (maximum " + MAX_PASSWORD_BYTES + " bytes).");
        }
    }

    private Employee find(Long id) {
        return employees
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee " + id + " not found."));
    }

    private static String normalizeFilter(String value) {
        return value == null ? "" : value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
