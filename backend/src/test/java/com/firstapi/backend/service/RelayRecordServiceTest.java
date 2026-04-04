package com.firstapi.backend.service;

import com.firstapi.backend.model.ApiKeyItem;
import com.firstapi.backend.model.RelayResult;
import com.firstapi.backend.model.RelayRoute;
import com.firstapi.backend.repository.RelayRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RelayRecordServiceTest {

    @Mock
    private RelayRecordRepository relayRecordRepository;

    @Mock
    private CostCalculationService costCalculationService;

    @Mock
    private UserService userService;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private DailyQuotaService dailyQuotaService;

    private RelayRecordService relayRecordService;

    @BeforeEach
    void setUp() {
        relayRecordService = new RelayRecordService(relayRecordRepository, costCalculationService, userService, subscriptionService, dailyQuotaService);
    }

    @Test
    void recordStoresCreatedAtForRuntimeStatistics() {
        ApiKeyItem apiKey = new ApiKeyItem();
        apiKey.setId(3L);
        apiKey.setOwnerId(7L);

        RelayResult result = new RelayResult();
        result.setSuccess(true);
        result.setStatusCode(200);
        result.setLatencyMs(512L);
        result.setAccountId(11L);
        result.setRequestId("req_abc");
        result.setPromptTokens(11);
        result.setCompletionTokens(22);
        result.setTotalTokens(33);

        relayRecordService.record(apiKey, new RelayRoute("openai"), result, "gpt-4o-mini", null);

        ArgumentCaptor<com.firstapi.backend.model.RelayRecordItem> captor = ArgumentCaptor.forClass(com.firstapi.backend.model.RelayRecordItem.class);
        verify(relayRecordRepository).save(captor.capture());

        assertThat(captor.getValue().getCreatedAt())
                .as("createdAt should always be persisted for account pool statistics")
                .matches("\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}");
    }
}

