package com.lowcarbon.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 密码加密与验证工具类。
 * <p>
 * 使用 SHA-256 算法配合随机盐值（salt）对密码进行加盐哈希，
 * 避免明文存储，并防御彩虹表攻击。
 * <p>
 * 存储格式：
 * <pre>
 *   原始密码 → 拼接盐值 → SHA-256 → Base64 编码 → 存入数据库 password_hash 字段
 * </pre>
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public final class PasswordUtil {

    private static final Logger LOGGER = Logger.getLogger(PasswordUtil.class.getName());

    /** 哈希算法名称。 */
    private static final String ALGORITHM = "SHA-256";

    /** 盐值长度（字节），Base64 编码后约 24 个字符。 */
    private static final int SALT_LENGTH = 16;

    /** 单次加密迭代次数（增强安全性）。 */
    private static final int ITERATIONS = 1000;

    private PasswordUtil() {
        // 工具类禁止实例化
    }

    /**
     * 生成随机盐值。
     *
     * @return Base64 编码的盐值字符串
     */
    public static String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] saltBytes = new byte[SALT_LENGTH];
        random.nextBytes(saltBytes);
        return Base64.getEncoder().encodeToString(saltBytes);
    }

    /**
     * 对密码进行加盐哈希。
     *
     * @param rawPassword 用户输入的明文密码
     * @param salt        盐值（通常由 {@link #generateSalt()} 生成）
     * @return Base64 编码的哈希字符串；若加密失败返回 null
     */
    public static String hashPassword(String rawPassword, String salt) {
        if (rawPassword == null || salt == null) {
            LOGGER.log(Level.WARNING, "密码或盐值为 null，无法加密");
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            String saltedInput = rawPassword + salt;
            byte[] hashBytes = digest.digest(saltedInput.getBytes());

            // 多轮迭代，增加暴力破解成本
            for (int i = 1; i < ITERATIONS; i++) {
                hashBytes = digest.digest(hashBytes);
            }
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            LOGGER.log(Level.SEVERE, "SHA-256 算法不可用", e);
            throw new RuntimeException("系统加密组件异常", e);
        }
    }

    /**
     * 验证密码是否匹配。
     *
     * @param rawPassword  用户输入的明文密码
     * @param storedHash   数据库中存储的哈希值
     * @param storedSalt   数据库中存储的盐值
     * @return {@code true} 表示密码正确
     */
    public static boolean verifyPassword(String rawPassword, String storedHash, String storedSalt) {
        if (rawPassword == null || storedHash == null || storedSalt == null) {
            return false;
        }
        String computedHash = hashPassword(rawPassword, storedSalt);
        return constantTimeEquals(computedHash, storedHash);
    }

    /**
     * 恒定时间字符串比较，防止时序攻击（timing attack）。
     *
     * @param a 字符串 A
     * @param b 字符串 B
     * @return 是否相等
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
