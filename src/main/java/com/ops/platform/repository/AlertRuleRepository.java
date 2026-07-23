package com.ops.platform.repository;

import com.ops.platform.entity.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {
    List<AlertRule> findByEnabledTrue();
}
