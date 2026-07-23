package com.ops.platform.repository;

import com.ops.platform.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AssetRepository extends JpaRepository<Asset, Long> {
    List<Asset> findByType(String type);
    List<Asset> findByMonitorEnabledTrue();
    List<Asset> findByParentHostId(Long parentHostId);
}
