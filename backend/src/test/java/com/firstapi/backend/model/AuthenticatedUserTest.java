// 修复点: 无（纯新增测试）
// 测试覆盖点: AuthenticatedUser isAdmin 方法 + 大小写不敏感判断 + null role 边界
package com.firstapi.backend.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticatedUserTest {

    @Test
    @DisplayName("role=ADMIN 时 isAdmin 应返回 true")
    void isAdminWithUpperCase() {
        AuthenticatedUser user = new AuthenticatedUser(1L, "admin", "管理员", "a@t.com", "ADMIN");
        assertTrue(user.isAdmin());
    }

    @Test
    @DisplayName("role=admin 小写时 isAdmin 应返回 true（大小写不敏感）")
    void isAdminWithLowerCase() {
        AuthenticatedUser user = new AuthenticatedUser(1L, "admin", "管理员", "a@t.com", "admin");
        assertTrue(user.isAdmin());
    }

    @Test
    @DisplayName("role=Admin 混合大小写时 isAdmin 应返回 true")
    void isAdminWithMixedCase() {
        AuthenticatedUser user = new AuthenticatedUser(1L, "admin", "管理员", "a@t.com", "Admin");
        assertTrue(user.isAdmin());
    }

    @Test
    @DisplayName("role=USER 时 isAdmin 应返回 false")
    void isNotAdmin() {
        AuthenticatedUser user = new AuthenticatedUser(1L, "member", "用户", "m@t.com", "USER");
        assertFalse(user.isAdmin());
    }

    @Test
    @DisplayName("role=null 时 isAdmin 应返回 false（不抛异常）")
    void isAdminWithNullRole() {
        AuthenticatedUser user = new AuthenticatedUser(1L, "x", "x", "x@t.com", null);
        assertFalse(user.isAdmin());
    }

    @Test
    @DisplayName("无参构造器应正常工作")
    void defaultConstructor() {
        AuthenticatedUser user = new AuthenticatedUser();
        assertNull(user.getId());
        assertNull(user.getUsername());
        assertFalse(user.isAdmin());
    }

    @Test
    @DisplayName("getter/setter 应正常存取所有字段")
    void getterSetterRoundTrip() {
        AuthenticatedUser user = new AuthenticatedUser();
        user.setId(99L);
        user.setUsername("testuser");
        user.setDisplayName("显示名");
        user.setEmail("test@example.com");
        user.setRole("USER");

        assertEquals(99L, user.getId());
        assertEquals("testuser", user.getUsername());
        assertEquals("显示名", user.getDisplayName());
        assertEquals("test@example.com", user.getEmail());
        assertEquals("USER", user.getRole());
    }
}
