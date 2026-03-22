// 修复点: 无（纯新增测试）
// 测试覆盖点: CurrentSessionHolder ThreadLocal 生命周期 + require() 未登录抛异常 + clear 释放
package com.firstapi.backend.common;

import com.firstapi.backend.model.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class CurrentSessionHolderTest {

    @AfterEach
    void cleanup() {
        CurrentSessionHolder.clear();
    }

    @Test
    @DisplayName("set 后 get 应返回同一用户对象")
    void shouldReturnSetUser() {
        AuthenticatedUser user = new AuthenticatedUser(1L, "admin", "管理员", "admin@test.com", "ADMIN");
        CurrentSessionHolder.set(user);
        assertSame(user, CurrentSessionHolder.get());
    }

    @Test
    @DisplayName("未 set 时 get 应返回 null")
    void shouldReturnNullWhenNotSet() {
        assertNull(CurrentSessionHolder.get());
    }

    @Test
    @DisplayName("未 set 时 require 应抛出 UNAUTHORIZED ResponseStatusException")
    void requireShouldThrowWhenNoSession() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                CurrentSessionHolder::require
        );
        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("set 后 require 应正常返回用户")
    void requireShouldReturnUserWhenSet() {
        AuthenticatedUser user = new AuthenticatedUser(2L, "member", "用户", "u@test.com", "USER");
        CurrentSessionHolder.set(user);
        AuthenticatedUser result = CurrentSessionHolder.require();
        assertEquals("member", result.getUsername());
    }

    @Test
    @DisplayName("clear 后 get 应返回 null")
    void shouldReturnNullAfterClear() {
        CurrentSessionHolder.set(new AuthenticatedUser(1L, "a", "a", "a@t.com", "ADMIN"));
        CurrentSessionHolder.clear();
        assertNull(CurrentSessionHolder.get());
    }

    @Test
    @DisplayName("覆盖 set 应返回最新用户")
    void shouldReturnLatestUser() {
        AuthenticatedUser user1 = new AuthenticatedUser(1L, "u1", "u1", "u1@t.com", "ADMIN");
        AuthenticatedUser user2 = new AuthenticatedUser(2L, "u2", "u2", "u2@t.com", "USER");
        CurrentSessionHolder.set(user1);
        CurrentSessionHolder.set(user2);
        assertEquals("u2", CurrentSessionHolder.get().getUsername());
    }
}
