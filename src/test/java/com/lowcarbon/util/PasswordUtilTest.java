package com.lowcarbon.util;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * PasswordUtil 单元测试。
 * <p>
 * 覆盖：盐值生成、密码哈希、恒定时间验证、边界条件。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class PasswordUtilTest {

    // ==================== generateSalt ====================

    @Test
    public void testGenerateSalt() {
        String salt = PasswordUtil.generateSalt();
        assertNotNull("盐值不应为 null", salt);
        assertFalse("盐值不应为空", salt.isEmpty());
        // 16 字节 Base64 编码应为 24 字符
        assertEquals("16 字节盐 Base64 编码后长度应为 24", 24, salt.length());
    }

    @Test
    public void testGenerateSalt_Unique() {
        String s1 = PasswordUtil.generateSalt();
        String s2 = PasswordUtil.generateSalt();
        assertNotEquals("两次生成的盐值应不同", s1, s2);
    }

    // ==================== hashPassword ====================

    @Test
    public void testHashPassword_Normal() {
        String hash = PasswordUtil.hashPassword("myPassword123", "testSalt");
        assertNotNull("哈希结果不应为 null", hash);
        assertFalse(hash.isEmpty());
    }

    @Test
    public void testHashPassword_NullPassword() {
        assertNull("密码为 null 时应返回 null", PasswordUtil.hashPassword(null, "salt"));
    }

    @Test
    public void testHashPassword_NullSalt() {
        assertNull("盐值为 null 时应返回 null", PasswordUtil.hashPassword("pwd", null));
    }

    @Test
    public void testHashPassword_SameInputDeterministic() {
        String h1 = PasswordUtil.hashPassword("abc", "def");
        String h2 = PasswordUtil.hashPassword("abc", "def");
        assertEquals("相同密码+盐值应产生相同哈希", h1, h2);
    }

    @Test
    public void testHashPassword_DifferentSaltDifferentHash() {
        String h1 = PasswordUtil.hashPassword("abc", "salt1");
        String h2 = PasswordUtil.hashPassword("abc", "salt2");
        assertNotEquals("相同密码不同盐值应产生不同哈希", h1, h2);
    }

    // ==================== verifyPassword ====================

    @Test
    public void testVerifyPassword_Correct() {
        String raw = "correctPass";
        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hashPassword(raw, salt);

        assertTrue("正确密码应验证通过",
                PasswordUtil.verifyPassword(raw, hash, salt));
    }

    @Test
    public void testVerifyPassword_Wrong() {
        String raw = "correctPass";
        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hashPassword(raw, salt);

        assertFalse("错误密码应验证失败",
                PasswordUtil.verifyPassword("wrongPass", hash, salt));
    }

    @Test
    public void testVerifyPassword_NullInputs() {
        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hashPassword("pwd", salt);

        assertFalse(PasswordUtil.verifyPassword(null, hash, salt));
        assertFalse(PasswordUtil.verifyPassword("pwd", null, salt));
        assertFalse(PasswordUtil.verifyPassword("pwd", hash, null));
        assertFalse(PasswordUtil.verifyPassword(null, null, null));
    }

    @Test
    public void testVerifyPassword_EmptyPassword() {
        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hashPassword("", salt);

        assertTrue("空字符串密码也应能正确验证",
                PasswordUtil.verifyPassword("", hash, salt));
    }

    @Test
    public void testVerifyPassword_RoundTrip() {
        // 模拟注册-登录完整流程
        String password = "Student@2026";
        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hashPassword(password, salt);

        assertTrue("注册后相同密码登录应通过",
                PasswordUtil.verifyPassword(password, hash, salt));
        assertFalse("注册后不同密码登录应失败",
                PasswordUtil.verifyPassword("OtherPass", hash, salt));
    }
}
