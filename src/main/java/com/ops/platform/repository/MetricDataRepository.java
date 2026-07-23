package com.ops.platform.repository;

import com.ops.platform.entity.MetricData;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface MetricDataRepository extends JpaRepository<MetricData, Long> {

    List<MetricData> findByAssetIdAndMetricTypeAndCollectTimeBetweenOrderByCollectTimeAsc(
            Long assetId, String metricType, LocalDateTime start, LocalDateTime end);

    List<MetricData> findTop5ByAssetIdAndMetricTypeOrderByCollectTimeDesc(Long assetId, String metricType);

    MetricData findFirstByAssetIdAndMetricTypeOrderByCollectTimeDesc(Long assetId, String metricType);

    void deleteByCollectTimeBefore(LocalDateTime time);
}
