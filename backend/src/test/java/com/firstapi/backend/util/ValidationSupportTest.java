// 修复点: 无（纯新增测试）
// 测试覆盖点: ValidationSupport 全部5个方法 + 边界条件（null/空字符串/空格/正常值/负数/零）
package com.firstapi.backend.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValidationSupportTest {

    // ======== requireNotBlank ========

    @Nested
    @DisplayName("requireNotBlank")
    class RequireNotBlankTests {

        @Test
        @DisplayName("正常字符串应返回 trim 后的值")
        void shouldReturnTrimmedValue() {
            assertEquals("hello", ValidationSupport.requireNotBlank("  hello  ", "err"));
        }

        @Test
        @DisplayName("null 应抛出 IllegalArgumentException")
        void shouldThrowOnNull() {
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> ValidationSupport.requireNotBlank(null, "不能为空")
            );
            assertEquals("不能为空", ex.getMessage());
        }

        @Test
        @DisplayName("空字符串应抛出 IllegalArgumentException")
        void shouldThrowOnEmpty() {
            assertThrows(IllegalArgumentException.class,
                    () -> ValidationSupport.requireNotBlank("", "不能为空"));
        }

        @Test
        @DisplayName("纯空格字符串应抛出 IllegalArgumentException")
        void shouldThrowOnWhitespace() {
            assertThrows(IllegalArgumentException.class,
                    () -> ValidationSupport.requireNotBlank("   ", "不能为空"));
        }

        @Test
        @DisplayName("tab 和换行符应抛出 IllegalArgumentException")
        void shouldThrowOnTabsAndNewlines() {
            assertThrows(IllegalArgumentException.class,
                    () -> ValidationSupport.requireNotBlank("\t\n", "不能为空"));
        }

        @Test
        @DisplayName("单个字符应正常返回")
        void shouldHandleSingleChar() {
            assertEquals("a", ValidationSupport.requireNotBlank("a", "err"));
        }
    }

    // ======== requirePositive ========

    @Nested
    @DisplayName("requirePositive")
    class RequirePositiveTests {

        @Test
        @DisplayName("正数应返回 int 值")
        void shouldReturnPositiveValue() {
            assertEquals(5, ValidationSupport.requirePositive(5, "err"));
        }

        @Test
        @DisplayName("null 应抛出异常")
        void shouldThrowOnNull() {
            assertThrows(IllegalArgumentException.class,
                    () -> ValidationSupport.requirePositive(null, "必须为正数"));
        }

        @Test
        @DisplayName("0 应抛出异常")
        void shouldThrowOnZero() {
            assertThrows(IllegalArgumentException.class,
                    () -> ValidationSupport.requirePositive(0, "必须为正数"));
        }

        @Test
        @DisplayName("负数应抛出异常")
        void shouldThrowOnNegative() {
            assertThrows(IllegalArgumentException.class,
                    () -> ValidationSupport.requirePositive(-1, "必须为正数"));
        }

        @Test
        @DisplayName("Integer.MAX_VALUE 应正常返回")
        void shouldHandleMaxValue() {
            assertEquals(Integer.MAX_VALUE,
                    ValidationSupport.requirePositive(Integer.MAX_VALUE, "err"));
        }

        @Test
        @DisplayName("1 作为边界正数应正常返回")
        void shouldHandleBoundaryOne() {
            assertEquals(1, ValidationSupport.requirePositive(1, "err"));
        }
    }

    // ======== requireNonNegative ========

    @Nested
    @DisplayName("requireNonNegative")
    class RequireNonNegativeTests {

        @Test
        @DisplayName("0 应正常返回")
        void shouldAcceptZero() {
            assertEquals(0, ValidationSupport.requireNonNegative(0, "err"));
        }

        @Test
        @DisplayName("正数应正常返回")
        void shouldAcceptPositive() {
            assertEquals(10, ValidationSupport.requireNonNegative(10, "err"));
        }

        @Test
        @DisplayName("null 应抛出异常")
        void shouldThrowOnNull() {
            assertThrows(IllegalArgumentException.class,
                    () -> ValidationSupport.requireNonNegative(null, "不能为负数"));
        }

        @Test
        @DisplayName("负数应抛出异常")
        void shouldThrowOnNegative() {
            assertThrows(IllegalArgumentException.class,
                    () -> ValidationSupport.requireNonNegative(-1, "不能为负数"));
        }

        @Test
        @DisplayName("Integer.MIN_VALUE 应抛出异常")
        void shouldThrowOnMinValue() {
            assertThrows(IllegalArgumentException.class,
                    () -> ValidationSupport.requireNonNegative(Integer.MIN_VALUE, "err"));
        }
    }

    // ======== isBlank ========

    @Nested
    @DisplayName("isBlank")
    class IsBlankTests {

        @Test
        @DisplayName("null 应返回 true")
        void shouldReturnTrueForNull() {
            assertTrue(ValidationSupport.isBlank(null));
        }

        @Test
        @DisplayName("空字符串应返回 true")
        void shouldReturnTrueForEmpty() {
            assertTrue(ValidationSupport.isBlank(""));
        }

        @Test
        @DisplayName("纯空格应返回 true")
        void shouldReturnTrueForWhitespace() {
            assertTrue(ValidationSupport.isBlank("   "));
        }

        @Test
        @DisplayName("非空字符串应返回 false")
        void shouldReturnFalseForNonBlank() {
            assertFalse(ValidationSupport.isBlank("hello"));
        }

        @Test
        @DisplayName("带空格的字符串应返回 false")
        void shouldReturnFalseForStringWithSpaces() {
            assertFalse(ValidationSupport.isBlank(" a "));
        }
    }

    // ======== trimToNull ========

    @Nested
    @DisplayName("trimToNull")
    class TrimToNullTests {

        @Test
        @DisplayName("null 输入应返回 null")
        void shouldReturnNullForNull() {
            assertNull(ValidationSupport.trimToNull(null));
        }

        @Test
        @DisplayName("空字符串应返回 null")
        void shouldReturnNullForEmpty() {
            assertNull(ValidationSupport.trimToNull(""));
        }

        @Test
        @DisplayName("纯空格应返回 null")
        void shouldReturnNullForWhitespace() {
            assertNull(ValidationSupport.trimToNull("   "));
        }

        @Test
        @DisplayName("正常字符串应返回 trim 后的值")
        void shouldReturnTrimmedValue() {
            assertEquals("hello", ValidationSupport.trimToNull("  hello  "));
        }

        @Test
        @DisplayName("无需 trim 的字符串应原样返回")
        void shouldReturnAsIsWhenNoTrimNeeded() {
            assertEquals("test", ValidationSupport.trimToNull("test"));
        }
    }
}
