package com.esn.inodoc.service;

import com.esn.inodoc.controller.error.IntegrationUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;
import java.time.Duration;

@Service
public class S3ImageStorage {
    private static final Logger log = LoggerFactory.getLogger(S3ImageStorage.class);
    private final String bucket;
    private final String region;
    private final String endpoint;
    private final String accessKey;
    private final String secretKey;

    public S3ImageStorage(@Value("${inodoc.media.bucket}") String bucket,
                          @Value("${inodoc.media.region:us-east-1}") String region,
                          @Value("${inodoc.media.endpoint:}") String endpoint,
                          @Value("${inodoc.media.access-key:}") String accessKey,
                          @Value("${inodoc.media.secret-key:}") String secretKey) {
        this.bucket = bucket;
        this.region = region == null || region.isBlank() ? "us-east-1" : region.trim();
        this.endpoint = endpoint;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }

    public String upload(String key, ImageValidator.ValidatedImage image) {
        try (var client = client()) {
            client.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
                            .contentType(image.contentType()).contentLength((long) image.bytes().length)
                            .cacheControl("public, max-age=31536000, immutable").build(),
                    RequestBody.fromBytes(image.bytes()));
            return client.utilities().getUrl(builder -> builder.bucket(bucket).key(key)).toExternalForm();
        } catch (S3Exception ex) {
            String code = ex.awsErrorDetails() == null ? "unknown" : ex.awsErrorDetails().errorCode();
            log.warn("S3 upload rejected: status={}, code={}", ex.statusCode(), code);
            if (ex.statusCode() == 401 || ex.statusCode() == 403) {
                throw new IntegrationUnavailableException("Le stockage S3 a refusé l'accès. Vérifiez la clé d'accès, la clé secrète et les droits d'écriture du bucket.");
            }
            if (ex.statusCode() == 404) {
                throw new IntegrationUnavailableException("Le bucket S3 est introuvable. Vérifiez son nom et l'endpoint Cellar.");
            }
            throw new IntegrationUnavailableException("Le stockage S3 a refusé l'envoi de l'image. Vérifiez sa configuration.");
        } catch (SdkException ex) {
            log.warn("S3 upload connection failed: type={}", ex.getClass().getSimpleName());
            throw new IntegrationUnavailableException("Le stockage S3 est indisponible. L'image n'a pas été enregistrée.");
        }
    }

    public void delete(String key) {
        try (var client = client()) {
            client.deleteObject(builder -> builder.bucket(bucket).key(key));
        } catch (SdkException ex) {
            throw new IntegrationUnavailableException("Le nettoyage de l'image S3 a échoué");
        }
    }

    public String signedUrl(String url) {
        String key = objectKey(url);
        if (key == null) return url;
        var builder = S3Presigner.builder().region(Region.of(region))
                .credentialsProvider(credentialsProvider())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(!endpoint.isBlank()).build());
        if (!endpoint.isBlank()) builder.endpointOverride(endpointUri());
        try (var presigner = builder.build()) {
            return presigner.presignGetObject(request -> request.signatureDuration(Duration.ofHours(1))
                    .getObjectRequest(object -> object.bucket(bucket).key(key))).url().toExternalForm();
        } catch (SdkException ex) {
            throw new IntegrationUnavailableException("La signature de l'image S3 a échoué.");
        }
    }

    public String storedUrl(String url) {
        if (objectKey(url) == null) return url;
        try {
            URI uri = URI.create(url);
            return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), null, null).toString();
        } catch (java.net.URISyntaxException ex) {
            throw new IntegrationUnavailableException("L'adresse de l'image S3 est invalide.");
        }
    }

    public boolean isStoredImage(String url) {
        return objectKey(url) != null;
    }

    public record BackupImage(String url, String contentType, byte[] bytes) {}

    public BackupImage backupImage(String url) {
        String key = objectKey(url);
        if (key == null) throw new IllegalArgumentException("Image hors du stockage de l'application");
        try (var client = client()) {
            var object = client.getObjectAsBytes(request -> request.bucket(bucket).key(key));
            return new BackupImage(url, object.response().contentType(), object.asByteArray());
        } catch (SdkException exception) {
            throw new IntegrationUnavailableException("Une image S3 est inaccessible. La sauvegarde est annulee.");
        }
    }

    public String restoreImage(BackupImage image) {
        return upload("restored/" + java.util.UUID.randomUUID(),
                new ImageValidator.ValidatedImage(image.bytes(), image.contentType(), ""));
    }

    public void deleteRestoredImage(String url) {
        String key = objectKey(url);
        if (key != null && key.startsWith("restored/")) delete(key);
    }

    private String objectKey(String url) {
        if (url == null || url.isBlank() || bucket.isBlank()) return null;
        try {
            URI uri = URI.create(url);
            if (uri.getHost() == null || uri.getUserInfo() != null) return null;
            if (!endpoint.isBlank()) {
                URI target = endpointUri();
                if (!target.getHost().equalsIgnoreCase(uri.getHost())
                        || target.getPort() != uri.getPort() || !target.getScheme().equalsIgnoreCase(uri.getScheme())) {
                    return null;
                }
                String prefix = target.getPath().replaceAll("/$", "") + "/" + bucket + "/";
                return uri.getPath().startsWith(prefix) ? uri.getPath().substring(prefix.length()) : null;
            }
            if (uri.getHost().equalsIgnoreCase(bucket + ".s3." + region + ".amazonaws.com")
                    || uri.getHost().equalsIgnoreCase(bucket + ".s3.amazonaws.com")) {
                return uri.getPath().substring(1);
            }
            String prefix = "/" + bucket + "/";
            if (uri.getHost().equalsIgnoreCase("s3." + region + ".amazonaws.com")
                    && uri.getPath().startsWith(prefix)) return uri.getPath().substring(prefix.length());
            return null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private URI endpointUri() {
        return URI.create(endpoint.contains("://") ? endpoint : "https://" + endpoint);
    }

    private AwsCredentialsProvider credentialsProvider() {
        if (accessKey.isBlank() != secretKey.isBlank()
                || (!endpoint.isBlank() && accessKey.isBlank())) {
            throw new IntegrationUnavailableException("Les identifiants du stockage S3 ne sont pas configurés");
        }
        return accessKey.isBlank() ? DefaultCredentialsProvider.create()
                : StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }

    private S3Client client() {
        if (bucket.isBlank()) {
            throw new IntegrationUnavailableException("Le stockage S3 n'est pas configuré");
        }
        var builder = S3Client.builder().region(Region.of(region))
            .credentialsProvider(credentialsProvider())
                .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(60))
                .apiCallAttemptTimeout(Duration.ofSeconds(30)));
        if (!endpoint.isBlank()) {
            builder.endpointOverride(endpointUri())
                .forcePathStyle(true);
        }
        return builder.build();
    }
}
