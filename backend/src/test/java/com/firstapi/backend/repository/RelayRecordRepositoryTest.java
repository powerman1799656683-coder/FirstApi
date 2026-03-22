// 修复点: @JdbcTest已从Spring Boot 4移除, 改用@SpringBootTest
// 测试覆盖点: RelayRecordRepository - 保存和查询relay使用记录
package com.firstapi.backend.repository;

import com.firstapi.backend.model.RelayRecordItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = "spring.sql.init.schema-locations=classpath:schema-test.sql")
class RelayRecordRepositoryTest {

    @Autowired
    private RelayRecordRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetRelayRecords() {
        jdbcTemplate.update("delete from `relay_records`");
    }

    @Test
    void savesUsageRecord() {
        RelayRecordItem item = new RelayRecordItem();
        item.setOwnerId(7L);
        item.setApiKeyId(3L);
        item.setProvider("openai");
        item.setAccountId(2L);
        item.setModel("gpt-4o-mini");
        item.setRequestId("req_123");
        item.setSuccess(true);
        item.setStatusCode(200);
        item.setLatencyMs(512L);
        item.setTotalTokens(42);

        RelayRecordItem saved = repository.save(item);

        assertThat(saved.getId()).isNotNull();
        assertThat(repository.findAll()).hasSize(1);
    }
}
