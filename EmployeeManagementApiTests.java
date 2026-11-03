package com.example.ems;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmployeeManagementApiTests {

    private static final String ADMIN_PASSWORD = "Admin@12345";
    private static final String EMPLOYEE_PASSWORD = "Passw0rd!23";
    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private String adminToken;

    @BeforeEach
    void loginAsAdmin() throws Exception {
        adminToken = login("admin", ADMIN_PASSWORD);
    }

    // ------------------------------------------------------------ authentication

    @Test
    void loginReturnsTokenAndRole() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "admin", "password", ADMIN_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void loginWithWrongPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "admin", "password", "wrong-password"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointsRequireAToken() throws Exception {
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/employees")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer not.a.real.token"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------ authorization

    @Test
    void employeeCannotAccessAdminEndpoints() throws Exception {
        String username = createEmployee("EMPLOYEE");
        String token = login(username, EMPLOYEE_PASSWORD);

        mockMvc.perform(get("/api/employees").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/attendance")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("employeeId", 1, "date", LocalDate.now().toString(), "status", "PRESENT"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    // ------------------------------------------------------------ employee CRUD

    @Test
    void adminCanCreateReadUpdateAndDeleteEmployee() throws Exception {
        String username = nextUsername();
        MvcResult created = mockMvc.perform(post("/api/employees")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(employeePayload(username, "EMPLOYEE"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/employees/" + id).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.department").value("Engineering"));

        Map<String, Object> update = updatePayload("EMPLOYEE", true);
        update.put("department", "Finance");
        mockMvc.perform(put("/api/employees/" + id)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.department").value("Finance"));

        mockMvc.perform(delete("/api/employees/" + id).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/employees/" + id).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchFiltersAndPagesResults() throws Exception {
        String username = createEmployee("EMPLOYEE");

        mockMvc.perform(get("/api/employees")
                        .param("search", username)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].username").value(username));

        mockMvc.perform(get("/api/employees")
                        .param("sortBy", "passwordHash")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidInputIsRejectedWithFieldErrors() throws Exception {
        Map<String, Object> payload = employeePayload("x", "EMPLOYEE");
        payload.put("email", "not-an-email");
        payload.put("password", "short");

        mockMvc.perform(post("/api/employees")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.username").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void duplicateUsernameIsRejected() throws Exception {
        String username = createEmployee("EMPLOYEE");
        Map<String, Object> payload = employeePayload(username, "EMPLOYEE");
        payload.put("email", "other-" + username + "@example.com");

        mockMvc.perform(post("/api/employees")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isConflict());
    }

    @Test
    void adminCannotDeleteOrDemoteThemselves() throws Exception {
        MvcResult me = mockMvc.perform(get("/api/me").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn();
        long adminId = objectMapper.readTree(me.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete("/api/employees/" + adminId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/employees/" + adminId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updatePayload("EMPLOYEE", true))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void disablingAnAccountInvalidatesItsExistingToken() throws Exception {
        String username = nextUsername();
        long id = createEmployeeAndReturnId(username, "EMPLOYEE");
        String token = login(username, EMPLOYEE_PASSWORD);
        mockMvc.perform(get("/api/me").header("Authorization", bearer(token))).andExpect(status().isOk());

        mockMvc.perform(put("/api/employees/" + id)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updatePayload("EMPLOYEE", false))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/me").header("Authorization", bearer(token))).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", EMPLOYEE_PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------ attendance

    @Test
    void employeeCanCheckInAndSeeOwnAttendance() throws Exception {
        String username = createEmployee("EMPLOYEE");
        String token = login(username, EMPLOYEE_PASSWORD);

        mockMvc.perform(post("/api/me/attendance/check-in").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PRESENT"));
        // Idempotent.
        mockMvc.perform(post("/api/me/attendance/check-in").header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/me/attendance").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/me/attendance/summary").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.present").value(1))
                .andExpect(jsonPath("$.attendancePercentage").value(100.0));
    }

    @Test
    void adminCanMarkAttendanceAndReadSummary() throws Exception {
        String username = nextUsername();
        long id = createEmployeeAndReturnId(username, "EMPLOYEE");
        LocalDate today = LocalDate.now();

        markAttendance(id, today.minusDays(2), "PRESENT");
        markAttendance(id, today.minusDays(1), "ABSENT");
        // Overwriting an existing day updates it rather than failing.
        markAttendance(id, today.minusDays(1), "LEAVE");

        mockMvc.perform(get("/api/attendance/summary")
                        .param("employeeId", String.valueOf(id))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.present").value(1))
                .andExpect(jsonPath("$.leave").value(1))
                .andExpect(jsonPath("$.absent").value(0))
                .andExpect(jsonPath("$.attendancePercentage").value(50.0));

        mockMvc.perform(post("/api/attendance")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "employeeId", id, "date", today.plusDays(1).toString(), "status", "PRESENT"))))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ password change

    @Test
    void employeeCanChangeOwnPassword() throws Exception {
        String username = createEmployee("EMPLOYEE");
        String token = login(username, EMPLOYEE_PASSWORD);

        mockMvc.perform(put("/api/me/password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "wrong-current", "newPassword", "BrandNew#456"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/me/password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", EMPLOYEE_PASSWORD, "newPassword", "BrandNew#456"))))
                .andExpect(status().isNoContent());

        login(username, "BrandNew#456");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", EMPLOYEE_PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------ helpers

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String createEmployee(String role) throws Exception {
        String username = nextUsername();
        createEmployeeAndReturnId(username, role);
        return username;
    }

    private long createEmployeeAndReturnId(String username, String role) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/employees")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(employeePayload(username, role))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void markAttendance(long employeeId, LocalDate date, String status) throws Exception {
        mockMvc.perform(post("/api/attendance")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("employeeId", employeeId, "date", date.toString(), "status", status))))
                .andExpect(status().isOk());
    }

    private static Map<String, Object> employeePayload(String username, String role) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("username", username);
        payload.put("password", EMPLOYEE_PASSWORD);
        payload.put("role", role);
        payload.put("fullName", "Test " + username);
        payload.put("email", username + "@example.com");
        payload.put("department", "Engineering");
        payload.put("jobTitle", "Developer");
        payload.put("salary", 50000.00);
        payload.put("joinDate", "2024-01-15");
        return payload;
    }

    /** Full-update payload. A fresh unique email is used each time so it never collides with another account. */
    private static Map<String, Object> updatePayload(String role, boolean enabled) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("role", role);
        payload.put("fullName", "Updated Name");
        payload.put("email", "updated-" + COUNTER.incrementAndGet() + "-" + System.nanoTime() + "@example.com");
        payload.put("department", "Engineering");
        payload.put("jobTitle", "Developer");
        payload.put("salary", 52000.00);
        payload.put("joinDate", "2024-01-15");
        payload.put("enabled", enabled);
        return payload;
    }

    private static String nextUsername() {
        return "user" + COUNTER.incrementAndGet() + System.nanoTime();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
