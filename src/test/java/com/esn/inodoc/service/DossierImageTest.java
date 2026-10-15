package com.esn.inodoc.service;

import com.esn.inodoc.config.AppProperties;
import com.esn.inodoc.domain.Certification;
import com.esn.inodoc.domain.CompetenceDossier;
import com.esn.inodoc.domain.Education;
import com.esn.inodoc.domain.Profile;
import com.esn.inodoc.domain.ShareType;
import com.esn.inodoc.domain.SharedLink;
import com.esn.inodoc.domain.TestResult;
import com.esn.inodoc.domain.SkillGroup;
import com.esn.inodoc.domain.LanguageSkill;
import com.esn.inodoc.domain.ExperienceCompany;
import com.esn.inodoc.domain.ExperienceProject;
import com.esn.inodoc.dto.company.CompanyContactDto;
import com.esn.inodoc.dto.dossier.DossierDto;
import com.esn.inodoc.dto.dossier.DossierMapper;
import com.esn.inodoc.dto.dossier.DossierUpsertRequest;
import com.esn.inodoc.repo.CompetenceDossierRepository;
import com.esn.inodoc.repo.SharedLinkRepository;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfReader;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DossierImageTest {
    @Test
    void formatsExperienceDurationFromExactDates() {
        assertEquals("2 mois", ExperiencePeriodFormatter.formatDuration(
                java.time.LocalDate.of(2026, 9, 1), java.time.LocalDate.of(2026, 10, 31)));
        assertEquals("3 ans", ExperiencePeriodFormatter.formatDuration(
                java.time.LocalDate.of(2023, 9, 1), java.time.LocalDate.of(2026, 9, 1)));
        assertEquals("2 months", ExperiencePeriodFormatter.formatDuration(
            java.time.LocalDate.of(2026, 9, 1), java.time.LocalDate.of(2026, 10, 31), true));
        assertEquals("3 years", ExperiencePeriodFormatter.formatDuration(
            java.time.LocalDate.of(2023, 9, 1), java.time.LocalDate.of(2026, 9, 1), true));
        var dossier = dossier("https://external.example.test/");
        var experience = new ExperienceCompany();
        experience.setCompany("Datelia");
        experience.setStartDate(java.time.LocalDate.of(2026, 9, 1));
        experience.setEndDate(java.time.LocalDate.of(2026, 10, 31));
        dossier.addExperience(experience);
        var dtoExperience = DossierMapper.toDto(dossier).experiences().get(0);
        assertEquals("2 mois", dtoExperience.duration());
        assertEquals("09/2026 - 10/2026", dtoExperience.period());
    }

    @Test
    void translatedDossierPdfUsesEnglishSectionAndExperienceLabels() throws Exception {
        var source = dossier("https://external.example.test/");
        source.setDocumentLanguage("English");
        source.setSummary("Professional summary");
        var experience = new ExperienceCompany();
        experience.setCompany("Example Company");
        experience.setStartDate(java.time.LocalDate.of(2023, 1, 1));
        experience.setRoleTitle("Software developer");
        experience.setContext("Product development");
        var project = new ExperienceProject();
        project.setName("Platform");
        project.setContext("Cloud migration");
        experience.addProject(project);
        source.addExperience(experience);

        var company = mock(CompanySettingsService.class);
        when(company.get()).thenReturn(new CompanyContactDto(null, null, null, null, null, 12));
        var storage = new S3ImageStorage("test-bucket", "", "cellar.example.test", "test-key", "test-secret");
        var reader = new PdfReader(new PdfService(company, storage).generateDossierPdf(source));
        try {
            var extractor = new com.lowagie.text.pdf.parser.PdfTextExtractor(reader);
            String text = extractor.getTextFromPage(1).toUpperCase(java.util.Locale.ROOT);
            assertTrue(text.contains("SUMMARY"));
            assertTrue(text.contains("EDUCATION AND CERTIFICATIONS"));
            assertTrue(text.contains("EXPERIENCE"));
            assertTrue(text.contains("ROLE"));
            assertTrue(text.contains("PROJECT"));
            assertTrue(text.contains("CONTEXT"));
            assertTrue(text.contains("3 YEARS"));
            assertFalse(text.contains("RÉSUMÉ"));
            assertFalse(text.contains("EXPÉRIENCES"));
            assertFalse(text.contains("RÔLE"));
        } finally {
            reader.close();
        }
    }

    @Test
    void marksOnlyMastersObsoleteAfterTheConfiguredMonthThreshold() {
        var lastUpdate = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)
            .minusMonths(13).toInstant();

        assertTrue(DossierObsolescence.isObsolete(true, lastUpdate, 12));
        assertFalse(DossierObsolescence.isObsolete(false, lastUpdate, 12));
        assertFalse(DossierObsolescence.isObsolete(true, java.time.Instant.now(), 12));

        var master = mock(CompetenceDossier.class);
        when(master.isMaster()).thenReturn(true);
        when(master.getUpdatedAt()).thenReturn(lastUpdate);
        assertTrue(DossierMapper.toSummary(master, 12).obsolete());

        when(master.isMaster()).thenReturn(false);
        assertFalse(DossierMapper.toSummary(master, 12).obsolete());
    }

    @Test
    void signsWebAndSharedResponsesButPersistsOnlyStableImageUrls() {
        var storage = new S3ImageStorage("test-bucket", "", "cellar.example.test", "test-key", "test-secret");
        var dossier = dossier("https://cellar.example.test/test-bucket/dossier-assets/");
        var repository = mock(CompetenceDossierRepository.class);
        when(repository.findById(dossier.getId())).thenReturn(Optional.of(dossier));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        var service = new DossierService(repository, mock(ProfileService.class), mock(TechSkillService.class),
            mock(PdfService.class), storage, mock(CompanySettingsService.class));

        DossierDto response = service.get(dossier.getId());
        assertSigned(response);
        assertFalse(dossier.getEducations().get(0).getImageUrl().contains("?"));
        var request = new DossierUpsertRequest(response.label(), false, response.headline(),
                response.yearsOfExperience(), response.expertiseDomain(), response.summary(), response.skillGroups(),
                response.experiences(), response.certifications(), response.educations(), response.languages(),
                response.references(), response.testResults());
        assertSigned(service.update(dossier.getId(), request));
        assertFalse(dossier.getEducations().get(0).getImageUrl().contains("?"));
        assertFalse(dossier.getCertifications().get(0).getImageUrl().contains("?"));
        assertFalse(dossier.getTestResults().get(0).getImageUrl().contains("?"));

        var links = mock(SharedLinkRepository.class);
        var link = new SharedLink();
        link.setDossier(dossier);
        link.setType(ShareType.CLIENT_READONLY);
        when(links.findByTokenWithDossier("synthetic-token")).thenReturn(Optional.of(link));
        var shares = new ShareService(links, repository, mock(PasswordEncoder.class), new AppProperties(),
                mock(CompanySettingsService.class), mock(MailAccountService.class), mock(EmailService.class),
                mock(PdfService.class), storage);
        assertSigned(shares.access("synthetic-token", null).dossier());
    }

    @Test
    void pdfEmbedsAllPrivateDossierImagesUsingSignedRequests() throws Exception {
        List<String> requests = Collections.synchronizedList(new ArrayList<>());
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/test-bucket/dossier-assets/", exchange -> {
            if (exchange.getRequestURI().getQuery() == null
                    || !exchange.getRequestURI().getQuery().contains("X-Amz-Signature=")) {
                exchange.sendResponseHeaders(403, -1);
            } else {
                String path = exchange.getRequestURI().getPath();
                requests.add(path);
                byte[] png = png(path.hashCode());
                exchange.getResponseHeaders().add("Content-Type", "image/png");
                exchange.sendResponseHeaders(200, png.length);
                exchange.getResponseBody().write(png);
            }
            exchange.close();
        });
        server.start();
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
            var storage = new S3ImageStorage("test-bucket", "", endpoint, "test-key", "test-secret");
            var company = mock(CompanySettingsService.class);
            when(company.get()).thenReturn(new CompanyContactDto(null, null, null, null, null, 12));
            byte[] pdf = new PdfService(company, storage).generateDossierPdf(
                    dossier(endpoint + "/test-bucket/dossier-assets/"));
            assertEquals(3, requests.size());
            int imageCount = 0;
            var reader = new PdfReader(pdf);
            try {
                for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                    var resources = reader.getPageN(page).getAsDict(PdfName.RESOURCES);
                    var objects = resources.getAsDict(PdfName.XOBJECT);
                    if (objects == null) continue;
                    for (Object name : objects.getKeys()) {
                        var object = PdfReader.getPdfObject(objects.get((PdfName) name));
                        if (object instanceof com.lowagie.text.pdf.PdfDictionary dictionary
                            && PdfName.IMAGE.equals(dictionary.getAsName(PdfName.SUBTYPE))
                            && dictionary.getAsNumber(PdfName.WIDTH).intValue() == 8) imageCount++;
                    }
                }
                assertTrue(imageCount >= 3, "Education, certification and test images must be embedded");
            } finally {
                reader.close();
            }
        } finally {
            server.stop(0);
        }
    }

    private void assertSigned(DossierDto dossier) {
        assertTrue(dossier.educations().get(0).imageUrl().contains("X-Amz-Signature="));
        assertTrue(dossier.certifications().get(0).imageUrl().contains("X-Amz-Signature="));
        assertTrue(dossier.testResults().get(0).imageUrl().contains("X-Amz-Signature="));
    }

    @Test
    void pdfDoesNotFetchArbitraryCompanyLogoUrls() throws Exception {
        var requests = new java.util.concurrent.atomic.AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            byte[] image = png(0);
            exchange.sendResponseHeaders(200, image.length);
            exchange.getResponseBody().write(image);
            exchange.close();
        });
        server.start();
        try {
            var company = mock(CompanySettingsService.class);
                when(company.get()).thenReturn(new CompanyContactDto(null, null, null, null,
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/internal-resource", 12));
            var storage = new S3ImageStorage("test-bucket", "", "cellar.example.test", "test-key", "test-secret");
            assertTrue(new PdfService(company, storage).generateDossierPdf(dossier("https://external.example.test/")).length > 0);
            assertEquals(0, requests.get(), "Untrusted logo URLs must not trigger server-side network requests");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shortDossierFlowsAcrossSectionsWithoutForcedPageBreaks() throws Exception {
        var source = dossier("https://external.example.test/");
        var reference = new com.esn.inodoc.domain.DossierReference();
        reference.setName("Reference contact");
        source.addReference(reference);
        var experience = new ExperienceCompany();
        experience.setCompany("Example company");
        experience.setStartDate(java.time.LocalDate.of(2026, 9, 1));
        experience.setEndDate(java.time.LocalDate.of(2026, 10, 31));
        var project = new ExperienceProject();
        project.setName("Short project");
        project.setTasks("One task");
        experience.addProject(project);
        source.addExperience(experience);
        var company = mock(CompanySettingsService.class);
        when(company.get()).thenReturn(new CompanyContactDto(null, null, null, null, null, 12));
        var storage = new S3ImageStorage("test-bucket", "", "cellar.example.test", "test-key", "test-secret");
        var reader = new PdfReader(new PdfService(company, storage).generateDossierPdf(source));
        try {
            assertEquals(1, reader.getNumberOfPages(), "Short sections must use the remaining page space");
            String text = new com.lowagie.text.pdf.parser.PdfTextExtractor(reader).getTextFromPage(1);
            assertTrue(text.contains("Technical test"));
            assertTrue(text.contains("Reference contact"));
            assertTrue(text.replaceAll("\\s+", " ").contains("Example company"), text);
            assertTrue(text.contains("2 mois"));
            assertTrue(text.contains("09/2026 - 10/2026"));
            assertTrue(text.contains("One task"));
            assertFalse(text.replaceAll("\\s+", "").contains("1/1"));
        } finally {
            reader.close();
        }
    }

    @Test
    void longTestDescriptionStartsOnCurrentPageAndContinuesWithoutLosingText() throws Exception {
        var source = dossier("https://external.example.test/");
        source.getTestResults().get(0).setDescription(java.util.stream.IntStream.range(0, 90)
                .mapToObj(index -> "Test detail " + index + ": preserved content")
                .collect(java.util.stream.Collectors.joining("\n")));
        var company = mock(CompanySettingsService.class);
        when(company.get()).thenReturn(new CompanyContactDto(null, null, null, null, null, 12));
        var storage = new S3ImageStorage("test-bucket", "", "cellar.example.test", "test-key", "test-secret");
        var reader = new PdfReader(new PdfService(company, storage).generateDossierPdf(source));
        try {
            assertTrue(reader.getNumberOfPages() > 1);
            var extractor = new com.lowagie.text.pdf.parser.PdfTextExtractor(reader);
            assertTrue(extractor.getTextFromPage(1).contains("Test detail 0"));
            StringBuilder text = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) text.append(extractor.getTextFromPage(page));
            for (int index = 0; index < 90; index++) assertTrue(text.toString().contains("Test detail " + index + ":"));
        } finally {
            reader.close();
        }
    }

    @Test
    void referenceLayoutKeepsA4SizeRepeatedHeadersPaginationAndAllContent() throws Exception {
        var source = dossier("https://external.example.test/");
        source.getProfile().setFirstName("Rodrigue");
        source.getProfile().setLastName("Kengoum Gassam");
        source.setHeadline("Ingénieur développeur Java / DevOps");
        source.setYearsOfExperience(9);
        source.getEducations().get(0).setSchool("Université Paris-Saclay");
        source.getEducations().get(0).setDegree("Master Informatique");
        source.getCertifications().get(0).setName("AWS Certified Cloud Practitioner");
        for (String[] values : List.of(
                new String[]{"Langages", "Java, TypeScript, Python, SQL"},
                new String[]{"Back-end", "Spring Boot, Hibernate, REST, Maven, JUnit"},
                new String[]{"Frameworks", "Angular, React, RxJS"},
                new String[]{"Base de données", "PostgreSQL, MongoDB, Redis"},
                new String[]{"DevOps", "Docker, Kubernetes, GitLab CI, Terraform"},
                new String[]{"Cloud", "AWS : Lambda, S3, RDS, EC2, CloudWatch"})) {
            var group = new SkillGroup();
            group.setCategory(values[0]);
            group.setContent(values[1]);
            source.addSkillGroup(group);
        }
        var language = new LanguageSkill();
        language.setLanguage("Anglais");
        language.setLevel("Courant");
        source.addLanguage(language);
        for (int index = 0; index < 3; index++) {
            var experience = new ExperienceCompany();
            experience.setCompany("Entreprise " + index);
            experience.setStartDate(java.time.LocalDate.of(2021 + index, 3, 1));
            experience.setRoleTitle("Développeur Java approche DevOps");
            var project = new ExperienceProject();
            project.setName("Plateforme métier " + index);
            project.setContext("Développement des services et accompagnement des équipes techniques.");
                int missionIndex = index;
            project.setTasks(java.util.stream.IntStream.range(0, 25).mapToObj(task ->
                    "Mission " + missionIndex + " tâche " + task + " : conception, développement et validation des API Java.")
                    .collect(java.util.stream.Collectors.joining("\n")));
            project.setTechnologies("Java, Spring Boot, PostgreSQL, Docker, Kubernetes");
            experience.addProject(project);
            source.addExperience(experience);
        }
        var company = mock(CompanySettingsService.class);
        when(company.get()).thenReturn(new CompanyContactDto("6 rue du 8 mai 1945, 75010 Paris",
            "09 67 18 38 10", "contact@inoco.fr", null, null, 12));
        var storage = new S3ImageStorage("test-bucket", "", "cellar.example.test", "test-key", "test-secret");
        byte[] pdf = new PdfService(company, storage).generateDossierPdf(source);
        var reader = new PdfReader(pdf);
        try {
            assertEquals(595f, reader.getPageSize(1).getWidth());
            assertEquals(842f, reader.getPageSize(1).getHeight());
            assertTrue(reader.getNumberOfPages() > 1);
            int embeddedImages = 0;
            int firstPageImages = 0;
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                var resources = reader.getPageN(page).getAsDict(PdfName.RESOURCES);
                var objects = resources.getAsDict(PdfName.XOBJECT);
                if (objects == null) continue;
                for (Object name : objects.getKeys()) {
                    var object = PdfReader.getPdfObject(objects.get((PdfName) name));
                    if (object instanceof com.lowagie.text.pdf.PdfDictionary dictionary
                            && PdfName.IMAGE.equals(dictionary.getAsName(PdfName.SUBTYPE))) {
                        embeddedImages++;
                        if (page == 1) firstPageImages++;
                    }
                }
            }
            assertTrue(embeddedImages >= 4);
            assertTrue(firstPageImages >= 3, "Reference section labels and divider must be embedded on page one");
            var extractor = new com.lowagie.text.pdf.parser.PdfTextExtractor(reader);
            StringBuilder allText = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                String text = extractor.getTextFromPage(page);
                assertTrue(text.contains("contact@inoco.fr"), "Header must repeat on page " + page);
                assertFalse(text.replaceAll("\\s+", "").contains(page + "/" + reader.getNumberOfPages()),
                    "The reference layout has no page number on page " + page);
                String body = text.replaceAll("\\s+", " ")
                    .replace("6 rue du 8 mai 1945, 75010 Paris", "")
                    .replace("Tél : 09 67 18 38 10", "")
                    .replace("Mail : contact@inoco.fr", "")
                    .replace(page + "/" + reader.getNumberOfPages(), "").trim();
                assertFalse(body.isBlank(), "Page " + page + " must contain more than its header and footer");
                allText.append(text);
            }
            assertTrue(allText.toString().contains("RKG"));
            assertFalse(allText.toString().contains("Kengoum Gassam"));
            assertTrue(allText.toString().contains("Mission 2 tâche 24"));
        } finally {
            reader.close();
        }
        java.nio.file.Files.write(java.nio.file.Path.of(System.getProperty("basedir"), "target",
                "reference-dossier-preview.pdf"), pdf);
    }

    private CompetenceDossier dossier(String imageBase) {
        var profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setFirstName("Camille");
        profile.setLastName("Test");
        var dossier = new CompetenceDossier();
        dossier.setId(UUID.randomUUID());
        dossier.setLabel("Image test");
        profile.addDossier(dossier);
        var education = new Education();
        education.setSchool("School");
        education.setImageUrl(imageBase + "education.png");
        dossier.addEducation(education);
        var certification = new Certification();
        certification.setName("Certification");
        certification.setImageUrl(imageBase + "certification.png");
        dossier.addCertification(certification);
        var test = new TestResult();
        test.setName("Technical test");
        test.setImageUrl(imageBase + "test.png");
        dossier.addTestResult(test);
        return dossier;
    }

    private byte[] png(int color) throws java.io.IOException {
        var bitmap = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        for (int row = 0; row < 8; row++) {
            for (int column = 0; column < 8; column++) bitmap.setRGB(column, row, color);
        }
        var output = new ByteArrayOutputStream();
        ImageIO.write(bitmap, "png", output);
        return output.toByteArray();
    }
}