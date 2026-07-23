package com.ops.platform.service;

import com.ops.platform.entity.Asset;
import com.ops.platform.repository.AssetRepository;
import com.ops.platform.util.AesUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class AssetService {

    @Autowired
    private AssetRepository assetRepository;

    public List<Asset> findAll() {
        return assetRepository.findAll();
    }

    public List<Asset> findByType(String type) {
        return assetRepository.findByType(type);
    }

    public Optional<Asset> findById(Long id) {
        return assetRepository.findById(id);
    }

    /** 新增/编辑资产。若表单传入的是明文密码，这里统一加密后存储；若未修改密码则保留原值 */
    public Asset save(Asset asset, String rawSshPassword, String rawSshPrivateKey) {
        if (rawSshPassword != null && !rawSshPassword.isBlank()) {
            asset.setSshPasswordEnc(AesUtil.encrypt(rawSshPassword));
        } else if (asset.getId() != null) {
            assetRepository.findById(asset.getId()).ifPresent(old -> asset.setSshPasswordEnc(old.getSshPasswordEnc()));
        }
        if (rawSshPrivateKey != null && !rawSshPrivateKey.isBlank()) {
            asset.setSshPrivateKeyEnc(AesUtil.encrypt(rawSshPrivateKey));
        } else if (asset.getId() != null) {
            assetRepository.findById(asset.getId()).ifPresent(old -> asset.setSshPrivateKeyEnc(old.getSshPrivateKeyEnc()));
        }
        asset.setUpdateTime(LocalDateTime.now());
        if (asset.getId() == null) {
            asset.setCreateTime(LocalDateTime.now());
        }
        return assetRepository.save(asset);
    }

    public void delete(Long id) {
        assetRepository.deleteById(id);
    }
}
