package com.esn.inodoc.config;

import com.esn.inodoc.domain.AvailabilityStatus;
import com.esn.inodoc.domain.Certification;
import com.esn.inodoc.domain.CompanySettings;
import com.esn.inodoc.domain.CompetenceDossier;
import com.esn.inodoc.domain.DossierReference;
import com.esn.inodoc.domain.Education;
import com.esn.inodoc.domain.ExperienceCompany;
import com.esn.inodoc.domain.ExperienceProject;
import com.esn.inodoc.domain.LanguageSkill;
import com.esn.inodoc.domain.Profile;
import com.esn.inodoc.domain.ProfileStatus;
import com.esn.inodoc.domain.Role;
import com.esn.inodoc.domain.RoleName;
import com.esn.inodoc.domain.SkillCategory;
import com.esn.inodoc.domain.SkillGroup;
import com.esn.inodoc.domain.TechSkill;
import com.esn.inodoc.domain.TestResult;
import com.esn.inodoc.domain.User;
import com.esn.inodoc.repo.ProfileRepository;
import com.esn.inodoc.repo.CompanySettingsRepository;
import com.esn.inodoc.repo.RoleRepository;
import com.esn.inodoc.repo.SkillCategoryRepository;
import com.esn.inodoc.repo.TechSkillRepository;
import com.esn.inodoc.repo.UserRepository;
import com.esn.inodoc.service.ProvisioningService;
import com.esn.inodoc.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * On startup, provisions the reference data (roles, skill
 * catalog, company settings) and the administrator account (from the
 * configured credentials).
 *
 * <p>When demo seeding is enabled ({@code inodoc.demo.enabled=true}) the
 * database is cleared and a fictional external profile
 * with two dossier versions is created — no other user besides the administrator.
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ProfileRepository profileRepository;
    private final SkillCategoryRepository skillCategoryRepository;
    private final TechSkillRepository techSkillRepository;
    private final CompanySettingsRepository companySettingsRepository;
    private final ProvisioningService provisioningService;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;

    public DataInitializer(UserRepository userRepository,
                           RoleRepository roleRepository,
                           ProfileRepository profileRepository,
                           SkillCategoryRepository skillCategoryRepository,
                           TechSkillRepository techSkillRepository,
                           CompanySettingsRepository companySettingsRepository,
                           ProvisioningService provisioningService,
                           PasswordEncoder passwordEncoder,
                           AppProperties appProperties) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.profileRepository = profileRepository;
        this.skillCategoryRepository = skillCategoryRepository;
        this.techSkillRepository = techSkillRepository;
        this.companySettingsRepository = companySettingsRepository;
        this.provisioningService = provisioningService;
        this.passwordEncoder = passwordEncoder;
        this.appProperties = appProperties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (appProperties.getDemo().isEnabled()) {
            userRepository.deleteAll();
            profileRepository.deleteAll();
        }
        // Create missing role rows on every startup, including roles added in later releases.
        if (roleRepository.count() == 0) {
            seedReferenceData();
        } else {
            provisioningService.createDefaultRoles();
        }
        // Create admin account from environment variables
        ensureAdminAccount();
        // Demo data: a fully populated competence dossier for visual preview.
        if (appProperties.getDemo().isEnabled()) {
            seedDemoDossier();
        }
    }

    /**
     * Provisions missing roles and initializes reference catalogs when empty.
     */
    private void seedReferenceData() {
        provisioningService.createDefaultRoles();
        if (skillCategoryRepository.count() == 0) {
            seedSkillCategories();
        }
        if (techSkillRepository.count() == 0) {
            seedTechSkills();
        }
        if (companySettingsRepository.count() == 0) {
            seedCompanySettings();
        }
        log.info("Reference data initialized (roles, skills, company settings)");
    }

    /**
     * Creates the bootstrap administrator from the configured credentials if it
     * does not exist yet. Skipped when no password is provided.
     *
     * Environment variables:
     *   INODOC_ADMIN_EMAIL - Administrator email (required)
     *   INODOC_ADMIN_PASSWORD - Administrator password (required, min 8 characters)
     */
    private void ensureAdminAccount() {
        AppProperties.Admin admin = appProperties.getAdmin();
        String email = admin.getEmail() == null ? "" : admin.getEmail().toLowerCase().trim();
        String password = admin.getPassword() == null ? "" : admin.getPassword();

        if (email.isBlank() || password.isBlank()) {
            log.warn("Admin account not initialized (INODOC_ADMIN_EMAIL and INODOC_ADMIN_PASSWORD environment variables are missing or empty)");
            return;
        }

        if (userRepository.existsByEmailIgnoreCase(email)) {
            log.info("Admin account already exists: {}", email);
            return;
        }

        try {
            createUser(email, password, admin.getFirstName(), admin.getLastName(),
                    Set.of(roleRepository.findByName(RoleName.ADMIN)
                            .orElseThrow(() -> new IllegalStateException("ADMIN role not found"))));
            log.info("Admin account created successfully");
        } catch (Exception e) {
            log.error("Failed to create admin account", e);
            throw new RuntimeException("Failed to create admin account", e);
        }
    }

    private void seedSkillCategories() {
        List.of("Langages", "Back-end", "Frameworks", "Base de données", "DevOps", "Cloud",
                        "Qualité de code", "Méthodologie", "Outils")
                .forEach(name -> {
                    SkillCategory category = new SkillCategory();
                    category.setName(name);
                    skillCategoryRepository.save(category);
                });
    }

    private void seedTechSkills() {
        Map<String, List<String>> catalog = new LinkedHashMap<>();
        catalog.put("Langages", List.of("Java", "Kotlin", "Python", "TypeScript", "JavaScript",
                "C#", "Go", "Rust", "PHP", "Ruby", "Scala", "SQL", "Bash", "C++"));
        catalog.put("Back-end", List.of("Spring Boot", "Spring Security", "Hibernate", "JPA", "Quarkus",
                "Micronaut", ".NET", "Node.js", "Express", "NestJS", "Django", "FastAPI", "Flask", "Laravel"));
        catalog.put("Frameworks", List.of("React", "Angular", "Vue.js", "Next.js", "Svelte", "Nuxt",
                "Tailwind CSS", "Bootstrap"));
        catalog.put("Base de données", List.of("PostgreSQL", "MySQL", "MariaDB", "MongoDB", "Redis",
                "Oracle", "SQL Server", "Elasticsearch", "Cassandra", "DynamoDB"));
        catalog.put("DevOps", List.of("Docker", "Kubernetes", "Jenkins", "GitLab CI", "GitHub Actions",
                "Terraform", "Ansible", "Helm", "ArgoCD", "Prometheus", "Grafana"));
        catalog.put("Cloud", List.of("AWS", "Google Cloud Platform", "Microsoft Azure", "OVHcloud",
                "Heroku", "DigitalOcean"));
        catalog.put("Qualité de code", List.of("JUnit", "Mockito", "SonarQube", "Jest", "Cypress",
                "Playwright", "Selenium", "TestContainers"));
        catalog.put("Méthodologie", List.of("Agile Scrum", "Kanban", "SAFe", "TDD", "DDD", "BDD",
                "Clean Architecture", "Hexagonal Architecture"));
        catalog.put("Outils", List.of("Git", "Maven", "Gradle", "npm", "Jira", "Confluence",
                "IntelliJ IDEA", "VS Code", "Postman", "Swagger"));

        catalog.forEach((category, names) -> names.forEach(name -> {
            TechSkill skill = new TechSkill();
            skill.setCategory(category);
            skill.setName(name);
            techSkillRepository.save(skill);
        }));
    }

    private void createUser(String email, String password, String firstName,
                            String lastName, Set<Role> roles) {
        createUser(email, password, firstName, lastName, roles, null);
    }

    private void createUser(String email, String password, String firstName,
                            String lastName, Set<Role> roles, Profile profile) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEnabled(true);
        user.setRoles(new HashSet<>(roles));
        if (profile != null) {
            user.setProfile(profile);
        }
        userRepository.save(user);
    }

    /**
     * Seeds a single, fully populated competence dossier for visual preview
    * (profile identity/HR data + master dossier and a comparison version).
     * Runs only when {@code inodoc.demo.enabled=true}. The whole graph is
     * persisted in one save thanks to the cascade on {@link Profile#getDossiers()}.
     */
    private void seedDemoDossier() {
        Profile profile = new Profile();
        profile.setFirstName("Alex");
        profile.setLastName("Exemple");
        profile.setEmail("alex.exemple@example.com");
        profile.setPhone(null);
        profile.setAddress(null);
        profile.setLocation("Paris / Île-de-France");
        profile.setStatus(ProfileStatus.EXTERNAL);
        profile.setAvailabilityStatus(AvailabilityStatus.AVAILABLE_SOON);
        profile.setAvailableFrom(LocalDate.now().plusMonths(1));
        profile.setDailyRate(550);

        CompetenceDossier dossier = new CompetenceDossier();
        dossier.setLabel("Dossier de compétences — Version complète");
        dossier.setMaster(true);
        dossier.setHeadline("Ingénieur Full Stack Java / Angular — Lead technique");
        dossier.setYearsOfExperience(9);
        dossier.setExpertiseDomain("Développement web, Cloud & DevOps");
        dossier.setSummary("Ingénieur logiciel avec 9 ans d'expérience dans la conception et la "
                + "réalisation d'applications web à forte volumétrie, du cadrage produit jusqu'au "
                + "déploiement en production. Spécialisé en architecture microservices (Java/Spring "
                + "Boot) et en développement front-end (Angular), j'accompagne les équipes sur la "
                + "qualité de code, la CI/CD et les bonnes pratiques Cloud. Reconnu pour mon "
                + "leadership technique, ma rigueur et ma capacité à vulgariser les sujets complexes.");

        dossier.addSkillGroup(skillGroup("Langages", "Java 17, Kotlin, TypeScript, JavaScript, Python, SQL", 0));
        dossier.addSkillGroup(skillGroup("Back-end", "Spring Boot, Spring Security, Hibernate / JPA, Node.js, REST, GraphQL", 1));
        dossier.addSkillGroup(skillGroup("Front-end", "Angular, React, RxJS, NgRx, HTML5, SCSS, Tailwind CSS", 2));
        dossier.addSkillGroup(skillGroup("Bases de données", "PostgreSQL, MySQL, MongoDB, Redis, Elasticsearch", 3));
        dossier.addSkillGroup(skillGroup("DevOps & Cloud", "Docker, Kubernetes, AWS (EKS, S3, RDS), Terraform, GitLab CI/CD", 4));
        dossier.addSkillGroup(skillGroup("Méthodologie", "Agile Scrum, TDD, DDD, Clean Architecture, Code Review", 5));

        ExperienceCompany bnp = experience("BNP Paribas", LocalDate.of(2021, 3, 1), null,
                "Lead développeur", "Refonte de la plateforme de paiement instantané à l'échelle européenne.", 0);
        bnp.addProject(project("Plateforme de paiement instantané (SEPA Instant)",
                "Migration d'un monolithe vers une architecture microservices event-driven.",
                "Équipe de 9 personnes (6 devs, 1 PO, 1 Scrum Master, 1 QA)",
                "Scrum, sprints de 2 semaines",
                "Concevoir et développer les API REST de paiement\n"
                        + "Mettre en place l'architecture microservices (Spring Boot, Kafka)\n"
                        + "Encadrer techniquement 3 développeurs\n"
                        + "Industrialiser la CI/CD GitLab et le déploiement Kubernetes\n"
                        + "Garantir une couverture de tests supérieure à 85 %",
                "Java 17, Spring Boot, Kafka, PostgreSQL, Docker, Kubernetes, AWS", 0));

        ExperienceCompany sg = experience("Société Générale", LocalDate.of(2018, 9, 1), LocalDate.of(2021, 2, 1),
                "Développeur Full Stack senior", "Développement d'applications internes pour les équipes de trading.", 1);
        sg.addProject(project("Portail de suivi des risques",
                "Application de visualisation en temps réel des positions de marché.",
                "Équipe de 6 personnes", "Kanban",
                "Développer le front Angular (dashboards temps réel)\n"
                        + "Concevoir les API de reporting\n"
                        + "Optimiser les performances des requêtes SQL",
                "Angular, Java, Spring Boot, Oracle, WebSocket", 0));

        ExperienceCompany cap = experience("Capgemini", LocalDate.of(2015, 6, 1), LocalDate.of(2018, 8, 1),
                "Développeur Java", "Missions de développement chez divers clients grands comptes.", 2);
        cap.addProject(project("Refonte du SI assurance",
                "Modernisation d'un système de gestion de contrats d'assurance.",
                "Équipe de 12 personnes", "SAFe",
                "Développer les modules de gestion de contrats\n"
                        + "Migrer les batchs legacy vers Spring Batch",
                "Java, Spring, Hibernate, MySQL", 0));

        dossier.addExperience(bnp);
        dossier.addExperience(sg);
        dossier.addExperience(cap);

        dossier.addEducation(education("Université Paris-Saclay",
                "Master 2 Informatique — Systèmes distribués", "2016", 0));
        dossier.addEducation(education("École Polytechnique de Yaoundé",
                "Diplôme d'Ingénieur en Génie Logiciel", "2015", 1));

        dossier.addCertification(certification("AWS Certified Solutions Architect – Associate",
                "Amazon Web Services", "2022", 0));
        dossier.addCertification(certification("Professional Scrum Master I (PSM I)",
                "Scrum.org", "2020", 1));
        dossier.addCertification(certification("Oracle Certified Professional, Java SE 11",
                "Oracle", "2019", 2));

        dossier.addLanguage(language("Français", "Natif", 0));
        dossier.addLanguage(language("Anglais", "Courant (C1)", 1));
        dossier.addLanguage(language("Espagnol", "Intermédiaire (B1)", 2));

        dossier.addReference(reference("Jean Dupont", "jean.dupont@example.com", null,
                "Manager technique ayant encadré la mission BNP Paribas. Disponible sur demande.", 0));
        dossier.addReference(reference("Marie Laurent", "marie.laurent@example.com", null,
                "Responsable d'équipe à la Société Générale.", 1));

        dossier.addTestResult(testResult("TestGorilla — Java (Coding)", "92 / 100",
                "Test technique Java avancé : algorithmique, concurrence et qualité de code.", 0));
        dossier.addTestResult(testResult("Codingame — Certification JavaScript", "Niveau Expert",
                "Évaluation front-end et algorithmique.", 1));

        profile.addDossier(dossier);
        profile.addDossier(demoVariation(dossier));
        profileRepository.save(profile);
        log.info("Demo competence dossier seeded: {} {}", profile.getFirstName(), profile.getLastName());
    }

        private CompetenceDossier demoVariation(CompetenceDossier source) {
        CompetenceDossier version = new CompetenceDossier();
        version.setLabel("Dossier de compétences - Version mission Cloud");
        version.setMaster(false);
        version.setHeadline("Architecte Java / Cloud - Référent technique");
        version.setYearsOfExperience(10);
        version.setExpertiseDomain("Architecture distribuée, Cloud & DevOps");
        version.setSummary(source.getSummary() + " Je pilote également les choix d'architecture Cloud "
            + "et l'accompagnement des équipes sur Kubernetes et Terraform.");
        source.getSkillGroups().forEach(group -> version.addSkillGroup(
            skillGroup(group.getCategory(), group.getContent(), group.getPosition())));
        version.getSkillGroups().get(4).setContent(
            "Docker, Kubernetes, AWS (EKS, S3, RDS), Terraform, GitLab CI/CD, ArgoCD, Prometheus");
        source.getExperiences().forEach(original -> {
            ExperienceCompany copy = experience(original.getCompany(), original.getStartDate(), original.getEndDate(),
                original.getRoleTitle(), original.getContext(), original.getPosition());
            original.getProjects().forEach(item -> copy.addProject(project(item.getName(), item.getContext(),
                item.getTeam(), item.getMethodology(), item.getTasks(), item.getTechnologies(), item.getPosition())));
            version.addExperience(copy);
        });
        version.getExperiences().get(0).setRoleTitle("Architecte et lead technique");
        version.getExperiences().get(0).getProjects().get(0).setTasks(
            source.getExperiences().get(0).getProjects().get(0).getTasks()
                + "\nDéfinir la stratégie d'observabilité et les déploiements GitOps");
        source.getEducations().forEach(item -> version.addEducation(
            education(item.getSchool(), item.getDegree(), item.getYear(), item.getPosition())));
        source.getCertifications().forEach(item -> version.addCertification(
            certification(item.getName(), item.getIssuer(), item.getYear(), item.getPosition())));
        source.getLanguages().forEach(item -> version.addLanguage(
            language(item.getLanguage(), item.getLevel(), item.getPosition())));
        version.getLanguages().get(1).setLevel("Bilingue (C2)");
        source.getReferences().forEach(item -> version.addReference(
            reference(item.getName(), item.getEmail(), item.getPhone(), item.getDescription(), item.getPosition())));
        source.getTestResults().forEach(item -> version.addTestResult(
            testResult(item.getName(), item.getScore(), item.getDescription(), item.getPosition())));
        return version;
        }

    private SkillGroup skillGroup(String category, String content, int position) {
        SkillGroup group = new SkillGroup();
        group.setCategory(category);
        group.setContent(content);
        group.setPosition(position);
        return group;
    }

    private ExperienceCompany experience(String company, LocalDate start, LocalDate end,
                                         String roleTitle, String context, int position) {
        ExperienceCompany experience = new ExperienceCompany();
        experience.setCompany(company);
        experience.setStartDate(start);
        experience.setEndDate(end);
        experience.setRoleTitle(roleTitle);
        experience.setContext(context);
        experience.setPosition(position);
        return experience;
    }

    private ExperienceProject project(String name, String context, String team, String methodology,
                                      String tasks, String technologies, int position) {
        ExperienceProject project = new ExperienceProject();
        project.setName(name);
        project.setContext(context);
        project.setTeam(team);
        project.setMethodology(methodology);
        project.setTasks(tasks);
        project.setTechnologies(technologies);
        project.setPosition(position);
        return project;
    }

    private Education education(String school, String degree, String year, int position) {
        Education education = new Education();
        education.setSchool(school);
        education.setDegree(degree);
        education.setYear(year);
        education.setPosition(position);
        return education;
    }

    private Certification certification(String name, String issuer, String year, int position) {
        Certification certification = new Certification();
        certification.setName(name);
        certification.setIssuer(issuer);
        certification.setYear(year);
        certification.setPosition(position);
        return certification;
    }

    private LanguageSkill language(String language, String level, int position) {
        LanguageSkill skill = new LanguageSkill();
        skill.setLanguage(language);
        skill.setLevel(level);
        skill.setPosition(position);
        return skill;
    }

    private DossierReference reference(String name, String email, String phone, String description, int position) {
        DossierReference reference = new DossierReference();
        reference.setName(name);
        reference.setEmail(email);
        reference.setPhone(phone);
        reference.setDescription(description);
        reference.setPosition(position);
        return reference;
    }

    private TestResult testResult(String name, String score, String description, int position) {
        TestResult result = new TestResult();
        result.setName(name);
        result.setScore(score);
        result.setDescription(description);
        result.setPosition(position);
        return result;
    }

    private void seedCompanySettings() { // TODO: Load from application.properties or environment variables instead of hardcoding
        CompanySettings settings = new CompanySettings();
        companySettingsRepository.save(settings);
        log.debug("Company settings initialized");
    }
}
