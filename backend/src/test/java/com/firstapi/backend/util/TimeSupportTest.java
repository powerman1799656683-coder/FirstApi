// 修复点: 无（纯新增测试）
// 测试覆盖点: TimeSupport today/nowDateTime/plusMonths + 边界条件（null日期/空日期/跨年/月末）
package com.firstapi.backend.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TimeSupportTest {

    // ======== today ========

    @Nested
    @DisplayName("today")
    class TodayTests {

        @Test
        @DisplayName("today() 应返回 yyyy/MM/dd 格式")
        void shouldReturnFormattedDate() {
            String result = TimeSupport.today();
            assertNotNull(result);
            assertTrue(result.matches("\\d{4}/\\d{2}/\\d{2}"),
                    "格式应为 yyyy/MM/dd, 实际: " + result);
        }
    }

    // ======== nowDateTime ========

    @Nested
    @DisplayName("nowDateTime")
    class NowDateTimeTests {

        @Test
        @DisplayName("nowDateTime() 应返回 yyyy/MM/dd HH:mm:ss 格式")
        void shouldReturnFormattedDateTime() {
            String result = TimeSupport.nowDateTime();
            assertNotNull(result);
            assertTrue(result.matches("\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}"),
                    "格式应为 yyyy/MM/dd HH:mm:ss, 实际: " + result);
        }
    }

    // ======== plusMonths ========

    @Nested
    @DisplayName("plusMonths")
    class PlusMonthsTests {

        @Test
        @DisplayName("正常日期加1个月")
        void shouldAddOneMonth() {
            String result = TimeSupport.plusMonths("2026/01/15", 1);
            assertEquals("2026/02/15", result);
        }

        @Test
        @DisplayName("12月加1个月应跨年")
        void shouldCrossYear() {
            String result = TimeSupport.plusMonths("2025/12/15", 1);
            assertEquals("2026/01/15", result);
        }

        @Test
        @DisplayName("1月31日加1个月应自动调整为2月28日（平年）")
        void shouldAdjustToEndOfFebruary() {
            String result = TimeSupport.plusMonths("2025/01/31", 1);
            assertEquals("2025/02/28", result);
        }

        @Test
        @DisplayName("加12个月等于加1年")
        void shouldAddTwelveMonths() {
            String result = TimeSupport.plusMonths("2025/06/15", 12);
            assertEquals("2026/06/15", result);
        }

        @Test
        @DisplayName("null 日期应使用当天作为基准")
        void shouldUseCurrentDateForNull() {
            String result = TimeSupport.plusMonths(null, 1);
            assertNotNull(result);
            assertTrue(result.matches("\\d{4}/\\d{2}/\\d{2}"));
        }

        @Test
        @DisplayName("空字符串日期应使用当天作为基准")
        void shouldUseCurrentDateForEmpty() {
            String result = TimeSupport.plusMonths("", 1);
            assertNotNull(result);
            assertTrue(result.matches("\\d{4}/\\d{2}/\\d{2}"));
        }

        @Test
        @DisplayName("加0个月应返回原日期")
        void shouldReturnSameDateForZeroMonths() {
            String result = TimeSupport.plusMonths("2026/03/16", 0);
            assertEquals("2026/03/16", result);
        }

        @Test
        @DisplayName("减去月份（负数）应正常工作")
        void shouldSubtractMonths() {
            String result = TimeSupport.plusMonths("2026/03/16", -1);
            assertEquals("2026/02/16", result);
        }
    }
}
