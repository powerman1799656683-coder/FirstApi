// 修复点: 无（纯新增测试）
// 测试覆盖点: SensitiveDataService AES-256-GCM 加解密 + 边界（null/空/已加密跳过/格式错误密文）
package com.firstapi.backend.service;

import com.firstapi.backend.config.AppSecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SensitiveDataServiceTest {

    private SensitiveDataService service;

    @BeforeEach
    void setUp() {
        AppSecurityProperties props = new AppSecurityProperties();
        props.setDataSecret("test-secret-for-unit-tests");
        service = new SensitiveDataService(props);
        service.init();
    }

    // ======== protect ========

    @Nested
    @DisplayName("protect")
    class ProtectTests {

        @Test
        @DisplayName("正常字符串应加密为 enc: 前缀")
        void shouldEncryptToEncPrefix() {
            String encrypted = service.protect("my-secret-data");
            assertNotNull(encrypted);
            assertTrue(encrypted.startsWith("enc:"));
        }

        @Test
        @DisplayName("相同字符串两次加密结果应不同（随机IV）")
        void shouldProduceDifferentCiphertexts() {
            String enc1 = service.protect("same-data");
            String enc2 = service.protect("same-data");
            assertNotEquals(enc1, enc2);
        }

        @Test
        @DisplayName("null 输入应返回 null")
        void shouldReturnNullForNull() {
            assertNull(service.protect(null));
        }

        @Test
        @DisplayName("空字符串应原样返回")
        void shouldReturnEmptyForEmpty() {
            assertEquals("", service.protect(""));
        }

        @Test
        @DisplayName("已加密字符串不应再次加密")
        void shouldNotDoubleEncrypt() {
            String encrypted = service.protect("data");
            String doubleEncrypted = service.protect(encrypted);
            assertEquals(encrypted, doubleEncrypted); // 应原样返回
        }

        @Test
        @DisplayName("纯空格字符串应原样返回")
        void shouldReturnWhitespaceAsIs() {
            assertEquals("   ", service.protect("   "));
        }
    }

    // ======== reveal ========

    @Nested
    @DisplayName("reveal")
    class RevealTests {

        @Test
        @DisplayName("加密后解密应还原原始值")
        void shouldDecryptToOriginal() {
            String original = "my-secret-credential";
            String encrypted = service.protect(original);
            String decrypted = service.reveal(encrypted);
            assertEquals(original, decrypted);
        }

        @Test
        @DisplayName("null 输入应返回 null")
        void shouldReturnNullForNull() {
            assertNull(service.reveal(null));
        }

        @Test
        @DisplayName("非 enc: 前缀的字符串应原样返回")
        void shouldReturnNonEncryptedAsIs() {
            assertEquals("plain-text", service.reveal("plain-text"));
        }

        @Test
        @DisplayName("格式错误的密文应抛出异常")
        void shouldThrowForMalformedCiphertext() {
            assertThrows(IllegalStateException.class,
                    () -> service.reveal("enc:invalid-format-no-dot"));
        }

        @Test
        @DisplayName("中文数据应正确加解密")
        void shouldHandleUnicode() {
            String original = "中文敏感数据测试";
            String encrypted = service.protect(original);
            assertEquals(original, service.reveal(encrypted));
        }

        @Test
        @DisplayName("长字符串应正确加解密")
        void shouldHandleLongString() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                sb.append("a");
            }
            String original = sb.toString();
            String encrypted = service.protect(original);
            assertEquals(original, service.reveal(encrypted));
        }
    }
}
