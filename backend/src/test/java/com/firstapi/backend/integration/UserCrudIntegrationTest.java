// 修复点: javax→jakarta, fasterxml→tools.jackson, 移除@AutoConfigureMockMvc(Spring Boot 4已移除)
// 测试覆盖点: User CRUD 完整集成测试 - 创建/读取/更新/删除/搜索 端到端HTTP验证
//   覆盖: 数据库持久化、JSON序列化、HTTP状态码、数据一致性
package com.firstapi.backend.integration;

import com.firstapi.backend.config.AuthFilter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
class UserCrudIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private AuthFilter authFilter;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Cookie adminCookie;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(authFilter)
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"AdminPass123!\"}"))
                .andReturn();
        adminCookie = loginResult.getResponse().getCookie("FIRSTAPI_SESSION");
    }

    @Test
    @DisplayName("完整 CRUD 流程: 创建 → 查询 → 更新 → 搜索 → 删除")
    void fullCrudLifecycle() throws Exception {
        // 1. CREATE
        MvcResult createResult = mockMvc.perform(post("/api/admin/users")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"crud@test.com\",\"username\":\"cruduser\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("crud@test.com"))
                .andExpect(jsonPath("$.data.username").value("cruduser"))
                .andExpect(jsonPath("$.data.balance").value("¥0.00"))
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long userId = created.get("data").get("id").asLong();

        // 2. GET by ID
        mockMvc.perform(get("/api/admin/users/" + userId)
                .cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("crud@test.com"));

        // 3. UPDATE
        mockMvc.perform(put("/api/admin/users/" + userId)
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"updateduser\",\"balance\":\"¥100.00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("updateduser"))
                .andExpect(jsonPath("$.data.balance").value("¥100.00"));

        // 4. SEARCH
        mockMvc.perform(get("/api/admin/users?keyword=updated")
                .cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").isNumber());

        // 5. DELETE
        mockMvc.perform(delete("/api/admin/users/" + userId)
                .cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 6. Verify deleted
        mockMvc.perform(get("/api/admin/users/" + userId)
                .cookie(adminCookie))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("创建用户时无效邮箱应返回 400")
    void createWithInvalidEmailReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/users")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\",\"username\":\"testuser\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("获取不存在的用户应返回 404")
    void getNonExistentUserReturns404() throws Exception {
        mockMvc.perform(get("/api/admin/users/999999")
                .cookie(adminCookie))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("列表接口应返回 PageResponse 格式")
    void listReturnsPageResponse() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                .cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.total").isNumber());
    }
}
