// 修复点: @JdbcTest已从Spring Boot 4移除, 改用@SpringBootTest
// 测试覆盖点: RelayApiKeyAuthService - API key认证、禁用key拒绝、lastUsed更新
package com.firstapi.backend.service;

import com.firstapi.backend.model.ApiKeyItem;
import com.firstapi.backend.model.RelayException;
import com.firstapi.backend.repository.MyApiKeysRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.sql.init.schema-locations=classpath:schema-test.sql",
        "app.security.data-secret=test-secret-key-for-testing"
})
class RelayApiKeyAuthServiceTest {

    @Autowired
    private RelayApiKeyAuthService service;

    @Autowired
    private MyApiKeysRepository repository;

    @Autowired
    private SensitiveDataService sensitiveDataService;

    @Test
    void rejectsDisabledKey() {
        ApiKeyItem item = new ApiKeyItem();
        item.setOwnerId(9L);
        item.setName("disabled");
        item.setKey(sensitiveDataService.protect("sk-firstapi-disabled"));
        item.setCreated("2026/03/16 12:10:00");
        item.setStatus("禁用");
        item.setLastUsed("-");
        repository.save(item);

        assertThatThrownBy(() -> service.authenticate("Bearer sk-firstapi-disabled"))
                .isInstanceOf(RelayException.class)
                .hasMessageContaining("API key");
    }

    @Test
    void authenticatesActiveKeyAndTouchesLastUsed() {
        ApiKeyItem item = new ApiKeyItem();
        item.setOwnerId(10L);
        item.setName("active");
        item.setKey(sensitiveDataService.protect("sk-firstapi-active"));
        item.setCreated("2026/03/16 12:11:00");
        item.setStatus("正常");
        item.setLastUsed("-");
        ApiKeyItem saved = repository.save(item);

        ApiKeyItem authenticated = service.authenticate("Bearer sk-firstapi-active");
        ApiKeyItem updated = repository.findByIdAndOwnerId(saved.getId(), saved.getOwnerId());

        assertThat(authenticated.getId()).isEqualTo(saved.getId());
        assertThat(updated.getLastUsed()).isNotEqualTo("-");
    }
}
