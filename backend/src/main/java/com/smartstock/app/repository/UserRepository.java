package com.smartstock.app.repository;

import com.smartstock.app.domain.User;
import com.smartstock.app.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    @Query("select count(distinct user.id) from User user join user.roles role where role = :role")
    long countUsersWithRole(Role role);
}
