# Secure Employee Management System

A Spring Boot application for managing employees and attendance, with **JWT authentication** and **role-based access control** (Admin and Employee). It exposes a documented REST API and includes a small single-page web UI.

## Features

- **Authentication**: login with username and password, stateless JWT (HS256) issued on success. Passwords are hashed with BCrypt.
- **Role-based authorization** (Spring Security):
  - **ADMIN**: add, update, delete, search and filter employees; mark attendance; view anyone's attendance and summaries.
  - **EMPLOYEE**: view own profile, check in for today, view own attendance and summary, change own password.
- **Employee management**: validated create/update, pagination, sorting (whitelisted fields), search by name/email/username, department filter.
- **Attendance tracking**: Present / Absent / Leave per employee per day, date-range queries, attendance percentage.
- **Security details**:
  - Role and account status are re-checked from the database on every request, so disabling an account or changing a role takes effect immediately, even for unexpired tokens.
  - Admins cannot delete, demote or disable their own account.
  - Generic login error messages, no stack traces in error responses, password hash never serialised.
  - Content-Security-Policy header; the UI inserts data with `textContent` only (no `innerHTML`).
  - Bean Validation on every request body; consistent JSON error format.
- **Databases**: H2 (file-based, zero setup) by default; MySQL via the `mysql` profile.

## Tech stack

Java 17+, Spring Boot 3.5, Spring Web, Spring Security (OAuth2 Resource Server for JWT), Spring Data JPA / Hibernate, Bean Validation, H2, MySQL, Maven, JUnit 5 + MockMvc, HTML/CSS/JavaScript.

## Getting started

Requirements: JDK 17+ and Maven 3.9+ (or open the project in IntelliJ IDEA / VS Code / Eclipse).

```bash
git clone https://github.com/<your-username>/employee-management-system.git
cd employee-management-system

mvn spring-boot:run
```

Open http://localhost:8080 and sign in.

| Account | Username | Password |
|---------|----------|----------|
| Admin | `admin` | `Admin@12345` |
| Demo employees | `priya.sharma`, `rahul.verma`, `ananya.iyer`, `karan.mehta` | `Employee@123` |

These are development defaults created on first start when the database is empty. **Change them (or set the environment variables below) for anything beyond local use.**

### Configuration

| Environment variable | Purpose | Default |
|----------------------|---------|---------|
| `EMS_JWT_SECRET` | HMAC secret for signing tokens (min. 32 characters) | development-only value |
| `EMS_ADMIN_PASSWORD` | Password of the bootstrap admin account | `Admin@12345` |

```bash
export EMS_JWT_SECRET="$(openssl rand -base64 48)"
export EMS_ADMIN_PASSWORD="a-strong-password"
mvn spring-boot:run
```

### Using MySQL

```bash
docker compose up -d                                    # starts MySQL 8.4 on port 3306
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

Connection settings come from `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` (defaults match `docker-compose.yml`). The `mysql` profile does not seed demo users; only the bootstrap admin is created.

### Running the tests

```bash
mvn test
```

The integration tests cover login, token rejection, role enforcement, employee CRUD, validation errors, duplicate detection, self-protection rules, attendance, and immediate invalidation of disabled accounts.

## REST API

All endpoints except login require `Authorization: Bearer <token>`.

| Method | Endpoint | Role | Description |
|--------|----------|------|-------------|
| POST | `/api/auth/login` | public | `{"username","password"}` → token |
| GET | `/api/me` | any | Own profile |
| PUT | `/api/me/password` | any | Change own password |
| GET | `/api/me/attendance?from=&to=` | any | Own attendance (default: last 30 days) |
| GET | `/api/me/attendance/summary` | any | Own attendance counts and percentage |
| POST | `/api/me/attendance/check-in` | any | Mark yourself present today |
| GET | `/api/employees?search=&department=&page=&size=&sortBy=&direction=` | ADMIN | Paged list |
| POST | `/api/employees` | ADMIN | Create employee |
| GET | `/api/employees/{id}` | ADMIN | Fetch one |
| PUT | `/api/employees/{id}` | ADMIN | Update (optional `newPassword` resets it) |
| DELETE | `/api/employees/{id}` | ADMIN | Delete (also removes their attendance) |
| POST | `/api/attendance` | ADMIN | `{"employeeId","date","status"}`: create or overwrite |
| GET | `/api/attendance?employeeId=&from=&to=` | ADMIN | List records |
| GET | `/api/attendance/summary?employeeId=&from=&to=` | ADMIN | Counts and percentage |

Example:

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Admin@12345"}' | jq -r .token)

curl -s -H "Authorization: Bearer $TOKEN" "localhost:8080/api/employees?search=priya"

curl -s -X POST localhost:8080/api/employees \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"username":"new.hire","password":"Passw0rd!23","role":"EMPLOYEE","fullName":"New Hire",
       "email":"new.hire@example.com","department":"Engineering","jobTitle":"Developer",
       "salary":50000,"joinDate":"2026-11-01"}'
```

Errors use one JSON shape:

```json
{ "status": 400, "error": "Bad Request", "message": "Validation failed.",
  "fieldErrors": { "email": "must be a well-formed email address" }, "timestamp": "..." }
```

## Project structure

```
src/main/java/com/example/ems/
├── EmsApplication.java
├── config/        DataInitializer (bootstrap admin + demo data)
├── controller/    AuthController, MeController, EmployeeController, AttendanceController
├── dto/           request/response records with validation
├── exception/     custom exceptions + GlobalExceptionHandler
├── model/         Employee, AttendanceRecord, Role, AttendanceStatus
├── repository/    Spring Data JPA repositories
├── security/      SecurityConfig, JwtConfig, TokenService, user details, JWT converter
└── service/       EmployeeService, AttendanceService
src/main/resources/
├── application.properties, application-mysql.properties
└── static/        index.html, app.js, style.css
src/test/          integration tests (MockMvc, in-memory H2)
```

## Known limitations and ideas

- Tokens are valid until they expire (60 minutes by default); there is no refresh-token or logout-everywhere endpoint.
- No login rate limiting or account lockout yet (add Bucket4j or Spring Security's lockout handling).
- Single JWT secret shared for signing and verifying; use asymmetric keys if other services need to verify tokens.
- Schema is managed by Hibernate `ddl-auto=update`; switch to Flyway or Liquibase for production.
- Ideas: leave requests, payroll, audit log, Dockerfile for the app, OpenAPI/Swagger docs, GitHub Actions CI.

## License

MIT
