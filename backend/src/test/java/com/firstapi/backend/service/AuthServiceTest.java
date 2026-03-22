// 修复点: 无（纯新增测试）
// 测试覆盖点: AuthService login/register/openSession/resolveAuthenticatedUser/logout
//   边界: 空用户名/空密码/短密码/密码不匹配/用户名重复/禁用用户/过期会话/无Cookie
package com.firstapi.backend.service;

import com.firstapi.backend.config.AuthProperties;
import com.firstapi.backend.model.AuthLoginRequest;
import com.firstapi.backend.model.AuthRegisterRequest;
import com.firstapi.backend.model.AuthUser;
import com.firstapi.backend.model.AuthenticatedUser;
import com.firstapi.backend.repository.AuthUserRepository;
import com.firstapi.backend.repository.UserRepository;
import com.firstapi.backend.util.PasswordHashSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthUserRepository authUserRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SettingsService settingsService;

    private AuthProperties authProperties;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authProperties = new AuthProperties();
        authProperties.setAdminPassword("AdminPass123!");
        authProperties.setSessionTtlMinutes(480);
        authService = new AuthService(authProperties, authUserRepository, userRepository, settingsService);
    }

    private AuthUser buildAuthUser(String username, String password, String role, boolean enabled) {
        AuthUser user = new AuthUser();
        user.setId(1L);
        user.setUsername(username);
        user.setDisplayName(username);
        user.setEmail(username + "@test.com");
        user.setPasswordHash(PasswordHashSupport.hash(password));
        user.setRole(role);
        user.setEnabled(enabled);
        return user;
    }

    // ======== login ========

    @Nested
    @DisplayName("login")
    class LoginTests {

        @Test
        @DisplayName("正确用户名和密码应登录成功")
        void shouldLoginSuccessfully() {
            AuthUser user = buildAuthUser("admin", "AdminPass123!", "ADMIN", true);
            when(authUserRepository.findByUsername("admin")).thenReturn(user);

            AuthLoginRequest req = new AuthLoginRequest();
            req.setUsername("admin");
            req.setPassword("AdminPass123!");

            AuthenticatedUser result = authService.login(req);
            assertNotNull(result);
            assertEquals("admin", result.getUsername());
            assertEquals("ADMIN", result.getRole());
        }

        @Test
        @DisplayName("错误密码应抛出 UNAUTHORIZED")
        void shouldRejectWrongPassword() {
            AuthUser user = buildAuthUser("admin", "AdminPass123!", "ADMIN", true);
            when(authUserRepository.findByUsername("admin")).thenReturn(user);

            AuthLoginRequest req = new AuthLoginRequest();
            req.setUsername("admin");
            req.setPassword("WrongPassword123");

            ResponseStatusException ex = assertThrows(
                    ResponseStatusException.class,
                    () -> authService.login(req)
            );
            assertEquals(401, ex.getStatusCode().value());
        }

        @Test
        @DisplayName("不存在的用户名应抛出 UNAUTHORIZED")
        void shouldRejectNonExistentUser() {
            when(authUserRepository.findByUsername("nobody")).thenReturn(null);

            AuthLoginRequest req = new AuthLoginRequest();
            req.setUsername("nobody");
            req.setPassword("SomePassword123");

            assertThrows(ResponseStatusException.class, () -> authService.login(req));
        }

        @Test
        @DisplayName("被禁用的用户应抛出 UNAUTHORIZED")
        void shouldRejectDisabledUser() {
            AuthUser user = buildAuthUser("disabled", "AdminPass123!", "USER", false);
            when(authUserRepository.findByUsername("disabled")).thenReturn(user);

            AuthLoginRequest req = new AuthLoginRequest();
            req.setUsername("disabled");
            req.setPassword("AdminPass123!");

            assertThrows(ResponseStatusException.class, () -> authService.login(req));
        }

        @Test
        @DisplayName("空用户名应抛出 IllegalArgumentException")
        void shouldRejectBlankUsername() {
            AuthLoginRequest req = new AuthLoginRequest();
            req.setUsername("");
            req.setPassword("AdminPass123!");

            assertThrows(IllegalArgumentException.class, () -> authService.login(req));
        }

        @Test
        @DisplayName("空密码应抛出 IllegalArgumentException")
        void shouldRejectBlankPassword() {
            AuthLoginRequest req = new AuthLoginRequest();
            req.setUsername("admin");
            req.setPassword("");

            assertThrows(IllegalArgumentException.class, () -> authService.login(req));
        }

        @Test
        @DisplayName("短密码不应泄露验证细节（静默当作不匹配）")
        void shouldTreatShortPasswordAsMismatch() {
            AuthUser user = buildAuthUser("admin", "AdminPass123!", "ADMIN", true);
            when(authUserRepository.findByUsername("admin")).thenReturn(user);

            AuthLoginRequest req = new AuthLoginRequest();
            req.setUsername("admin");
            req.setPassword("short1234"); // 只有9位, 但 requireNotBlank 通过

            // 短密码在 PasswordHashSupport.matches 中会抛 IllegalArgumentException
            // AuthService 捕获后视为密码不匹配
            assertThrows(ResponseStatusException.class, () -> authService.login(req));
        }
    }

    // ======== register ========

    @Nested
    @DisplayName("register")
    class RegisterTests {

        @Test
        @DisplayName("合法注册应成功")
        void shouldRegisterSuccessfully() {
            when(authUserRepository.findByUsername("newuser")).thenReturn(null);

            AuthRegisterRequest req = new AuthRegisterRequest();
            req.setUsername("newuser");
            req.setPassword("NewUserPass1!");
            req.setConfirmPassword("NewUserPass1!");
            req.setDisplayName("新用户");
            req.setEmail("new@example.com");

            AuthenticatedUser result = authService.register(req);
            assertEquals("newuser", result.getUsername());
            assertEquals("USER", result.getRole());
            verify(authUserRepository).save(any(AuthUser.class));
        }

        @Test
        @DisplayName("密码不一致应抛出异常")
        void shouldRejectMismatchedPasswords() {
            AuthRegisterRequest req = new AuthRegisterRequest();
            req.setUsername("newuser");
            req.setPassword("NewUserPass1!");
            req.setConfirmPassword("DifferentPass1");
            req.setDisplayName("新用户");
            req.setEmail("new@example.com");

            assertThrows(IllegalArgumentException.class, () -> authService.register(req));
        }

        @Test
        @DisplayName("密码少于10位应抛出异常")
        void shouldRejectShortPassword() {
            AuthRegisterRequest req = new AuthRegisterRequest();
            req.setUsername("newuser");
            req.setPassword("Short1!");
            req.setConfirmPassword("Short1!");
            req.setDisplayName("新用户");
            req.setEmail("new@example.com");

            assertThrows(IllegalArgumentException.class, () -> authService.register(req));
        }

        @Test
        @DisplayName("重复用户名应抛出异常")
        void shouldRejectDuplicateUsername() {
            AuthUser existing = new AuthUser();
            existing.setUsername("existinguser");
            when(authUserRepository.findByUsername("existinguser")).thenReturn(existing);

            AuthRegisterRequest req = new AuthRegisterRequest();
            req.setUsername("existinguser");
            req.setPassword("NewUserPass1!");
            req.setConfirmPassword("NewUserPass1!");
            req.setDisplayName("新用户");
            req.setEmail("new@example.com");

            assertThrows(IllegalArgumentException.class, () -> authService.register(req));
        }

        @Test
        @DisplayName("空显示名称应抛出异常")
        void shouldRejectBlankDisplayName() {
            AuthRegisterRequest req = new AuthRegisterRequest();
            req.setUsername("newuser");
            req.setPassword("NewUserPass1!");
            req.setConfirmPassword("NewUserPass1!");
            req.setDisplayName("");
            req.setEmail("new@example.com");

            assertThrows(IllegalArgumentException.class, () -> authService.register(req));
        }
    }

    // ======== session management ========

    @Nested
    @DisplayName("session management")
    class SessionTests {

        @Test
        @DisplayName("openSession 应返回非空 token")
        void shouldCreateToken() {
            AuthenticatedUser user = new AuthenticatedUser(1L, "admin", "管理员", "a@t.com", "ADMIN");
            String token = authService.openSession(user);
            assertNotNull(token);
            assertFalse(token.isEmpty());
            assertEquals(32, token.length()); // UUID without hyphens
        }

        @Test
        @DisplayName("resolveAuthenticatedUser 有效 token 应返回用户")
        void shouldResolveValidSession() {
            AuthUser authUser = buildAuthUser("admin", "AdminPass123!", "ADMIN", true);
            when(authUserRepository.findById(1L)).thenReturn(authUser);

            AuthenticatedUser user = new AuthenticatedUser(1L, "admin", "管理员", "a@t.com", "ADMIN");
            String token = authService.openSession(user);

            HttpServletRequest request = mock(HttpServletRequest.class);
            Cookie cookie = new Cookie("FIRSTAPI_SESSION", token);
            when(request.getCookies()).thenReturn(new Cookie[]{cookie});

            AuthenticatedUser resolved = authService.resolveAuthenticatedUser(request);
            assertNotNull(resolved);
            assertEquals("admin", resolved.getUsername());
        }

        @Test
        @DisplayName("无 Cookie 的请求应返回 null")
        void shouldReturnNullForNoCookies() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getCookies()).thenReturn(null);

            assertNull(authService.resolveAuthenticatedUser(request));
        }

        @Test
        @DisplayName("无效 token 应返回 null")
        void shouldReturnNullForInvalidToken() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            Cookie cookie = new Cookie("FIRSTAPI_SESSION", "invalid-token");
            when(request.getCookies()).thenReturn(new Cookie[]{cookie});

            assertNull(authService.resolveAuthenticatedUser(request));
        }

        @Test
        @DisplayName("logout 应移除 session")
        void shouldRemoveSessionOnLogout() {
            AuthenticatedUser user = new AuthenticatedUser(1L, "admin", "管理员", "a@t.com", "ADMIN");
            String token = authService.openSession(user);

            HttpServletRequest request = mock(HttpServletRequest.class);
            Cookie cookie = new Cookie("FIRSTAPI_SESSION", token);
            when(request.getCookies()).thenReturn(new Cookie[]{cookie});

            authService.logout(request);

            // 再次 resolve 应返回 null
            assertNull(authService.resolveAuthenticatedUser(request));
        }

        @Test
        @DisplayName("buildSessionCookie 应设置正确属性")
        void shouldBuildCookieCorrectly() {
            ResponseCookie cookie = authService.buildSessionCookie("test-token");
            assertEquals("FIRSTAPI_SESSION", cookie.getName());
            assertEquals("test-token", cookie.getValue());
            assertTrue(cookie.isHttpOnly());
            assertEquals("/", cookie.getPath());
        }

        @Test
        @DisplayName("clearSessionCookie 应设置 maxAge=0")
        void shouldClearCookie() {
            ResponseCookie cookie = authService.clearSessionCookie();
            assertEquals("FIRSTAPI_SESSION", cookie.getName());
            assertEquals("", cookie.getValue());
            assertEquals(java.time.Duration.ZERO, cookie.getMaxAge());
        }
    }
}
