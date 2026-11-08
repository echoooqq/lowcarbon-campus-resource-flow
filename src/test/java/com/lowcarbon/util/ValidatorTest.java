package com.lowcarbon.util;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Validator 单元测试。
 * <p>
 * 覆盖：用户名、密码、价格、信用分、物品名/描述、危险字符检测。
 *
 * @author lowcarbon-team
 * @since 2026-11-09
 */
public class ValidatorTest {

    // ==================== isBlank ====================

    @Test
    public void testIsBlank() {
        assertTrue(Validator.isBlank(null));
        assertTrue(Validator.isBlank(""));
        assertTrue(Validator.isBlank("   "));
        assertTrue(Validator.isBlank("\t\n"));
        assertFalse(Validator.isBlank("a"));
    }

    // ==================== validateUsername ====================

    @Test
    public void testValidateUsername_Valid() {
        assertNull("3 字母应通过", Validator.validateUsername("abc"));
        assertNull("20 字符应通过", Validator.validateUsername("a1234567890123456789"));
        assertNull("含下划线应通过", Validator.validateUsername("user_test1"));
    }

    @Test
    public void testValidateUsername_Blank() {
        assertNotNull("null 应报错", Validator.validateUsername(null));
        assertNotNull("空串应报错", Validator.validateUsername(""));
        assertNotNull("全空白应报错", Validator.validateUsername("   "));
    }

    @Test
    public void testValidateUsername_TooShort() {
        assertNotNull("2 字符应报错", Validator.validateUsername("ab"));
        assertNotNull("1 字符应报错", Validator.validateUsername("a"));
    }

    @Test
    public void testValidateUsername_TooLong() {
        StringBuilder sb = new StringBuilder("a");
        for (int i = 0; i < 20; i++) {
            sb.append("b");
        }
        assertNotNull("21 字符应报错", Validator.validateUsername(sb.toString()));
    }

    @Test
    public void testValidateUsername_InvalidFormat() {
        assertNotNull("数字开头应报错", Validator.validateUsername("1abc"));
        assertNotNull("含中文应报错", Validator.validateUsername("张三"));
        assertNotNull("含空格应报错", Validator.validateUsername("ab c"));
        assertNotNull("非法特殊字符", Validator.validateUsername("ab@c"));
    }

    // ==================== validatePassword ====================

    @Test
    public void testValidatePassword_Valid() {
        assertNull("6 位密码应通过", Validator.validatePassword("123456"));
        assertNull("32 位密码应通过",
                Validator.validatePassword("abcdefghijklmnopqrstuvwxyz012345"));
        assertNull("含特殊字符应通过", Validator.validatePassword("P@ssw0rd!"));
    }

    @Test
    public void testValidatePassword_Blank() {
        assertNotNull("null 应报错", Validator.validatePassword(null));
        assertNotNull("空串应报错", Validator.validatePassword(""));
    }

    @Test
    public void testValidatePassword_TooShort() {
        assertNotNull("5 位应报错", Validator.validatePassword("12345"));
    }

    @Test
    public void testValidatePassword_TooLong() {
        String pwd = "a".repeat(33);
        assertNotNull("33 位应报错", Validator.validatePassword(pwd));
    }

    // ==================== validatePasswordConfirm ====================

    @Test
    public void testValidatePasswordConfirm_Match() {
        assertNull("两次一致应通过", Validator.validatePasswordConfirm("abc123", "abc123"));
    }

    @Test
    public void testValidatePasswordConfirm_Mismatch() {
        assertNotNull("不一致应报错", Validator.validatePasswordConfirm("abc123", "abc456"));
    }

    // ==================== validatePrice ====================

    @Test
    public void testValidatePrice_Valid() {
        assertNull("0 元应通过", Validator.validatePrice(0.0));
        assertNull("正数应通过", Validator.validatePrice(99.9));
        assertNull("上限应通过", Validator.validatePrice(99999.99));
    }

    @Test
    public void testValidatePrice_Invalid() {
        assertNotNull("负数应报错", Validator.validatePrice(-1.0));
        assertNotNull("超上限应报错", Validator.validatePrice(100000.0));
    }

    // ==================== validateCreditScore ====================

    @Test
    public void testValidateCreditScore_Valid() {
        assertNull("0 应通过", Validator.validateCreditScore(0));
        assertNull("100 应通过", Validator.validateCreditScore(100));
        assertNull("200 应通过", Validator.validateCreditScore(200));
    }

    @Test
    public void testValidateCreditScore_Invalid() {
        assertNotNull("负数应报错", Validator.validateCreditScore(-1));
        assertNotNull("超上限应报错", Validator.validateCreditScore(201));
    }

    // ==================== validateItemName ====================

    @Test
    public void testValidateItemName() {
        assertNull("正常名称应通过", Validator.validateItemName("高等数学教材"));
        assertNotNull("空串应报错", Validator.validateItemName(""));
        assertNotNull("null 应报错", Validator.validateItemName(null));
        // 50 字符应通过
        assertNull(Validator.validateItemName("a".repeat(50)));
        // 51 字符应报错
        assertNotNull(Validator.validateItemName("a".repeat(51)));
    }

    // ==================== validateComment ====================

    @Test
    public void testValidateComment() {
        assertNull("正常评论应通过", Validator.validateComment("很不错的物品，推荐！"));
        assertNotNull("空评论应报错", Validator.validateComment(""));
        assertNotNull("null 应报错", Validator.validateComment(null));
    }

    // ==================== containsDangerousChars ====================

    @Test
    public void testContainsDangerousChars() {
        assertTrue("分号应被检测", Validator.containsDangerousChars("a;b"));
        assertTrue("单引号应被检测", Validator.containsDangerousChars("a'b"));
        assertTrue("双引号应被检测", Validator.containsDangerousChars("a\"b"));
        assertTrue("反斜杠应被检测", Validator.containsDangerousChars("a\\b"));
        assertFalse("正常文本应通过", Validator.containsDangerousChars("hello world"));
        assertFalse("null 应返回 false", Validator.containsDangerousChars(null));
    }

    // ==================== truncate ====================

    @Test
    public void testTruncate() {
        assertEquals("hello", Validator.truncate("hello world", 5));
        assertEquals("hello", Validator.truncate("hello", 10));
        assertNull(Validator.truncate(null, 5));
    }

    // ==================== isLengthInRange ====================

    @Test
    public void testIsLengthInRange() {
        assertFalse(Validator.isLengthInRange(null, 1, 5));
        assertFalse(Validator.isLengthInRange("abc", 4, 10));
        assertFalse(Validator.isLengthInRange("abcde", 1, 4));
        assertTrue(Validator.isLengthInRange("abc", 1, 5));
        assertTrue(Validator.isLengthInRange("abcde", 1, 5));
    }
}
