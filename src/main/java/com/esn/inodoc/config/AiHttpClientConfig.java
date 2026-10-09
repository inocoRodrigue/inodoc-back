package com.esn.inodoc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.time.Duration;

@Configuration
public class AiHttpClientConfig {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AiHttpClientConfig.class);

    @Bean("aiRestClient")
    public RestClient aiRestClient(
            @Value("${inodoc.ai.tls.trust-store-type:JVM}") String type,
            @Value("${inodoc.ai.tls.trust-store:}") String path,
            @Value("${inodoc.ai.tls.trust-store-password:}") String password,
            @Value("${inodoc.ai.tls.skip-certificate-verification:false}") boolean skipVerification) {
        return create(type, path, password, skipVerification);
    }

    public static RestClient create(String type, String path, String password) {
        return create(type, path, password, false);
    }

    public static RestClient create(String type, String path, String password, boolean skipVerification) {
        var client = java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10));
        if (skipVerification) {
            log.warn("AI_TLS_SKIP_CERTIFICATE_VERIFICATION=true : chaîne de certificats IA non vérifiée. "
                    + "Les CV et la clé API risquent d'être interceptés. Réactivez la vérification en production.");
            client.sslContext(unverifiedContext());
        } else if (!"JVM".equalsIgnoreCase(type)) {
            client.sslContext(trustedContext(type, path, password));
        }

        var factory = new JdkClientHttpRequestFactory(client.build());
        factory.setReadTimeout(Duration.ofSeconds(90));
        return RestClient.builder().requestFactory(factory).build();
    }

    private static SSLContext unverifiedContext() {
        try {
            var context = SSLContext.getInstance("TLS");
            // Scoped to the AI client only; the JDK still checks the requested hostname.
            context.init(null, new TrustManager[]{new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    throw new UnsupportedOperationException("Client authentication is not supported");
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    // Explicit opt-out controlled by AI_TLS_SKIP_CERTIFICATE_VERIFICATION.
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }}, null);
            return context;
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Impossible d'initialiser le client TLS de l'IA", ex);
        }
    }

    static SSLContext trustedContext(String type, String path, String password) {
        try {
            var store = KeyStore.getInstance(type);
            if ("Windows-ROOT".equalsIgnoreCase(type)) {
                store.load(null, null);
            } else {
                if (path.isBlank()) {
                    throw new IllegalStateException("AI_TLS_TRUST_STORE est requis pour le magasin TLS configuré");
                }
                try (var input = Files.newInputStream(Path.of(path))) {
                    store.load(input, password.toCharArray());
                }
            }
            if (store.size() == 0) {
                throw new IllegalStateException("Le magasin TLS de l'IA ne contient aucun certificat");
            }
            var managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            managers.init(store);
            var context = SSLContext.getInstance("TLS");
            context.init(null, managers.getTrustManagers(), null);
            return context;
        } catch (GeneralSecurityException | IOException ex) {
            throw new IllegalStateException("Impossible de charger le magasin TLS de l'IA. Vérifiez sa configuration.", ex);
        }
    }
}
