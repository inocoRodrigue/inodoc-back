package com.esn.inodoc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringJUnitConfig(ApplicationBackupServiceTest.Config.class)
class ApplicationBackupServiceTest {
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:backup;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        }
        @Bean JdbcTemplate jdbc(DataSource source) { return new JdbcTemplate(source); }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
        @Bean ApplicationBackupService backup(JdbcTemplate jdbc) {
            return new ApplicationBackupService(jdbc, new ObjectMapper(), storage(), initializer());
        }
        @Bean S3ImageStorage storage() { return org.mockito.Mockito.mock(S3ImageStorage.class); }
        @Bean com.esn.inodoc.config.DataInitializer initializer() {
            return org.mockito.Mockito.mock(com.esn.inodoc.config.DataInitializer.class);
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationBackupService backup;
    @Autowired S3ImageStorage storage;
    @Autowired com.esn.inodoc.config.DataInitializer initializer;

    @BeforeEach
    void prepare() {
        org.mockito.Mockito.reset(storage, initializer);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE profiles (id INTEGER PRIMARY KEY, name VARCHAR NOT NULL)");
        jdbc.execute("CREATE TABLE competence_dossiers (id INTEGER PRIMARY KEY, profile_id INTEGER REFERENCES profiles(id), master BOOLEAN NOT NULL)");
        jdbc.execute("CREATE TABLE experiences (id INTEGER PRIMARY KEY, dossier_id INTEGER REFERENCES competence_dossiers(id))");
        jdbc.execute("CREATE TABLE experience_projects (id INTEGER PRIMARY KEY, experience_id INTEGER REFERENCES experiences(id))");
        jdbc.execute("CREATE TABLE cra_declarations (id INTEGER PRIMARY KEY, archived BOOLEAN, created_at TIMESTAMP WITH TIME ZONE)");
        jdbc.execute("CREATE TABLE cra_day_entries (id INTEGER PRIMARY KEY, declaration_id INTEGER REFERENCES cra_declarations(id), quantity NUMERIC(5,2))");
        jdbc.update("INSERT INTO profiles VALUES (1, 'Alice')");
        jdbc.update("INSERT INTO competence_dossiers VALUES (10, 1, TRUE), (11, 1, FALSE)");
        jdbc.update("INSERT INTO experiences VALUES (20, 10), (21, 11)");
        jdbc.update("INSERT INTO experience_projects VALUES (30, 20), (31, 21)");
        jdbc.update("INSERT INTO cra_declarations VALUES (40, TRUE, TIMESTAMP WITH TIME ZONE '2024-01-15 09:30:00+01:00'), (41, FALSE, NULL)");
        jdbc.update("INSERT INTO cra_day_entries VALUES (50, 40, 0.50), (51, 41, 1.00)");
    }

    @Test
    void roundTripRestoresAllCraAndOnlyMasterDossiersWithTheirChildren() {
        byte[] zip = backup.exportZip();
        jdbc.update("UPDATE profiles SET name = 'Changed'");
        backup.importZip(zip);
        assertThat(jdbc.queryForObject("SELECT name FROM profiles", String.class)).isEqualTo("Alice");
        assertThat(jdbc.queryForList("SELECT id FROM competence_dossiers", Integer.class)).containsExactly(10);
        assertThat(jdbc.queryForList("SELECT id FROM experiences", Integer.class)).containsExactly(20);
        assertThat(jdbc.queryForList("SELECT id FROM experience_projects", Integer.class)).containsExactly(30);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cra_declarations", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cra_day_entries", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT archived FROM cra_declarations WHERE id=40", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT quantity FROM cra_day_entries WHERE id=50", java.math.BigDecimal.class))
                .isEqualByComparingTo("0.50");
        assertThat(jdbc.queryForObject("SELECT created_at FROM cra_declarations WHERE id=40", java.time.OffsetDateTime.class))
                .isEqualTo(java.time.OffsetDateTime.parse("2024-01-15T09:30:00+01:00"));
    }

    @Test
    void invalidZipDoesNotDeleteAnything() {
        assertThatThrownBy(() -> backup.importZip(new byte[]{1, 2, 3})).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM competence_dossiers", Integer.class)).isEqualTo(2);
    }

    @Test
    void annualReviewsAndCareerTemplatesAndRecordsAreRestoredAfterLaterChanges() {
        java.util.UUID admin = prepareAdmin();
        jdbc.execute("CREATE DOMAIN TIMESTAMPTZ AS TIMESTAMP WITH TIME ZONE");
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            for (String migration : java.util.List.of("V7__reviews.sql", "V8__career.sql", "V9__private_shares.sql")) {
                org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
                        new org.springframework.core.io.ClassPathResource("db/migration/" + migration));
            }
            return null;
        });
        java.util.UUID form = java.util.UUID.randomUUID();
        java.util.UUID campaign = java.util.UUID.randomUUID();
        java.util.UUID submission = java.util.UUID.randomUUID();
        java.util.UUID template = java.util.UUID.randomUUID();
        java.util.UUID entry = java.util.UUID.randomUUID();
        String reviewSchema = "{\"fields\":[{\"key\":\"annualGoals\",\"label\":\"Objectifs personnalises\"}]}";
        String careerSchema = "{\"fields\":[{\"key\":\"milestone\",\"label\":\"Etape personnalisee\"}]}";
        String answers = "{\"annualGoals\":\"Certification obtenue\"}";
        String careerData = "{\"milestone\":\"Promotion\"}";
        jdbc.update("INSERT INTO review_forms(id,created_at,updated_at,title,schema) VALUES (?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'Annuel personnalise',?)", form, reviewSchema);
        jdbc.update("INSERT INTO review_campaigns(id,created_at,updated_at,title,type,form_id,status) VALUES (?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'Campagne archivee','ANNUAL',?,'CLOSED')", campaign, form);
        jdbc.update("INSERT INTO review_submissions(id,created_at,updated_at,campaign_id,user_id,answers,status) VALUES (?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?,?,?,'SUBMITTED')", submission, campaign, admin, answers);
        jdbc.update("INSERT INTO career_templates(id,created_at,updated_at,title,entry_schema) VALUES (?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'Carnet personnalise',?)", template, careerSchema);
        jdbc.update("INSERT INTO career_entries(id,created_at,updated_at,user_id,title,data) VALUES (?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?,'Evolution',?)", entry, admin, careerData);
        jdbc.update("INSERT INTO reference_documents(id,created_at,updated_at,title,content) VALUES (?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'Guide','Reference personnalisee')", java.util.UUID.randomUUID());
        jdbc.update("INSERT INTO career_entry_shares(id,created_at,updated_at,career_entry_id,manager_user_id) VALUES (?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?,?)", java.util.UUID.randomUUID(), entry, admin);
        jdbc.update("INSERT INTO review_submission_shares(id,created_at,updated_at,submission_id,manager_user_id) VALUES (?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?,?)", java.util.UUID.randomUUID(), submission, admin);
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
                new org.springframework.core.io.ClassPathResource("db/migration/V10__campaign_form_snapshot.sql"));
            return null;
        });
        String modifiedReviewSchema = reviewSchema.replace("annualGoals", "newAnnualGoals");
        jdbc.update("UPDATE review_forms SET schema=?", modifiedReviewSchema);

        byte[] zip = backup.exportZip();
        jdbc.update("UPDATE review_forms SET schema='{}'");
        jdbc.update("UPDATE career_templates SET entry_schema='{}'");
        jdbc.update("DELETE FROM review_submissions");
        jdbc.update("DELETE FROM career_entries");
        backup.importZip(zip);

        assertThat(jdbc.queryForObject("SELECT schema FROM review_forms", String.class)).isEqualTo(modifiedReviewSchema);
        assertThat(jdbc.queryForObject("SELECT form_schema FROM review_campaigns", String.class)).isEqualTo(reviewSchema);
        assertThat(jdbc.queryForObject("SELECT entry_schema FROM career_templates", String.class)).isEqualTo(careerSchema);
        assertThat(jdbc.queryForObject("SELECT answers FROM review_submissions", String.class)).isEqualTo(answers);
        assertThat(jdbc.queryForObject("SELECT data FROM career_entries", String.class)).isEqualTo(careerData);
        assertThat(jdbc.queryForObject("SELECT status FROM review_campaigns", String.class)).isEqualTo("CLOSED");
        assertThat(jdbc.queryForObject("SELECT content FROM reference_documents", String.class)).isEqualTo("Reference personnalisee");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM career_entry_shares", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM review_submission_shares", Integer.class)).isEqualTo(1);
    }

    @Test
    void resetConfirmationCodesAreNeverExportedOrRestored() throws Exception {
        java.util.UUID admin = prepareAdmin();
        jdbc.execute("CREATE DOMAIN TIMESTAMPTZ AS TIMESTAMP WITH TIME ZONE");
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
                    new org.springframework.core.io.ClassPathResource("db/migration/V29__application_reset_challenges.sql"));
            return null;
        });
        jdbc.update("INSERT INTO application_reset_challenges VALUES (?, ?, 'admin@test', 'secret-hash', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
                admin, java.util.UUID.randomUUID());
        byte[] zip = backup.exportZip();
        try (var input = new ZipInputStream(new ByteArrayInputStream(zip))) {
            input.getNextEntry();
            assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                    .doesNotContain("application_reset_challenges", "secret-hash");
        }
        backup.importZip(zip);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM application_reset_challenges", Integer.class)).isZero();
    }

    @Test
    void constraintFailureRollsBackTheWholeReplacement() throws Exception {
        byte[] broken = archiveWithInvalidName(backup.exportZip());
        assertThatThrownBy(() -> backup.importZip(broken)).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT name FROM profiles", String.class)).isEqualTo("Alice");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM competence_dossiers", Integer.class)).isEqualTo(2);
    }

    private byte[] archiveWithInvalidName(byte[] zip) throws Exception {
        String json;
        try (var input = new ZipInputStream(new ByteArrayInputStream(zip))) {
            input.getNextEntry();
            json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        byte[] broken;
        try (var output = new ByteArrayOutputStream(); var archive = new ZipOutputStream(output)) {
            archive.putNextEntry(new ZipEntry("database.json"));
            archive.write(json.replace("\"name\":\"Alice\"", "\"name\":null").getBytes(StandardCharsets.UTF_8));
            archive.closeEntry();
            archive.finish();
            broken = output.toByteArray();
        }
        return broken;
    }

    @Test
    void imagesAreEmbeddedRestoredAndRewrittenIncludingJsonFields() {
        jdbc.execute("ALTER TABLE profiles ADD COLUMN image_url VARCHAR");
        jdbc.execute("ALTER TABLE profiles ADD COLUMN details VARCHAR");
        String previous = "https://old-bucket/image.png";
        String restored = "https://new-bucket/restored/image.png";
        var image = new S3ImageStorage.BackupImage(previous, "image/png", new byte[]{1, 2, 3});
        jdbc.update("UPDATE profiles SET image_url=?, details=?", previous, "{\"nested\":[\"" + previous + "\"]}");
        org.mockito.Mockito.when(storage.isStoredImage(previous)).thenReturn(true);
        org.mockito.Mockito.when(storage.backupImage(previous)).thenReturn(image);
        org.mockito.Mockito.when(storage.restoreImage(org.mockito.ArgumentMatchers.any())).thenReturn(restored);
        byte[] zip = backup.exportZip();
        backup.importZip(zip);
        assertThat(jdbc.queryForObject("SELECT image_url FROM profiles", String.class)).isEqualTo(restored);
        assertThat(jdbc.queryForObject("SELECT details FROM profiles", String.class)).contains(restored).doesNotContain(previous);
        org.mockito.Mockito.verify(storage).backupImage(previous);
        org.mockito.Mockito.verify(storage, org.mockito.Mockito.never()).deleteRestoredImage(restored);
    }

    @Test
    void inaccessibleImageAbortsResetBeforeDeletingData() {
        jdbc.execute("ALTER TABLE profiles ADD COLUMN image_url VARCHAR");
        String image = "https://bucket/unavailable.png";
        jdbc.update("UPDATE profiles SET image_url=?", image);
        org.mockito.Mockito.when(storage.isStoredImage(image)).thenReturn(true);
        org.mockito.Mockito.when(storage.backupImage(image)).thenThrow(new IllegalStateException("S3 unavailable"));
        assertThatThrownBy(() -> backup.reset(java.util.UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM competence_dossiers", Integer.class)).isEqualTo(2);
        org.mockito.Mockito.verifyNoInteractions(initializer);
    }

    @Test
    void failedImportCleansUpNewImagesAndPreservesTheDatabase() throws Exception {
        jdbc.execute("ALTER TABLE profiles ADD COLUMN image_url VARCHAR");
        String previous = "https://bucket/image.png";
        String restored = "https://bucket/restored/new.png";
        jdbc.update("UPDATE profiles SET image_url=?", previous);
        org.mockito.Mockito.when(storage.isStoredImage(previous)).thenReturn(true);
        org.mockito.Mockito.when(storage.backupImage(previous))
                .thenReturn(new S3ImageStorage.BackupImage(previous, "image/png", new byte[]{1}));
        byte[] zip = archiveWithInvalidName(backup.exportZip());
        org.mockito.Mockito.when(storage.restoreImage(org.mockito.ArgumentMatchers.any())).thenReturn(restored);
        assertThatThrownBy(() -> backup.importZip(zip)).isInstanceOf(RuntimeException.class);
        org.mockito.Mockito.verify(storage).deleteRestoredImage(restored);
        assertThat(jdbc.queryForObject("SELECT image_url FROM profiles", String.class)).isEqualTo(previous);
    }

    private java.util.UUID prepareAdmin() {
        java.util.UUID admin = java.util.UUID.randomUUID();
        java.util.UUID role = java.util.UUID.randomUUID();
        jdbc.execute("CREATE TABLE users (id UUID PRIMARY KEY, email VARCHAR NOT NULL, password_hash VARCHAR NOT NULL, enabled BOOLEAN, profile_id INTEGER REFERENCES profiles(id))");
        jdbc.execute("CREATE TABLE roles (id UUID PRIMARY KEY, name VARCHAR NOT NULL)");
        jdbc.execute("CREATE TABLE user_roles (user_id UUID REFERENCES users(id), role_id UUID REFERENCES roles(id), PRIMARY KEY(user_id,role_id))");
        jdbc.update("INSERT INTO users VALUES (?, 'admin@test', 'hash', TRUE, 1)", admin);
        jdbc.update("INSERT INTO roles VALUES (?, 'ADMIN')", role);
        jdbc.update("INSERT INTO user_roles VALUES (?, ?)", admin, role);
        return admin;
    }

    @Test
    void resetReturnsRestorableBackupAndKeepsOnlyTheActingAdminWithoutProfile() {
        java.util.UUID admin = prepareAdmin();
        org.mockito.Mockito.doAnswer(invocation -> {
            jdbc.update("INSERT INTO roles VALUES (?, 'ADMIN')", java.util.UUID.randomUUID());
            return null;
        }).when(initializer).initializeAfterReset();
        byte[] zip = backup.reset(admin);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM profiles", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cra_declarations", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE id=?", String.class, admin)).isEqualTo("hash");
        assertThat(jdbc.queryForObject("SELECT profile_id FROM users WHERE id=?", Integer.class, admin)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_roles", Integer.class)).isEqualTo(1);
        backup.importZip(zip);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cra_declarations", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT profile_id FROM users WHERE id=?", Integer.class, admin)).isEqualTo(1);
    }

    @Test
    void resetFailureRollsBackAllDeletions() {
        java.util.UUID admin = prepareAdmin();
        org.mockito.Mockito.doThrow(new IllegalStateException("Cannot seed"))
                .when(initializer).initializeAfterReset();
        assertThatThrownBy(() -> backup.reset(admin)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM profiles", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cra_declarations", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT profile_id FROM users WHERE id=?", Integer.class, admin)).isEqualTo(1);
    }

    @Test
    void archiveWithoutAnActiveAdminIsRejectedBeforeReplacement() {
        java.util.UUID admin = prepareAdmin();
        jdbc.update("UPDATE users SET enabled=FALSE");
        byte[] zip = backup.exportZip();
        jdbc.update("UPDATE users SET enabled=TRUE");
        assertThatThrownBy(() -> backup.importZip(zip)).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT enabled FROM users WHERE id=?", Boolean.class, admin)).isTrue();
    }
}