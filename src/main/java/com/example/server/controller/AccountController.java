package com.example.server.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.server.model.User;
import com.example.server.repository.UserRepository;

@RestController
@RequestMapping("/account")
public class AccountController {

    @Autowired
    UserRepository userRepository;

    @GetMapping("all-users")
    public ResponseEntity<?> getAllUsers() {
        List<User> users = userRepository.findAll();

        return ResponseEntity.status(200).body(users);
    }
}
