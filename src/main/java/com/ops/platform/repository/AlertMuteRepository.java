package com.ops.platform.repository;

import com.ops.platform.entity.AlertMute;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface AlertMuteRepository extends JpaRepository<AlertMute, Long> {
    List<AlertMute> findByEndTimeAfter(LocalDateTime now);
}
