// 修复点: 无（纯新增测试）
// 测试覆盖点: UserService CRUD + 邮箱校验 + keyword 过滤 + NOT_FOUND 边界 + null 字段更新逻辑
package com.firstapi.backend.service;

import com.firstapi.backend.common.PageResponse;
import com.firstapi.backend.model.UserItem;
import com.firstapi.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository repository;

    @InjectMocks
    private UserService userService;

    private UserItem sampleUser() {
        UserItem item = new UserItem();
        item.setId(1L);
        item.setEmail("test@example.com");
        item.setUsername("testuser");
        item.setBalance("¥0.00");
        item.setGroup("默认组");
        item.setRole("用户");
        item.setStatus("正常");
        item.setTime("2026/03/16");
        return item;
    }

    // ======== list ========

    @Nested
    @DisplayName("list")
    class ListTests {

        @Test
        @DisplayName("无关键词时应返回全部用户")
        void shouldReturnAllWithoutKeyword() {
            when(repository.findAll()).thenReturn(Arrays.asList(sampleUser()));
            PageResponse<UserItem> result = userService.list(null);
            assertEquals(1, result.getTotal());
        }

        @Test
        @DisplayName("关键词应过滤不匹配的用户（大小写不敏感）")
        void shouldFilterByKeyword() {
            UserItem u1 = sampleUser();
            u1.setEmail("alice@example.com");
            UserItem u2 = sampleUser();
            u2.setId(2L);
            u2.setEmail("bob@test.com");
            when(repository.findAll()).thenReturn(Arrays.asList(u1, u2));

            PageResponse<UserItem> result = userService.list("alice");
            assertEquals(1, result.getTotal());
        }

        @Test
        @DisplayName("空关键词应视为无过滤")
        void shouldTreatBlankKeywordAsNoFilter() {
            when(repository.findAll()).thenReturn(Arrays.asList(sampleUser()));
            PageResponse<UserItem> result = userService.list("   ");
            assertEquals(1, result.getTotal());
        }

        @Test
        @DisplayName("空列表应返回 total=0")
        void shouldReturnEmptyList() {
            when(repository.findAll()).thenReturn(Collections.<UserItem>emptyList());
            PageResponse<UserItem> result = userService.list(null);
            assertEquals(0, result.getTotal());
        }

        @Test
        @DisplayName("按 ID 数字搜索应匹配")
        void shouldFilterByIdAsString() {
            UserItem u = sampleUser();
            u.setId(42L);
            when(repository.findAll()).thenReturn(Arrays.asList(u));

            PageResponse<UserItem> result = userService.list("42");
            assertEquals(1, result.getTotal());
        }
    }

    // ======== get ========

    @Nested
    @DisplayName("get")
    class GetTests {

        @Test
        @DisplayName("存在的 ID 应返回用户")
        void shouldReturnExistingUser() {
            when(repository.findById(1L)).thenReturn(sampleUser());
            UserItem result = userService.get(1L);
            assertEquals("testuser", result.getUsername());
        }

        @Test
        @DisplayName("不存在的 ID 应抛出 NOT_FOUND")
        void shouldThrowNotFound() {
            when(repository.findById(999L)).thenReturn(null);
            ResponseStatusException ex = assertThrows(
                    ResponseStatusException.class,
                    () -> userService.get(999L)
            );
            assertEquals(404, ex.getStatusCode().value());
        }
    }

    // ======== create ========

    @Nested
    @DisplayName("create")
    class CreateTests {

        @Test
        @DisplayName("合法请求应创建用户")
        void shouldCreateUser() {
            UserItem.Request req = new UserItem.Request();
            req.setEmail("new@example.com");
            req.setUsername("newuser");
            when(repository.save(any(UserItem.class))).thenAnswer(inv -> {
                UserItem item = inv.getArgument(0);
                item.setId(10L);
                return item;
            });

            UserItem result = userService.create(req);
            assertEquals("new@example.com", result.getEmail());
            assertEquals("newuser", result.getUsername());
            assertEquals("¥0.00", result.getBalance());  // 默认值
            assertEquals("默认组", result.getGroup());    // 默认值
        }

        @Test
        @DisplayName("无效邮箱格式应抛出异常")
        void shouldRejectInvalidEmail() {
            UserItem.Request req = new UserItem.Request();
            req.setEmail("not-an-email");
            req.setUsername("user");
            assertThrows(IllegalArgumentException.class,
                    () -> userService.create(req));
        }

        @Test
        @DisplayName("空邮箱应抛出异常")
        void shouldRejectBlankEmail() {
            UserItem.Request req = new UserItem.Request();
            req.setEmail("");
            req.setUsername("user");
            assertThrows(IllegalArgumentException.class,
                    () -> userService.create(req));
        }

        @Test
        @DisplayName("空用户名应抛出异常")
        void shouldRejectBlankUsername() {
            UserItem.Request req = new UserItem.Request();
            req.setEmail("test@example.com");
            req.setUsername("");
            assertThrows(IllegalArgumentException.class,
                    () -> userService.create(req));
        }

        @Test
        @DisplayName("邮箱包含空格应被拒绝")
        void shouldRejectEmailWithSpaces() {
            UserItem.Request req = new UserItem.Request();
            req.setEmail("test @example.com");
            req.setUsername("user");
            assertThrows(IllegalArgumentException.class,
                    () -> userService.create(req));
        }
    }

    // ======== update ========

    @Nested
    @DisplayName("update")
    class UpdateTests {

        @Test
        @DisplayName("部分更新只修改提供的字段")
        void shouldPartiallyUpdate() {
            UserItem existing = sampleUser();
            when(repository.findById(1L)).thenReturn(existing);
            when(repository.update(eq(1L), any(UserItem.class))).thenAnswer(inv -> inv.getArgument(1));

            UserItem.Request req = new UserItem.Request();
            req.setUsername("updated");

            UserItem result = userService.update(1L, req);
            assertEquals("updated", result.getUsername());
            assertEquals("test@example.com", result.getEmail()); // 未修改
        }

        @Test
        @DisplayName("更新不存在的用户应抛出 NOT_FOUND")
        void shouldThrowNotFoundOnUpdate() {
            when(repository.findById(999L)).thenReturn(null);
            assertThrows(ResponseStatusException.class,
                    () -> userService.update(999L, new UserItem.Request()));
        }

        @Test
        @DisplayName("更新邮箱为无效格式应被拒绝")
        void shouldRejectInvalidEmailOnUpdate() {
            when(repository.findById(1L)).thenReturn(sampleUser());
            UserItem.Request req = new UserItem.Request();
            req.setEmail("bad-email");
            assertThrows(IllegalArgumentException.class,
                    () -> userService.update(1L, req));
        }
    }

    // ======== delete ========

    @Nested
    @DisplayName("delete")
    class DeleteTests {

        @Test
        @DisplayName("删除存在的用户应成功调用 repository")
        void shouldDeleteExisting() {
            when(repository.findById(1L)).thenReturn(sampleUser());
            userService.delete(1L);
            verify(repository).deleteById(1L);
        }

        @Test
        @DisplayName("删除不存在的用户应抛出 NOT_FOUND")
        void shouldThrowNotFoundOnDelete() {
            when(repository.findById(999L)).thenReturn(null);
            assertThrows(ResponseStatusException.class,
                    () -> userService.delete(999L));
        }
    }
}
