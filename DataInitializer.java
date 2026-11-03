package com.example.ems.config;

import com.example.ems.model.AttendanceRecord;
import com.example.ems.model.AttendanceStatus;
import com.example.ems.model.Employee;
import com.example.ems.model.Role;
import com.example.ems.repository.AttendanceRepository;
import com.example.ems.repository.EmployeeRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * On first start (empty database) creates the bootstrap admin account and, optionally, a few demo
 * employees with sample attendance so the UI is not empty.
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final String DEMO_PASSWORD = "Employee@123";

    private final EmployeeRepository employees;
    private final AttendanceRepository attendance;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;
    private final boolean seedDemoData;

    public DataInitializer(
            EmployeeRepository employees,
            AttendanceRepository attendance,
            PasswordEncoder passwordEncoder,
            @Value("${ems.bootstrap.admin-username}") String adminUsername,
            @Value("${ems.bootstrap.admin-password}") String adminPassword,
            @Value("${ems.seed-demo-data:false}") boolean seedDemoData) {
        this.employees = employees;
        this.attendance = attendance;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.seedDemoData = seedDemoData;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (employees.count() > 0) {
            return;
        }

        Employee admin = new Employee(
                adminUsername.toLowerCase(Locale.ROOT),
                passwordEncoder.encode(adminPassword),
                Role.ADMIN,
                "System Administrator",
                "admin@example.com");
        admin.setDepartment("Administration");
        admin.setJobTitle("Administrator");
        admin.setJoinDate(LocalDate.now());
        employees.save(admin);
        log.info("Created bootstrap admin account '{}'. Change its password after first login.", admin.getUsername());

        if (seedDemoData) {
            seedDemoEmployees();
        }
    }

    private void seedDemoEmployees() {
        List<Employee> demo = new ArrayList<>();
        demo.add(newEmployee("priya.sharma", "Priya Sharma", "Engineering", "Backend Developer", "85000", 420));
        demo.add(newEmployee("rahul.verma", "Rahul Verma", "Engineering", "QA Engineer", "62000", 300));
        demo.add(newEmployee("ananya.iyer", "Ananya Iyer", "Human Resources", "HR Executive", "55000", 150));
        demo.add(newEmployee("karan.mehta", "Karan Mehta", "Sales", "Account Manager", "58000", 90));
        employees.saveAll(demo);

        List<AttendanceRecord> records = new ArrayList<>();
        for (int e = 0; e < demo.size(); e++) {
            for (int daysAgo = 1; daysAgo <= 14; daysAgo++) {
                LocalDate date = LocalDate.now().minusDays(daysAgo);
                if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                    continue;
                }
                int pick = (e * 3 + daysAgo) % 9;
                AttendanceStatus status = pick == 0
                        ? AttendanceStatus.ABSENT
                        : pick == 1 ? AttendanceStatus.LEAVE : AttendanceStatus.PRESENT;
                records.add(new AttendanceRecord(demo.get(e), date, status));
            }
        }
        attendance.saveAll(records);
        log.info("Seeded {} demo employees (password: {}).", demo.size(), DEMO_PASSWORD);
    }

    private Employee newEmployee(
            String username, String fullName, String department, String title, String salary, int daysEmployed) {
        Employee employee = new Employee(
                username, passwordEncoder.encode(DEMO_PASSWORD), Role.EMPLOYEE, fullName, username + "@example.com");
        employee.setDepartment(department);
        employee.setJobTitle(title);
        employee.setSalary(new BigDecimal(salary));
        employee.setJoinDate(LocalDate.now().minusDays(daysEmployed));
        return employee;
    }
}
