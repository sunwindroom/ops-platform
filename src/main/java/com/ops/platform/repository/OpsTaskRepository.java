package com.ops.platform.repository;

import com.ops.platform.entity.OpsTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OpsTaskRepository extends JpaRepository<OpsTask, Long> {
    List<OpsTask> findByEnabledTrue();
}
