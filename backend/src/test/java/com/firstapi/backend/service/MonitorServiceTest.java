package com.firstapi.backend.service;

import com.firstapi.backend.model.MonitorAlertItem;
import com.firstapi.backend.model.MonitorData;
import com.firstapi.backend.model.RelayRecordItem;
import com.firstapi.backend.repository.AccountRepository;
import com.firstapi.backend.repository.GroupRepository;
import com.firstapi.backend.repository.MonitorAlertRepository;
import com.firstapi.backend.repository.MonitorAlertRuleRepository;
import com.firstapi.backend.repository.MonitorNodeRepository;
import com.firstapi.backend.repository.RelayRecordRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonitorServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter RECORD_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    @Mock
    private MonitorNodeRepository nodeRepository;

    @Mock
    private MonitorAlertRepository alertRepository;

    @Mock
    private MonitorAlertRuleRepository alertRuleRepository;

    @Mock
    private RelayRecordRepository relayRecordRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private MonitorService service;

    @BeforeEach
    void setUp() {
        service = new MonitorService(
                nodeRepository,
                alertRepository,
                alertRuleRepository,
                relayRecordRepository,
                accountRepository,
                groupRepository,
                jdbcTemplate
        );
    }

    @Test
    void getSystemMonitorDataReturnsRuntimeDataset() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        when(relayRecordRepository.findAll()).thenReturn(List.of(
                record(now.minusSeconds(30), 180L),
                record(now.minusSeconds(10), 90L)
        ));
        when(alertRepository.findAll()).thenReturn(List.of(
                new MonitorAlertItem(1L, now.format(RECORD_TIME_FORMAT), "WARNING",
                        "CPU High", "Alerting", "system", "#f59e0b")
        ));

        MonitorData data = service.getSystemMonitorData("1h", null, null);

        assertThat(data).isNotNull();
        assertThat(data.lastRefresh).isNotBlank();
        assertThat(data.cpu).isNotNull();
        assertThat(data.memory).isNotNull();
        assertThat(data.jvm).isNotNull();
        assertThat(data.database).isNotNull();
        assertThat(data.disk).isNotNull();
        assertThat(data.network).isNotNull();
        assertThat(data.cpu.history).isNotNull().isNotEmpty();
        assertThat(data.network.history).isNotNull().isNotEmpty();
        assertThat(data.node).isNotNull();
        assertThat(data.node.os).isNotBlank();
        assertThat(data.node.nodeId).isNotBlank();
        assertThat(data.alertEvents).hasSize(1);
    }

    private static RelayRecordItem record(LocalDateTime createdAt, long latencyMs) {
        RelayRecordItem item = new RelayRecordItem();
        item.setCreatedAt(createdAt.format(RECORD_TIME_FORMAT));
        item.setLatencyMs(latencyMs);
        item.setTotalTokens(2048);
        item.setSuccess(true);
        return item;
    }
}
