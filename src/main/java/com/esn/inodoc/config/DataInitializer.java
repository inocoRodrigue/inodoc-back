package com.esn.inodoc.config;

import com.esn.inodoc.domain.CompanySettings;
import com.esn.inodoc.domain.Role;
import com.esn.inodoc.domain.RoleName;
import com.esn.inodoc.domain.SkillCategory;
import com.esn.inodoc.domain.TechSkill;
import com.esn.inodoc.domain.User;
import com.esn.inodoc.repo.CompanySettingsRepository;
import com.esn.inodoc.repo.RoleRepository;
import com.esn.inodoc.repo.SkillCategoryRepository;
import com.esn.inodoc.repo.TechSkillRepository;
import com.esn.inodoc.repo.UserRepository;
import com.esn.inodoc.service.ProvisioningService;
import com.esn.inodoc.service.cra.CraProjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final SkillCategoryRepository skillCategoryRepository;
    private final TechSkillRepository techSkillRepository;
    private final CompanySettingsRepository companySettingsRepository;
    private final ProvisioningService provisioningService;
    private final CraProjectService craProjectService;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;
    private final JdbcTemplate jdbc;
    private final CareerDataInitializer careerDataInitializer;

    public DataInitializer(UserRepository userRepository,
                           RoleRepository roleRepository,
                           SkillCategoryRepository skillCategoryRepository,
                           TechSkillRepository techSkillRepository,
                           CompanySettingsRepository companySettingsRepository,
                           ProvisioningService provisioningService,
                           CraProjectService craProjectService,
                           PasswordEncoder passwordEncoder,
                           AppProperties appProperties,
                           JdbcTemplate jdbc,
                           CareerDataInitializer careerDataInitializer) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.skillCategoryRepository = skillCategoryRepository;
        this.techSkillRepository = techSkillRepository;
        this.companySettingsRepository = companySettingsRepository;
        this.provisioningService = provisioningService;
        this.craProjectService = craProjectService;
        this.passwordEncoder = passwordEncoder;
        this.appProperties = appProperties;
        this.jdbc = jdbc;
        this.careerDataInitializer = careerDataInitializer;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (isDatabaseEmpty()) initializeAfterReset();
        initializeAdminAccount();
    }

    private boolean isDatabaseEmpty() {
        return Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection -> {
            if (connection.getMetaData().getDatabaseProductName().equals("PostgreSQL")) {
                try (var statement = connection.createStatement()) {
                    statement.execute("SELECT pg_advisory_xact_lock(1768845123)");
                }
            }
            try (var tables = connection.getMetaData().getTables(null, connection.getSchema(), "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    String name = tables.getString("TABLE_NAME");
                    if (name.equalsIgnoreCase("flyway_schema_history")) continue;
                    try (var statement = connection.createStatement()) {
                        statement.setMaxRows(1);
                        try (var rows = statement.executeQuery("SELECT 1 FROM \"" + name.replace("\"", "\"\"") + "\"")) {
                            if (rows.next()) return false;
                        }
                    }
                }
            }
            return true;
        }));
    }

    private void initializeReferenceData() {
        provisioningService.createDefaultRoles();
        if (skillCategoryRepository.count() == 0) {
            initializeSkillCategories();
        }
        if (techSkillRepository.count() == 0) {
            initializeTechSkills();
        }
        if (companySettingsRepository.count() == 0) {
            companySettingsRepository.save(new CompanySettings());
        }
        log.info("Reference data initialized (roles, skills, company settings)");
    }

    @Transactional
    public void initializeAfterReset() {
        initializeReferenceData();
        craProjectService.ensureDefaultProjects();
        careerDataInitializer.initialize();
        roleRepository.flush();
    }

    /**
        * Creates the configured bootstrap administrator on startup only when absent.
     *
     * Environment variables:
     *   INODOC_ADMIN_EMAIL - Administrator email (required)
     *   INODOC_ADMIN_PASSWORD - Administrator password (required, min 8 characters)
     */
    private void initializeAdminAccount() {
        AppProperties.Admin admin = appProperties.getAdmin();
        String email = admin.getEmail() == null ? "" : admin.getEmail().toLowerCase().trim();
        if (!email.isBlank() && userRepository.existsByEmailIgnoreCase(email)) {
            log.info("Cet admin exist déjà!");
            return;
        }
        String password = admin.getPassword() == null ? "" : admin.getPassword();

        if (email.isBlank() || password.length() < 8) {
            throw new IllegalStateException("Initialisation impossible : email administrateur et mot de passe d'au moins 8 caracteres requis");
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFirstName(admin.getFirstName());
        user.setLastName(admin.getLastName());
        user.setEnabled(true);
        user.setRoles(new HashSet<>(Set.of(roleRepository.findByName(RoleName.ADMIN)
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setName(RoleName.ADMIN);
                    role.setDescription("Administrateur");
                    return roleRepository.save(role);
                }))));
        userRepository.save(user);
        log.info("Admin account initialized");
    }

    private void initializeSkillCategories() {
        List.of("Langages", "Back-end", "Frameworks", "Base de données", "DevOps", "Cloud",
                        "Qualité de code", "Méthodologie", "Outils")
                .forEach(name -> {
                    SkillCategory category = new SkillCategory();
                    category.setName(name);
                    skillCategoryRepository.save(category);
                });
    }

    private void initializeTechSkills() {
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

}
