package com.esn.inodoc.service;

import com.esn.inodoc.controller.error.IntegrationUnavailableException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class S3ImageStorageTest {
    @Test
    void usesCustomEndpointBucketAndCredentialsForUploadAndDelete() throws Exception {
        List<String> requests = Collections.synchronizedList(new ArrayList<>());
        List<String> authorizations = Collections.synchronizedList(new ArrayList<>());
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
            var storage = new S3ImageStorage("test-bucket", "", endpoint,
                    "test-access-key", "test-secret-key");
            String url = storage.upload("profiles/test/photo.png", image());
            storage.delete("profiles/test/photo.png");

            assertEquals(endpoint + "/test-bucket/profiles/test/photo.png", url);
            assertEquals(List.of("PUT /test-bucket/profiles/test/photo.png",
                    "DELETE /test-bucket/profiles/test/photo.png"), requests);
            assertEquals(2, authorizations.size());
            assertTrue(authorizations.stream().allMatch(value -> value != null
                    && value.contains("Credential=test-access-key/")
                    && value.contains("/us-east-1/s3/aws4_request")));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsIncompleteCustomCredentialsBeforeSendingRequests() {
        for (String[] credentials : List.of(new String[]{"", ""},
                new String[]{"test-access-key", ""}, new String[]{"", "test-secret-key"})) {
            var storage = new S3ImageStorage("test-bucket", "us-east-1", "cellar.example.test",
                    credentials[0], credentials[1]);
            assertThrows(IntegrationUnavailableException.class,
                    () -> storage.upload("profiles/test/photo.png", image()));
        }
    }

    @Test
    void rejectsMissingBucketBeforeSendingRequests() {
        var storage = new S3ImageStorage("", "us-east-1", "cellar.example.test",
                "test-access-key", "test-secret-key");
        assertThrows(IntegrationUnavailableException.class,
                () -> storage.upload("profiles/test/photo.png", image()));
    }

    @Test
    void reportsInvalidCredentialsInsteadOfClaimingStorageIsUnavailable() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] response = "<Error><Code>SignatureDoesNotMatch</Code></Error>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/xml");
            exchange.sendResponseHeaders(403, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var storage = new S3ImageStorage("test-bucket", "",
                    "http://127.0.0.1:" + server.getAddress().getPort(), "test-access-key", "test-secret-key");
            var error = assertThrows(IntegrationUnavailableException.class,
                    () -> storage.upload("profiles/test/photo.png", image()));
            assertTrue(error.getMessage().contains("refusé l'accès"));
            assertFalse(error.getMessage().contains("test-secret-key"));
        } finally {
            server.stop(0);
        }
    }

    private ImageValidator.ValidatedImage image() {
        return new ImageValidator.ValidatedImage("test-image".getBytes(StandardCharsets.UTF_8),
                "image/png", "png");
    }

    @Test
    void signsPrivateCellarImagesAndKeepsOnlyTheStableUrlForStorage() {
        var storage = new S3ImageStorage("test-bucket", "", "cellar.example.test",
                "test-access-key", "test-secret-key");
        String stable = "https://cellar.example.test/test-bucket/dossier-assets/photo%20test.png";
        String signed = storage.signedUrl(stable);
        var uri = java.net.URI.create(signed);
        assertEquals("/test-bucket/dossier-assets/photo test.png", uri.getPath());
        assertTrue(uri.getQuery().contains("X-Amz-Signature="));
        assertTrue(uri.getQuery().contains("X-Amz-Expires=3600"));
        assertTrue(uri.getQuery().contains("test-access-key"));
        assertEquals(stable, storage.storedUrl(signed));
        assertTrue(storage.signedUrl(signed).contains("X-Amz-Signature="));
    }

    @Test
    void neverSignsOrRewritesImagesOutsideTheConfiguredBucket() {
        var storage = new S3ImageStorage("test-bucket", "", "cellar.example.test",
                "test-access-key", "test-secret-key");
        for (String url : List.of("https://other.example.test/photo.png?token=keep",
                "https://cellar.example.test/another-bucket/photo.png",
                "https://cellar.example.test.evil.test/test-bucket/photo.png")) {
            assertEquals(url, storage.signedUrl(url));
            assertEquals(url, storage.storedUrl(url));
        }
        assertNull(storage.signedUrl(null));
    }

}