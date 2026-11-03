package com.example.ems.repository;

import com.example.ems.model.AttendanceRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceRepository extends JpaRepository<AttendanceRecord, Long> {

    Optional<AttendanceRecord> findByEmployeeIdAndAttendanceDate(Long employeeId, LocalDate attendanceDate);

    @Query("""
            select a from AttendanceRecord a join fetch a.employee e
            where a.attendanceDate between :from and :to
            order by a.attendanceDate desc, e.fullName asc
            """)
    List<AttendanceRecord> findInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select a from AttendanceRecord a join fetch a.employee e
            where e.id = :employeeId and a.attendanceDate between :from and :to
            order by a.attendanceDate desc
            """)
    List<AttendanceRecord> findForEmployeeInRange(
            @Param("employeeId") Long employeeId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Modifying
    @Query("delete from AttendanceRecord a where a.employee.id = :employeeId")
    void deleteByEmployeeId(@Param("employeeId") Long employeeId);
}
