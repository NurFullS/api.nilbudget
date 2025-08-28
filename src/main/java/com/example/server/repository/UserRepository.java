package com.example.server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.server.model.User;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByEmail(String email);
    boolean existsByEmail(String email);
}