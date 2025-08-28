package com.example.server.repository;

import com.example.server.model.ConsumptionHistory;
import com.example.server.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface ConsumptionHistoryRepository extends JpaRepository<ConsumptionHistory, Long> {
    List<ConsumptionHistory> findByUserOrderByDateDesc(User user);

    @Query("SELECT SUM(c.amount) FROM ConsumptionHistory c WHERE c.user = :user AND c.type = :type")
    BigDecimal sumByUserAndType(@Param("user") User user, @Param("type") String type);
}