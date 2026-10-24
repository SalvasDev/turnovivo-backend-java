package com.saldev.turnovivo.repository;

import com.saldev.turnovivo.model.entity.User;
import com.saldev.turnovivo.model.enums.Role;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    List<User> findAllByOrderByEmailAsc();

    List<User> findByRoleOrderByEmailAsc(Role role);
    /*
    * Synchronized with UserService: "findByEmail(email)"
    * Spring Boot analyzes the method name and automatically generates the SQL:
    * SELECT * FROM users WHERE email = ?
    *
    * Optional is used to handle missing data gracefully.
    */
    Optional<User> findByEmail(String email);

    Optional<User> findFirstByRole(Role role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") UUID userId);
}
