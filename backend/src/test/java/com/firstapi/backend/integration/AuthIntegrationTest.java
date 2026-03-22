// 修复点: javax→jakarta, fasterxml→tools.jackson, 移除@AutoConfigureMockMvc(Spring Boot 4已移除)
// 测试覆盖点: Auth完整流程集成测试 - 登录/注册/会话/登出 端到端HTTP验证
//   覆盖: Cookie设置、401状态码、JSON响应格式、角色分配
package com.firstapi.backend.integration;

import com.firstapi.backend.config.AuthFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import jakarta.servlet.http.Cookie;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class AuthIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private AuthFilter authFilter;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(authFilter)
                .build();
    }

    // ======== Login ========

    @Nested
    @DisplayName("POST /api/auth/login")
    class LoginTests {

        @Test
        @DisplayName("admin 登录应成功并返回 session cookie")
        void adminLoginSuccess() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"admin\",\"password\":\"AdminPass123!\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.username").value("admin"))
                    .andExpect(jsonPath("$.data.role").value("ADMIN"))
                    .andExpect(cookie().exists("FIRSTAPI_SESSION"))
                    .andReturn();
        }

        @Test
        @DisplayName("member 用户登录应成功")
        void memberLoginSuccess() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"member\",\"password\":\"UserPass123!\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.role").value("USER"));
        }

        @Test
        @DisplayName("错误密码应返回 401")
        void wrongPasswordReturns401() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"admin\",\"password\":\"WrongPassword1\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("空请求体应返回 400")
        void emptyBodyReturns400() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("不存在的用户应返回 401")
        void nonExistentUserReturns401() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"nobody\",\"password\":\"SomePassword1!\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ======== Session ========

    @Nested
    @DisplayName("GET /api/auth/session")
    class SessionTests {

        @Test
        @DisplayName("无 cookie 请求 session 应返回 401")
        void noCookieReturns401() throws Exception {
            mockMvc.perform(get("/api/auth/session"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("有效 cookie 应返回用户信息")
        void validCookieReturnsSession() throws Exception {
            // 先登录获取 cookie
            MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"admin\",\"password\":\"AdminPass123!\"}"))
                    .andReturn();

            Cookie sessionCookie = loginResult.getResponse().getCookie("FIRSTAPI_SESSION");

            // 用 cookie 请求 session
            mockMvc.perform(get("/api/auth/session")
                    .cookie(sessionCookie))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.username").value("admin"));
        }
    }

    // ======== Register ========

    @Nested
    @DisplayName("POST /api/auth/register")
    class RegisterTests {

        @Test
        @DisplayName("合法注册应成功并返回 session cookie")
        void registerSuccess() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"newuser123\",\"password\":\"NewUserPass1!\",\"confirmPassword\":\"NewUserPass1!\",\"displayName\":\"新用户\",\"email\":\"new123@example.com\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.role").value("USER"))
                    .andExpect(cookie().exists("FIRSTAPI_SESSION"));
        }

        @Test
        @DisplayName("密码不一致应返回 400")
        void mismatchedPasswordsReturns400() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"user2\",\"password\":\"Password12345\",\"confirmPassword\":\"Different12345\",\"displayName\":\"测试\",\"email\":\"u2@test.com\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("短密码应返回 400")
        void shortPasswordReturns400() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"user3\",\"password\":\"Short1!\",\"confirmPassword\":\"Short1!\",\"displayName\":\"测试\",\"email\":\"u3@test.com\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ======== Logout ========

    @Nested
    @DisplayName("POST /api/auth/logout")
    class LogoutTests {

        @Test
        @DisplayName("登出后 session 应失效")
        void logoutInvalidatesSession() throws Exception {
            // 登录
            MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"admin\",\"password\":\"AdminPass123!\"}"))
                    .andReturn();
            Cookie sessionCookie = loginResult.getResponse().getCookie("FIRSTAPI_SESSION");

            // 登出
            mockMvc.perform(post("/api/auth/logout")
                    .cookie(sessionCookie))
                    .andExpect(status().isOk());

            // 验证 session 已失效
            mockMvc.perform(get("/api/auth/session")
                    .cookie(sessionCookie))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ======== Admin Endpoint Access ========

    @Nested
    @DisplayName("Admin endpoint access control")
    class AdminAccessTests {

        @Test
        @DisplayName("未认证请求 admin API 应返回 401")
        void unauthenticatedAdminAccessReturns401() throws Exception {
            mockMvc.perform(get("/api/admin/users"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("admin 用户应能访问 admin API")
        void adminCanAccessAdminEndpoints() throws Exception {
            MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"admin\",\"password\":\"AdminPass123!\"}"))
                    .andReturn();
            Cookie sessionCookie = loginResult.getResponse().getCookie("FIRSTAPI_SESSION");

            mockMvc.perform(get("/api/admin/users")
                    .cookie(sessionCookie))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("member 用户访问 admin API 应返回 403")
        void memberCannotAccessAdminEndpoints() throws Exception {
            MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"member\",\"password\":\"UserPass123!\"}"))
                    .andReturn();
            Cookie sessionCookie = loginResult.getResponse().getCookie("FIRSTAPI_SESSION");

            mockMvc.perform(get("/api/admin/users")
                    .cookie(sessionCookie))
                    .andExpect(status().isForbidden());
        }
    }
}
