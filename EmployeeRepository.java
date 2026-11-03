package com.example.ems.repository;

import com.example.ems.model.Employee;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    /**
     * Case-insensitive search over name, email and username, optionally restricted to a
     * department. Empty strings mean "no filter" (callers never pass null).
     */
    @Query("""
            select e from Employee e
            where (:search = ''
                   or lower(e.fullName) like lower(concat('%', :search, '%'))
                   or lower(e.email) like lower(concat('%', :search, '%'))
                   or lower(e.username) like lower(concat('%', :search, '%')))
              and (:department = '' or lower(e.department) = lower(:department))
            """)
    Page<Employee> search(
            @Param("search") String search, @Param("department") String department, Pageable pageable);
}
