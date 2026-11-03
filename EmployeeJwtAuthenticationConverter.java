package com.example.ems.security;

import com.example.ems.model.Employee;
import com.example.ems.repository.EmployeeRepository;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Turns a verified JWT into an authentication. The role and account state are re-read from the
 * database on every request, so disabling an account, deleting it, or changing its role takes
 * effect immediately, even for tokens that have not expired yet.
 */
@Component
public class EmployeeJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final EmployeeRepository employees;

    public EmployeeJwtAuthenticationConverter(EmployeeRepository employees) {
        this.employees = employees;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Employee employee = employees
                .findByUsername(jwt.getSubject())
                .filter(Employee::isEnabled)
                .orElseThrow(() -> new BadCredentialsException("Account is not active"));
        List<GrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority("ROLE_" + employee.getRole().name()));
        return new JwtAuthenticationToken(jwt, authorities, employee.getUsername());
    }
}
