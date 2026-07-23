package com.ops.platform.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * 简单AES加解密工具，用于数据库中SSH密码/密钥等敏感信息的存储保护。
 * 生产环境建议将 SECRET 通过环境变量注入，而不是硬编码。
 */
public class AesUtil {

    // 16字节密钥，建议部署时通过环境变量 OPS_AES_SECRET 覆盖（见 application.yml）
    private static String SECRET = "OpsPlatformKey16";

    public static void setSecret(String secret) {
        if (secret != null && secret.length() == 16) {
            SECRET = secret;
        }
    }

    public static String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) return "";
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(SECRET.getBytes(), "AES"));
            byte[] encrypted = cipher.doFinal(plain.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("加密失败", e);
        }
    }

    public static String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isEmpty()) return "";
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(SECRET.getBytes(), "AES"));
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encrypted));
            return new String(decrypted, "UTF-8");
        } catch (Exception e) {
            throw new RuntimeException("解密失败", e);
        }
    }
}
