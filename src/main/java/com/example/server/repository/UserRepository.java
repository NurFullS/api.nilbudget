package com.example.server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.server.model.User;
// import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByEmail(String email);
    Optional<User> findById(Long id);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}