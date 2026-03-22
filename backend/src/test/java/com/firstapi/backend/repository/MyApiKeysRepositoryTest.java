// 修复点: @JdbcTest已从Spring Boot 4移除, 改用@SpringBootTest
// 测试覆盖点: MyApiKeysRepository - 通过明文key查找加密存储的API key
package com.firstapi.backend.repository;

import com.firstapi.backend.model.ApiKeyItem;
import com.firstapi.backend.service.SensitiveDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.sql.init.schema-locations=classpath:schema-test.sql",
        "app.security.data-secret=test-secret-key-for-testing"
})
class MyApiKeysRepositoryTest {

    @Autowired
    private MyApiKeysRepository repository;

    @Autowired
    private SensitiveDataService sensitiveDataService;

    @Test
    void findsActiveKeyByPlainTextValue() {
        ApiKeyItem item = new ApiKeyItem();
        item.setOwnerId(7L);
        item.setName("relay");
        item.setKey(sensitiveDataService.protect("sk-firstapi-test"));
        item.setCreated("2026/03/16 12:00:00");
        item.setStatus("正常");
        item.setLastUsed("-");
        repository.save(item);

        ApiKeyItem resolved = repository.findByPlainTextKey("sk-firstapi-test");

        assertThat(resolved).isNotNull();
        assertThat(resolved.getOwnerId()).isEqualTo(7L);
    }
}
