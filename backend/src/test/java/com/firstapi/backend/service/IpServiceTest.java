package com.firstapi.backend.service;

import com.firstapi.backend.config.RelayProperties;
import com.firstapi.backend.model.IpItem;
import com.firstapi.backend.model.RelayException;
import com.firstapi.backend.model.RelayResult;
import com.firstapi.backend.repository.IpRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IpServiceTest {

    @Mock
    private IpRepository repository;

    @Mock
    private UpstreamHttpClient upstreamHttpClient;

    @Mock
    private RelayProperties relayProperties;

    private IpService service;

    @BeforeEach
    void setUp() {
        service = new IpService(repository, upstreamHttpClient, relayProperties);
    }

    @Test
    void createAcceptsHttpProxyAddressWithCredentials() {
        IpItem.Request request = new IpItem.Request();
        request.setName("vps-proxy");
        request.setProtocol("HTTP");
        request.setAddress("http://user:pass@proxy.example.com:8080");
        request.setLocation("vps");

        when(repository.save(any(IpItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IpItem created = service.create(request);

        assertThat(created.getAddress()).isEqualTo("http://user:pass@proxy.example.com:8080");
        assertThat(created.getProtocol()).isEqualTo("HTTP");
    }

    @Test
    void createRejectsAddressContainingSpaces() {
        IpItem.Request request = new IpItem.Request();
        request.setProtocol("HTTP");
        request.setAddress("proxy example.com:8080");

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateAcceptsCredentialAddress() {
        IpItem current = new IpItem();
        current.setId(9L);
        current.setName("old");
        current.setProtocol("HTTP");
        current.setAddress("127.0.0.1:8080");

        when(repository.findById(9L)).thenReturn(current);
        when(repository.update(eq(9L), any(IpItem.class))).thenAnswer(invocation -> invocation.getArgument(1));

        IpItem.Request request = new IpItem.Request();
        request.setAddress("http://user:pass@proxy.example.com:8080");

        IpItem updated = service.update(9L, request);

        assertThat(updated.getAddress()).isEqualTo("http://user:pass@proxy.example.com:8080");
    }

    @Test
    void testIpUsesRealProbeAndWritesLatency() {
        IpItem current = new IpItem();
        current.setId(11L);
        current.setProtocol("SOCKS5");
        current.setAddress("127.0.0.1:7890");
        when(repository.findById(11L)).thenReturn(current);
        when(repository.update(eq(11L), any(IpItem.class))).thenAnswer(invocation -> invocation.getArgument(1));
        when(relayProperties.getOpenaiBaseUrl()).thenReturn("https://api.openai.com");

        RelayResult result = new RelayResult();
        result.setStatusCode(204);
        result.setLatencyMs(37L);
        result.setSuccess(true);
        when(upstreamHttpClient.get(eq("https://api.openai.com/v1/models"), any(), eq(current))).thenReturn(result);

        IpItem tested = service.testIp(11L);

        assertThat(tested.getStatus()).isEqualTo("normal");
        assertThat(tested.getLatency()).isEqualTo("37ms");
    }

    @Test
    void testIpMarksErrorWhenProbeThrows() {
        IpItem current = new IpItem();
        current.setId(12L);
        current.setProtocol("SOCKS5");
        current.setAddress("127.0.0.1:7890");
        when(repository.findById(12L)).thenReturn(current);
        when(repository.update(eq(12L), any(IpItem.class))).thenAnswer(invocation -> invocation.getArgument(1));
        when(relayProperties.getOpenaiBaseUrl()).thenReturn("https://api.openai.com");
        when(upstreamHttpClient.get(eq("https://api.openai.com/v1/models"), any(), eq(current)))
                .thenThrow(new RelayException(HttpStatus.BAD_GATEWAY, "probe failed", "upstream_error"));

        IpItem tested = service.testIp(12L);

        assertThat(tested.getStatus()).isEqualTo("error");
        assertThat(tested.getLatency()).isEqualTo("-");
    }
}
