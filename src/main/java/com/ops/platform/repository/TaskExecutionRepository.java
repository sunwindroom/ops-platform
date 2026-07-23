package com.ops.platform.repository;

import com.ops.platform.entity.TaskExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaskExecutionRepository extends JpaRepository<TaskExecution, Long> {
    List<TaskExecution> findByTaskIdOrderByStartTimeDesc(Long taskId);
    List<TaskExecution> findTop100ByOrderByStartTimeDesc();
}
