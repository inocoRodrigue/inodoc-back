package com.esn.inodoc.service;

import com.esn.inodoc.controller.error.IntegrationUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AiModelClient {
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final RestClient client;

    public AiModelClient(@Value("${inodoc.ai.base-url}") String baseUrl,
                         @Value("${inodoc.ai.api-key}") String apiKey,
                         @Value("${inodoc.ai.model}") String model,
                         @org.springframework.beans.factory.annotation.Qualifier("aiRestClient") RestClient client) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.client = client;
    }

    public String generateJson(String systemPrompt, String userContent, int maxResponseLength) {
        validateConfiguration();
        URI endpoint = endpoint();
        Map<String, Object> request = Map.of(
                "model", model,
                "temperature", 1,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userContent)));

        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                JsonNode response = client.post().uri(endpoint)
                        .headers(headers -> headers.setBearerAuth(apiKey))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve().body(JsonNode.class);
                JsonNode content = response == null ? null
                        : response.path("choices").path(0).path("message").path("content");
                if (content == null || !content.isTextual() || content.textValue().length() > maxResponseLength) {
                    throw new IntegrationUnavailableException("L'IA a renvoyé une réponse invalide.");
                }
                return content.textValue();
            } catch (RestClientException ex) {
                if (hasSslFailure(ex)) {
                    throw new IntegrationUnavailableException(
                            "Le certificat du service IA n'a pas pu être vérifié. Contactez votre administrateur.");
                }
                if (attempt == 0 && isTemporaryFailure(ex)) {
                    pauseBeforeRetry();
                    continue;
                }
                throw new IntegrationUnavailableException("Le service IA est indisponible. Réessayez plus tard.");
            }
        }
        throw new IntegrationUnavailableException("Le service IA est indisponible. Réessayez plus tard.");
    }

    public void validateConfiguration() {
        if (baseUrl.isBlank() || apiKey.isBlank() || model.isBlank()) {
            throw new IntegrationUnavailableException("Le service IA n'est pas configuré.");
        }
        endpoint();
    }

    private URI endpoint() {
        URI endpoint;
        try {
            endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/chat/completions");
        } catch (IllegalArgumentException ex) {
            throw new IntegrationUnavailableException("L'adresse du service IA est invalide.");
        }
        if (endpoint.getHost() == null || endpoint.getUserInfo() != null
                || endpoint.getQuery() != null || endpoint.getFragment() != null) {
            throw new IntegrationUnavailableException("L'adresse du service IA est invalide.");
        }
        if (!"https".equalsIgnoreCase(endpoint.getScheme())
                && !("http".equalsIgnoreCase(endpoint.getScheme())
                && Set.of("localhost", "127.0.0.1", "[::1]").contains(endpoint.getHost()))) {
            throw new IntegrationUnavailableException("Le service IA doit utiliser HTTPS ou une adresse locale.");
        }
        return endpoint;
    }

    private boolean isTemporaryFailure(RestClientException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof RestClientResponseException responseException) {
                HttpStatusCode status = responseException.getStatusCode();
                return status.value() == 429 || status.is5xxServerError();
            }
        }
        return false;
    }

    private boolean hasSslFailure(RestClientException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof javax.net.ssl.SSLHandshakeException) {
                return true;
            }
        }
        return false;
    }

    private void pauseBeforeRetry() {
        try {
            Thread.sleep(300);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IntegrationUnavailableException("La requête au service IA a été interrompue.");
        }
    }
}