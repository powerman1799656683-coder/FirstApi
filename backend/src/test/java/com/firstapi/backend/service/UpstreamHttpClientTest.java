package com.firstapi.backend.service;

import com.firstapi.backend.config.RelayProperties;
import com.firstapi.backend.model.IpItem;
import com.firstapi.backend.model.RelayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpstreamHttpClientTest {

    private UpstreamHttpClient client;

    @BeforeEach
    void setUp() {
        client = new UpstreamHttpClient(new ObjectMapper(), new RelayProperties());
    }

    @Test
    void parseProxyAddressSupportsCredentialsInUri() {
        UpstreamHttpClient.ProxyAddress address = client.parseProxyAddress("http://user:pass@proxy.example.com:8080");

        assertThat(address.host()).isEqualTo("proxy.example.com");
        assertThat(address.port()).isEqualTo(8080);
        assertThat(address.userInfo()).isEqualTo("user:pass");
    }

    @Test
    void parseProxyAddressSupportsHostPortWithoutScheme() {
        UpstreamHttpClient.ProxyAddress address = client.parseProxyAddress("127.0.0.1:7890");

        assertThat(address.host()).isEqualTo("127.0.0.1");
        assertThat(address.port()).isEqualTo(7890);
        assertThat(address.userInfo()).isNull();
    }

    @Test
    void resolveProxyRouteAddsBasicAuthForHttpProxy() {
        IpItem proxy = new IpItem();
        proxy.setProtocol("HTTP");
        proxy.setAddress("http://user:pass@proxy.example.com:8080");

        UpstreamHttpClient.ProxyRoute route = client.resolveProxyRoute(proxy);

        assertThat(route).isNotNull();
        assertThat(route.proxy().type()).isEqualTo(Proxy.Type.HTTP);
        InetSocketAddress socketAddress = (InetSocketAddress) route.proxy().address();
        assertThat(socketAddress.getHostString()).isEqualTo("proxy.example.com");
        assertThat(socketAddress.getPort()).isEqualTo(8080);
        String expected = "Basic " + Base64.getEncoder()
                .encodeToString("user:pass".getBytes(StandardCharsets.UTF_8));
        assertThat(route.proxyAuthorization()).isEqualTo(expected);
    }

    @Test
    void resolveProxyRouteRejectsSocksAuthCredentials() {
        IpItem proxy = new IpItem();
        proxy.setProtocol("SOCKS5");
        proxy.setAddress("user:pass@127.0.0.1:1080");

        assertThatThrownBy(() -> client.resolveProxyRoute(proxy))
                .isInstanceOf(RelayException.class)
                .hasMessageContaining("SOCKS5 proxy auth is not supported");
    }
}
