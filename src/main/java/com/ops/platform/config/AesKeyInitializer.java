package com.ops.platform.config;

import com.ops.platform.util.AesUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AesKeyInitializer {

    @Value("${ops.security.aes-secret:OpsPlatformKey16}")
    private String aesSecret;

    @PostConstruct
    public void init() {
        AesUtil.setSecret(aesSecret);
    }
}
