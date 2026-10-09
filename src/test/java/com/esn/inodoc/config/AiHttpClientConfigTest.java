package com.esn.inodoc.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiHttpClientConfigTest {
    @Test
    void verifiedJvmAndExplicitOptOutBothCreateClients() {
        assertNotNull(AiHttpClientConfig.create("JVM", "", "", false));
        assertNotNull(AiHttpClientConfig.create("JVM", "", "", true));
    }

    @Test
    void customTrustConfigurationNeverSilentlyFallsBack() {
        assertThrows(IllegalStateException.class,
                () -> AiHttpClientConfig.create("PKCS12", "", "", false));
        assertThrows(IllegalStateException.class,
                () -> AiHttpClientConfig.create("UNKNOWN-STORE", "", "", false));
    }
}
