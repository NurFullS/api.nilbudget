package com.example.server.controller;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.server.model.ConsumptionHistory;
import com.example.server.model.User;
import com.example.server.repository.ConsumptionHistoryRepository;
import com.example.server.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.math.BigDecimal;
import java.security.Key;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class UserController {

    private final UserRepository userRepository;
    private final ConsumptionHistoryRepository consumptionHistoryRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final String jwtSecret = "mySuperSecretKey12345678901234567890";

    public UserController(UserRepository userRepository, ConsumptionHistoryRepository consumptionHistoryRepository) {
        this.userRepository = userRepository;
        this.consumptionHistoryRepository = consumptionHistoryRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user, HttpServletResponse response) {
        try {
            if (userRepository.existsByEmail(user.getEmail())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Email already in use");
            }

            user.setPassword(passwordEncoder.encode(user.getPassword()));
            if (user.getBalance() == null)
                user.setBalance(BigDecimal.ZERO);
            if (user.getValute() == null)
                user.setValute("USD");

            User savedUser = userRepository.save(user);

            String token = generateToken(savedUser.getId());
            setJwtCookie(response, token);

            savedUser.setPassword(null);
            return ResponseEntity.ok(savedUser);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error registering user");
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody User user, HttpServletResponse response) {
        try {
            User existingUser = userRepository.findByEmail(user.getEmail());
            if (existingUser != null && passwordEncoder.matches(user.getPassword(), existingUser.getPassword())) {
                String token = generateToken(existingUser.getId());
                setJwtCookie(response, token);

                existingUser.setPassword(null);
                return ResponseEntity.ok(existingUser);
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid email or password");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error logging in");
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request) {
        User user = getUserFromRequest(request);
        if (user == null)
            return ResponseEntity.ok(null);
        user.setPassword(null);
        return ResponseEntity.ok(user);
    }

    @PostMapping("/balance")
    public ResponseEntity<?> updateBalance(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        try {
            BigDecimal amount = new BigDecimal(body.get("amount").toString());
            String currency = body.get("currency").toString();

            User user = getUserFromRequest(request);
            if (user == null) {
                return ResponseEntity.ok(null);
            }

            if (user.getBalance() == null)
                user.setBalance(BigDecimal.ZERO);

            user.setBalance(user.getBalance().add(amount));
            user.setValute(currency.toUpperCase());

            userRepository.save(user);
            user.setPassword(null);
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            return ResponseEntity.ok(null);
        }
    }

    private String generateToken(Long userId) {
        long expirationMillis = 1000L * 60 * 60 * 24 * 7;
        Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes());

        return Jwts.builder()
                .setSubject(userId.toString())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMillis))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    private void setJwtCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie("jwt", token);
        cookie.setHttpOnly(true);
        cookie.setMaxAge(7 * 24 * 60 * 60);
        cookie.setPath("/");
        response.addCookie(cookie);
    }

    private User getUserFromRequest(HttpServletRequest request) {
        try {
            Cookie[] cookies = request.getCookies();
            if (cookies == null)
                return null;

            Cookie jwtCookie = Arrays.stream(cookies)
                    .filter(c -> c.getName().equals("jwt"))
                    .findFirst()
                    .orElse(null);
            if (jwtCookie == null)
                return null;

            String token = jwtCookie.getValue();
            Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes());

            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            Long userId = Long.parseLong(claims.getSubject());
            return userRepository.findById(userId).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    @PostMapping("/consumption")
    public ResponseEntity<?> subtractBalance(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        try {
            BigDecimal amount = new BigDecimal(body.get("amount").toString());
            String description = body.getOrDefault("description", "").toString();

            if (amount.compareTo(BigDecimal.ZERO) <= 0)
                return ResponseEntity.ok(null);

            User user = getUserFromRequest(request);
            if (user == null)
                return ResponseEntity.ok(null);

            if (user.getBalance() == null)
                user.setBalance(BigDecimal.ZERO);
            if (user.getBalance().compareTo(amount) < 0)
                return ResponseEntity.ok(null);

            user.setBalance(user.getBalance().subtract(amount));
            userRepository.save(user);

            ConsumptionHistory history = new ConsumptionHistory();
            history.setUser(user);
            history.setAmount(amount);
            history.setDescription(description);
            history.setDate(LocalDateTime.now());
            history.setType("EXPENSE");
            consumptionHistoryRepository.save(history);

            user.setPassword(null);
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            return ResponseEntity.ok(null);
        }
    }

    @GetMapping("/consumption/history")
    public ResponseEntity<?> getConsumptionHistory(HttpServletRequest request) {
        try {
            User user = getUserFromRequest(request);
            if (user == null) {
                return ResponseEntity.ok(null);
            }

            List<ConsumptionHistory> history = consumptionHistoryRepository.findByUserOrderByDateDesc(user);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            return ResponseEntity.ok(null);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        Cookie cookie = new Cookie("jwt", null);
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        cookie.setPath("/");
        response.addCookie(cookie);

        return ResponseEntity.ok(null);
    }

    @GetMapping("/summary")
    public ResponseEntity<?> getSummary(HttpServletRequest request) {
        try {
            User user = getUserFromRequest(request);
            if (user == null) {
                return ResponseEntity.ok(null);
            }

            BigDecimal totalIncome = consumptionHistoryRepository.sumByUserAndType(user, "INCOME");
            BigDecimal totalExpense = consumptionHistoryRepository.sumByUserAndType(user, "EXPENSE");

            totalIncome = totalIncome != null ? totalIncome : BigDecimal.ZERO;
            totalExpense = totalExpense != null ? totalExpense : BigDecimal.ZERO;
            user.setBalance(user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO);

            return ResponseEntity.ok(Map.of(
                    "totalIncome", totalIncome,
                    "totalExpense", totalExpense,
                    "balance", user.getBalance()));
        } catch (Exception e) {
            return ResponseEntity.ok(null);
        }
    }

    @PostMapping("/income")
    public ResponseEntity<?> addIncome(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        try {
            BigDecimal amount = new BigDecimal(body.get("amount").toString());
            String description = body.getOrDefault("description", "").toString();
            String currency = body.getOrDefault("currency", "USD").toString();

            if (amount.compareTo(BigDecimal.ZERO) <= 0)
                return ResponseEntity.ok(null);

            User user = getUserFromRequest(request);
            if (user == null)
                return ResponseEntity.ok(null);

            if (user.getBalance() == null)
                user.setBalance(BigDecimal.ZERO);

            user.setBalance(user.getBalance().add(amount));
            user.setValute(currency.toUpperCase());
            userRepository.save(user);

            ConsumptionHistory history = new ConsumptionHistory();
            history.setUser(user);
            history.setAmount(amount);
            history.setDescription(description);
            history.setDate(LocalDateTime.now());
            history.setType("INCOME");
            consumptionHistoryRepository.save(history);

            user.setPassword(null);
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            return ResponseEntity.ok(null);
        }
    }

    @PostMapping("/valute")
    public ResponseEntity<?> updateValute(HttpServletRequest request, @RequestBody Map<String, String> body) {
        try {
            User user = getUserFromRequest(request);
            if (user == null) {
                return ResponseEntity.ok(null);
            }

            String newValute = body.get("valute");
            if (newValute == null || newValute.isEmpty()) {
                return ResponseEntity.ok(null);
            }

            user.setValute(newValute.toUpperCase());
            userRepository.save(user);

            user.setPassword(null);
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            return ResponseEntity.ok(null);
        }
    }

}
