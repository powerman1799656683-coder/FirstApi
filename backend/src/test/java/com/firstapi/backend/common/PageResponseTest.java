// 修复点: 无（纯新增测试）
// 测试覆盖点: PageResponse 构造 + items/total 一致性 + 空列表边界
package com.firstapi.backend.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PageResponseTest {

    @Test
    @DisplayName("total 应等于 items.size()")
    void totalShouldMatchItemsSize() {
        List<String> items = Arrays.asList("a", "b", "c");
        PageResponse<String> page = new PageResponse<String>(items);
        assertEquals(3, page.getTotal());
        assertEquals(3, page.getItems().size());
    }

    @Test
    @DisplayName("空列表应返回 total=0")
    void emptyListShouldHaveZeroTotal() {
        PageResponse<String> page = new PageResponse<String>(Collections.<String>emptyList());
        assertEquals(0, page.getTotal());
        assertTrue(page.getItems().isEmpty());
    }

    @Test
    @DisplayName("单元素列表应返回 total=1")
    void singleItemList() {
        PageResponse<Integer> page = new PageResponse<Integer>(Collections.singletonList(42));
        assertEquals(1, page.getTotal());
        assertEquals(42, page.getItems().get(0));
    }
}
