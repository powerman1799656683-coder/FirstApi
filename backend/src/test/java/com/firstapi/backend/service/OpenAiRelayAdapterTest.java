package com.firstapi.backend.service;

import com.firstapi.backend.model.AccountItem;
import com.firstapi.backend.model.IpItem;
import com.firstapi.backend.model.RelayChatCompletionRequest;
import com.firstapi.backend.model.RelayResult;
import com.firstapi.backend.model.RelayRoute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAiRelayAdapterTest {

    @Mock
    private RelayAccountSelector relayAccountSelector;

    @Mock
    private SensitiveDataService sensitiveDataService;

    @Mock
    private UpstreamHttpClient upstreamHttpClient;

    private OpenAiRelayAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OpenAiRelayAdapter(
                relayAccountSelector,
                sensitiveDataService,
                upstreamHttpClient,
                new ObjectMapper()
        );
    }

    @Test
    void relayPassesResolvedProxyToUpstreamClient() {
        AccountItem account = new AccountItem();
        account.setId(7L);
        account.setCredential("enc-credential");

        IpItem proxy = new IpItem();
        proxy.setId(3L);
        proxy.setProtocol("SOCKS5");
        proxy.setAddress("127.0.0.1:7890");

        when(relayAccountSelector.selectAccount("openai", "ChatGPT Plus")).thenReturn(account);
        when(relayAccountSelector.resolveBaseUrl(account, "openai")).thenReturn("https://api.openai.com");
        when(relayAccountSelector.resolveProxy(account)).thenReturn(proxy);
        when(sensitiveDataService.reveal("enc-credential")).thenReturn("sk-test");

        RelayResult upstream = new RelayResult();
        upstream.setSuccess(true);
        when(upstreamHttpClient.postJson(
                eq("https://api.openai.com/v1/chat/completions"),
                anyMap(),
                any(JsonNode.class),
                same(proxy)
        )).thenReturn(upstream);

        RelayChatCompletionRequest request = RelayChatCompletionRequest.builder()
                .model("gpt-4o-mini")
                .addMessage("user", "hello")
                .build();

        RelayResult result = adapter.relay(request, new RelayRoute("openai"), "ChatGPT Plus");

        assertThat(result).isSameAs(upstream);
        assertThat(result.getAccountId()).isEqualTo(7L);
        verify(upstreamHttpClient).postJson(
                eq("https://api.openai.com/v1/chat/completions"),
                anyMap(),
                any(JsonNode.class),
                same(proxy)
        );
        verify(relayAccountSelector).releaseAccount(account);
    }
}
