package com.ops.platform.repository;

import com.ops.platform.entity.AlertRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AlertRecordRepository extends JpaRepository<AlertRecord, Long> {
    List<AlertRecord> findByStatusOrderByCreateTimeDesc(String status);
    AlertRecord findFirstByRuleIdAndAssetIdAndStatusOrderByCreateTimeDesc(Long ruleId, Long assetId, String status);
    List<AlertRecord> findTop50ByOrderByCreateTimeDesc();
    long countByStatus(String status);
}
