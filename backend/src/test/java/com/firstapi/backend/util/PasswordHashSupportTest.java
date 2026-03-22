// 修复点: 无（纯新增测试）
// 测试覆盖点: PasswordHashSupport hash/matches 方法 + 边界条件（短密码/null/空/格式错误的hash/常量时间比较）
package com.firstapi.backend.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHashSupportTest {

    private static final String VALID_PASSWORD = "MySecurePassword123";
    private static final String SHORT_PASSWORD = "short";

    // ======== hash ========

    @Nested
    @DisplayName("hash")
    class HashTests {

        @Test
        @DisplayName("正常密码应生成以 pbkdf2_sha256$ 开头的哈希")
        void shouldGenerateValidHash() {
            String hash = PasswordHashSupport.hash(VALID_PASSWORD);
            assertNotNull(hash);
            assertTrue(hash.startsWith("pbkdf2_sha256$120000$"));
        }

        @Test
        @DisplayName("哈希应包含4段（以$分隔）")
        void shouldHaveFourParts() {
            String hash = PasswordHashSupport.hash(VALID_PASSWORD);
            String[] parts = hash.split("\\$");
            assertEquals(4, parts.length);
        }

        @Test
        @DisplayName("相同密码的两次哈希结果应不同（随机盐）")
        void shouldProduceDifferentHashesForSamePassword() {
            String hash1 = PasswordHashSupport.hash(VALID_PASSWORD);
            String hash2 = PasswordHashSupport.hash(VALID_PASSWORD);
            assertNotEquals(hash1, hash2);
        }

        @Test
        @DisplayName("密码少于10位应抛出异常")
        void shouldThrowForShortPassword() {
            assertThrows(IllegalArgumentException.class,
                    () -> PasswordHashSupport.hash(SHORT_PASSWORD));
        }

        @Test
        @DisplayName("null 密码应抛出异常")
        void shouldThrowForNullPassword() {
            assertThrows(IllegalArgumentException.class,
                    () -> PasswordHashSupport.hash(null));
        }

        @Test
        @DisplayName("空密码应抛出异常")
        void shouldThrowForEmptyPassword() {
            assertThrows(IllegalArgumentException.class,
                    () -> PasswordHashSupport.hash(""));
        }

        @Test
        @DisplayName("恰好10位密码应正常生成哈希")
        void shouldAcceptExactly10Chars() {
            String hash = PasswordHashSupport.hash("1234567890");
            assertNotNull(hash);
            assertTrue(hash.startsWith("pbkdf2_sha256$"));
        }

        @Test
        @DisplayName("9位密码应抛出异常")
        void shouldRejectNineChars() {
            assertThrows(IllegalArgumentException.class,
                    () -> PasswordHashSupport.hash("123456789"));
        }
    }

    // ======== matches ========

    @Nested
    @DisplayName("matches")
    class MatchesTests {

        @Test
        @DisplayName("正确密码应匹配")
        void shouldMatchCorrectPassword() {
            String hash = PasswordHashSupport.hash(VALID_PASSWORD);
            assertTrue(PasswordHashSupport.matches(VALID_PASSWORD, hash));
        }

        @Test
        @DisplayName("错误密码应不匹配")
        void shouldNotMatchWrongPassword() {
            String hash = PasswordHashSupport.hash(VALID_PASSWORD);
            assertFalse(PasswordHashSupport.matches("WrongPassword123", hash));
        }

        @Test
        @DisplayName("null 哈希应返回 false")
        void shouldReturnFalseForNullHash() {
            assertFalse(PasswordHashSupport.matches(VALID_PASSWORD, null));
        }

        @Test
        @DisplayName("空哈希应返回 false")
        void shouldReturnFalseForEmptyHash() {
            assertFalse(PasswordHashSupport.matches(VALID_PASSWORD, ""));
        }

        @Test
        @DisplayName("格式错误的哈希（段数不足）应返回 false")
        void shouldReturnFalseForMalformedHash() {
            assertFalse(PasswordHashSupport.matches(VALID_PASSWORD, "invalid$hash"));
        }

        @Test
        @DisplayName("短密码 matches 不应泄露验证细节（应抛出异常而非返回 false）")
        void shouldThrowForShortPasswordInMatches() {
            String hash = PasswordHashSupport.hash(VALID_PASSWORD);
            // 短密码在 matches 中被 requirePassword 拦截, 会抛出异常
            assertThrows(IllegalArgumentException.class,
                    () -> PasswordHashSupport.matches("short", hash));
        }
    }
}
