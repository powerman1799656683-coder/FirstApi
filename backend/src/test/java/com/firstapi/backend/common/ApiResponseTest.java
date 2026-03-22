// 修复点: 无（纯新增测试）
// 测试覆盖点: ApiResponse ok/fail 工厂方法 + 泛型参数 + null data 边界
package com.firstapi.backend.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ApiResponseTest {

    @Test
    @DisplayName("ok(data) 应返回 success=true, message=ok")
    void okWithData() {
        ApiResponse<String> resp = ApiResponse.ok("hello");
        assertTrue(resp.isSuccess());
        assertEquals("ok", resp.getMessage());
        assertEquals("hello", resp.getData());
    }

    @Test
    @DisplayName("ok(message, data) 应使用自定义 message")
    void okWithCustomMessage() {
        ApiResponse<Integer> resp = ApiResponse.ok("创建成功", 42);
        assertTrue(resp.isSuccess());
        assertEquals("创建成功", resp.getMessage());
        assertEquals(42, resp.getData());
    }

    @Test
    @DisplayName("fail(message, data) 应返回 success=false")
    void failResponse() {
        ApiResponse<Void> resp = ApiResponse.fail("操作失败", null);
        assertFalse(resp.isSuccess());
        assertEquals("操作失败", resp.getMessage());
        assertNull(resp.getData());
    }

    @Test
    @DisplayName("ok 应支持 null data")
    void okWithNullData() {
        ApiResponse<Object> resp = ApiResponse.ok(null);
        assertTrue(resp.isSuccess());
        assertNull(resp.getData());
    }

    @Test
    @DisplayName("ok 应支持 List 泛型")
    void okWithListData() {
        List<String> list = Arrays.asList("a", "b", "c");
        ApiResponse<List<String>> resp = ApiResponse.ok(list);
        assertTrue(resp.isSuccess());
        assertEquals(3, resp.getData().size());
    }

    @Test
    @DisplayName("fail 可以携带非 null data")
    void failWithData() {
        ApiResponse<String> resp = ApiResponse.fail("错误", "detail");
        assertFalse(resp.isSuccess());
        assertEquals("detail", resp.getData());
    }
}
