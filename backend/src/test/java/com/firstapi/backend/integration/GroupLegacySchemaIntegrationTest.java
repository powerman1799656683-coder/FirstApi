package com.firstapi.backend.integration;

import com.firstapi.backend.config.AuthFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import jakarta.servlet.http.Cookie;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class GroupLegacySchemaIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private AuthFilter authFilter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private Cookie adminCookie;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(authFilter)
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"AdminPass123!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        adminCookie = loginResult.getResponse().getCookie("FIRSTAPI_SESSION");

        // Simulate legacy production schema drift: user_count exists and is required with no default value.
        jdbcTemplate.execute("delete from `groups`");
        if (!hasColumn("groups", "user_count")) {
            jdbcTemplate.execute("alter table `groups` add column `user_count` varchar(64) not null");
        }
    }

    @Test
    @DisplayName("POST /api/admin/groups should still work when legacy user_count column is required")
    void createGroupWithLegacyUserCountColumn() throws Exception {
        mockMvc.perform(post("/api/admin/groups")
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"legacy-group\",\"platform\":\"OpenAI\",\"accountType\":\"ChatGPT Plus\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("legacy-group"));
    }

    private boolean hasColumn(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns where table_name = ? and column_name = ?",
                Integer.class,
                table.toUpperCase(),
                column.toUpperCase()
        );
        return count != null && count > 0;
    }
}
