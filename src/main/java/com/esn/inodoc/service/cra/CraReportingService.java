package com.esn.inodoc.service.cra;

import com.esn.inodoc.controller.error.*;
import com.esn.inodoc.domain.*;
import com.esn.inodoc.domain.cra.*;
import com.esn.inodoc.dto.cra.CraOperationsDto;
import com.esn.inodoc.repo.*;
import com.esn.inodoc.repo.cra.*;
import com.esn.inodoc.service.*;
import com.esn.inodoc.utils.SecurityUtils;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CraReportingService {
    private final UserRepository users;
    private final CraDeclarationRepository declarations;
    private final CraReminderRepository reminders;
    private final MailAccountRepository accounts;
    private final MailAccountService mail;
    private final EmailService emails;
    private final CraRulesService rules;
    private final CraAuditService audit;
    private final CraFeatureService feature;
    public CraReportingService(UserRepository users, CraDeclarationRepository declarations, CraReminderRepository reminders,
                               MailAccountRepository accounts, MailAccountService mail, EmailService emails,
                               CraRulesService rules, CraAuditService audit, CraFeatureService feature) {
        this.users = users; this.declarations = declarations; this.reminders = reminders; this.accounts = accounts;
        this.mail = mail; this.emails = emails; this.rules = rules; this.audit = audit; this.feature = feature;
    }
    public static Specification<User> criteria(YearMonth month, String state, String search) {
        if (!Set.of("", "MISSING", "PENDING", "DECLARED", "VALIDATED").contains(state)) throw new BadRequestException("État invalide");
        return (root, query, builder) -> {
            var conditions = new ArrayList<jakarta.persistence.criteria.Predicate>();
            conditions.add(builder.isTrue(root.get("enabled")));
            var eligible = query.subquery(Integer.class);
            var account = eligible.from(User.class);
            eligible.select(builder.literal(1)).where(builder.equal(account.get("id"), root.get("id")), account.join("roles").get("name").in(List.of(RoleName.values())));
            conditions.add(builder.exists(eligible));
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                var name = builder.concat(builder.concat(root.get("firstName"), " "), root.get("lastName"));
                conditions.add(builder.or(builder.like(builder.lower(name), pattern, '\\'), builder.like(builder.lower(root.get("email")), pattern, '\\')));
            }
            if (!state.isBlank()) {
                var match = query.subquery(Integer.class); var declaration = match.from(CraDeclaration.class);
                var conditionsForDeclaration = new ArrayList<jakarta.persistence.criteria.Predicate>();
                conditionsForDeclaration.add(builder.equal(declaration.get("collaborator").get("id"), root.get("id")));
                conditionsForDeclaration.add(builder.equal(declaration.get("year"), month.getYear()));
                conditionsForDeclaration.add(builder.equal(declaration.get("month"), month.getMonthValue()));
                if (!state.equals("MISSING")) conditionsForDeclaration.add(builder.equal(declaration.get("status"), CraDeclarationStatus.valueOf(state)));
                match.select(builder.literal(1)).where(conditionsForDeclaration.toArray(jakarta.persistence.criteria.Predicate[]::new));
                conditions.add(state.equals("MISSING") ? builder.not(builder.exists(match)) : builder.exists(match));
            }
            return builder.and(conditions.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
    @Transactional(readOnly = true)
    public Page<CraOperationsDto.Monitor> monitor(int year, int month, String state, String search, int page, int size) {
        var period = rules.period(year, month);
        if (period.isAfter(YearMonth.now()) || page < 0 || size < 1 || size > 100) throw new BadRequestException("Période ou pagination invalide");
        var result = users.findAll(criteria(period, state, search), PageRequest.of(page, size, Sort.by("lastName", "firstName", "id")));
        if (result.isEmpty()) return result.map(user -> row(user, period, null));
        var entries = declarations.findMonthlyForUsers(year, month, result.getContent().stream().map(User::getId).toList()).stream()
                .collect(Collectors.toMap(item -> item.getCollaborator().getId(), Function.identity()));
        return result.map(user -> row(user, period, entries.get(user.getId())));
    }
    @Transactional(readOnly = true)
    public CraOperationsDto.Statistics statistics(int year, int month) {
        var period = rules.period(year, month);
        if (period.isAfter(YearMonth.now())) throw new BadRequestException("Les statistiques ne sont pas disponibles pour un mois futur");
        var statusCounts = new LinkedHashMap<String, Long>();
        for (String status : List.of("MISSING", "INCOMPLETE", "PENDING", "DECLARED", "VALIDATED", "NOT_EXPECTED"))
            statusCounts.put(status, 0L);
        Map<LocalDate, Double> dailyUnits = new TreeMap<>();
        Map<String, Double> clientRevenue = new HashMap<>();
        Map<String, Double> projectRevenue = new HashMap<>();
        long collaborators = 0, declared = 0;
        double expected = 0, assigned = 0, missing = 0, validatedAmount = 0;
        for (int pageIndex = 0; ; pageIndex++) {
            var page = users.findAll(criteria(period, "", ""), PageRequest.of(pageIndex, 100, Sort.by("lastName", "firstName", "id")));
            if (page.isEmpty()) break;
            var monthly = declarations.findMonthlyForUsers(year, month, page.getContent().stream().map(User::getId).toList()).stream()
                    .collect(Collectors.toMap(item -> item.getCollaborator().getId(), Function.identity()));
            for (User user : page.getContent()) {
                CraDeclaration declaration = monthly.get(user.getId());
                var monitor = row(user, period, declaration);
                collaborators++;
                statusCounts.compute(monitor.status(), (key, count) -> count == null ? 1L : count + 1);
                if (declaration != null) declared++;
                expected += monitor.expectedUnits(); assigned += monitor.assignedUnits(); missing += monitor.missingUnits();
                for (CraDayEntry entry : rules.effectiveEntries(declaration == null ? emptyDeclaration(user, period) : declaration)) {
                    double units = entry.getDayPart().units();
                    dailyUnits.merge(entry.getWorkDate(), units, Double::sum);
                    if (declaration == null || declaration.getStatus() != CraDeclarationStatus.VALIDATED
                            || user.getProfile() == null || user.getProfile().getDailyRate() == null) continue;
                    double dailyRate = user.getProfile().getDailyRate();
                    if (entry.getDayPart() != CraDayPart.AFTERNOON)
                        validatedAmount += addRevenue(entry.getProject(), 0.5, dailyRate, clientRevenue, projectRevenue);
                    if (entry.getDayPart() != CraDayPart.MORNING) {
                        CraProject afternoon = entry.getDayPart() == CraDayPart.FULL_DAY && entry.getAfternoonProject() != null
                                ? entry.getAfternoonProject() : entry.getProject();
                        validatedAmount += addRevenue(afternoon, 0.5, dailyRate, clientRevenue, projectRevenue);
                    }
                }
            }
            if (page.isLast()) break;
        }
        List<CraOperationsDto.StatisticMetric> statusMetrics = statusCounts.entrySet().stream()
                .map(item -> new CraOperationsDto.StatisticMetric(item.getKey(), item.getKey(), item.getValue(), 0))
                .toList();
        List<CraOperationsDto.StatisticMetric> activity = dailyUnits.entrySet().stream()
                .map(item -> new CraOperationsDto.StatisticMetric(item.getKey().toString(), item.getKey().toString(), item.getValue(), 0))
                .toList();
        double completion = expected == 0 ? 0 : Math.max(0, Math.min(100, (expected - missing) * 100 / expected));
        return new CraOperationsDto.Statistics(year, month, collaborators, declared, expected, assigned, missing,
                completion, validatedAmount, statusMetrics, activity, rankedRevenue(clientRevenue), rankedRevenue(projectRevenue));
    }
    private CraDeclaration emptyDeclaration(User user, YearMonth period) {
        var declaration = new CraDeclaration(); declaration.setCollaborator(user);
        declaration.setYear(period.getYear()); declaration.setMonth(period.getMonthValue());
        return declaration;
    }
    private double addRevenue(CraProject project, double units, double dailyRate, Map<String, Double> clients,
                             Map<String, Double> projectsByName) {
        double amount = units * dailyRate;
        String client = project.getClientName() == null || project.getClientName().isBlank()
                ? "Client non renseigné" : project.getClientName().trim();
        clients.merge(client, amount, Double::sum);
        projectsByName.merge(project.getName(), amount, Double::sum);
        return amount;
    }
    private List<CraOperationsDto.StatisticMetric> rankedRevenue(Map<String, Double> revenue) {
        return revenue.entrySet().stream().sorted(Map.Entry.<String, Double>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey())).limit(8)
                .map(item -> new CraOperationsDto.StatisticMetric(item.getKey(), item.getKey(), 0, item.getValue())).toList();
    }
    private CraOperationsDto.Monitor row(User user, YearMonth period, CraDeclaration declaration) {
        var calendar = rules.calendar(user.getId(), period);
        var effectiveDeclaration = declaration;
        if (effectiveDeclaration == null) {
            effectiveDeclaration = new CraDeclaration(); effectiveDeclaration.setCollaborator(user);
            effectiveDeclaration.setYear(period.getYear()); effectiveDeclaration.setMonth(period.getMonthValue());
        }
        Map<LocalDate, CraDayEntry> entries = rules.effectiveEntries(effectiveDeclaration).stream()
                .collect(Collectors.toMap(CraDayEntry::getWorkDate, Function.identity()));
        double missing = 0;
        for (var day : calendar.days()) {
            var entry = entries.get(day.date());
            if (day.morning() && (entry == null || entry.getDayPart() == CraDayPart.AFTERNOON)) missing += 0.5;
            if (day.afternoon() && (entry == null || entry.getDayPart() == CraDayPart.MORNING)) missing += 0.5;
        }
        double assigned = entries.values().stream().mapToDouble(day -> day.getDayPart().units()).sum();
        String state = calendar.expectedUnits() == 0 ? "NOT_EXPECTED" : declaration == null ? "MISSING"
                : declaration.getStatus() == CraDeclarationStatus.PENDING && missing > 0 ? "INCOMPLETE" : declaration.getStatus().name();
        return new CraOperationsDto.Monitor(user.getId(), user.getFirstName() + " " + user.getLastName(), user.getEmail(),
                declaration == null ? null : declaration.getId(), state, calendar.expectedUnits(), assigned, missing);
    }
    @Transactional
    public int queue(int year, int month, CraOperationsDto.ReminderRequest request) {
        var period = rules.period(year, month);
        if (period.isAfter(YearMonth.now())) throw new BadRequestException("Les relances concernent le mois courant ou un mois précédent");
        if (request.userIds() == null || request.userIds().isEmpty() || request.userIds().size() > 100) throw new BadRequestException("Sélection invalide");
        var sender = mail.requireActive(request.mailAccountId());
        UUID actor = SecurityUtils.requirePrincipal().getUserId();
        int queued = 0;
        for (UUID id : request.userIds().stream().distinct().sorted().toList()) {
            var user = users.lockForCraReminder(id).filter(User::isEnabled).filter(account -> !account.getRoles().isEmpty())
                    .orElseThrow(() -> new NotFoundException("Collaborateur introuvable"));
            var declaration = declarations.findByCollaborator_IdAndYearAndMonth(id, year, month).orElse(null);
            var row = row(user, period, declaration);
            if (Set.of("VALIDATED", "DECLARED", "NOT_EXPECTED").contains(row.status())) continue;
            if (reminders.existsByUserIdAndYearAndMonthAndQueuedDate(id, year, month, LocalDate.now())) continue;
            var reminder = new CraReminder(); reminder.setUserId(id); reminder.setRequestedBy(actor); reminder.setMailAccountId(sender.getId());
            reminder.setYear(year); reminder.setMonth(month); reminder.setQueuedDate(LocalDate.now()); reminder.setRecipient(user.getEmail()); reminders.save(reminder);
            audit.record(declaration, "REMINDER_QUEUED", "Relance " + period + " à " + user.getEmail(), declaration == null ? null : audit.snapshot(declaration));
            queued++;
        }
        return queued;
    }
    @Transactional(readOnly = true)
    public Page<CraReminder> reminders(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new BadRequestException("Pagination invalide");
        return reminders.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
    }
    @Transactional
    public void dispatch() {
        if (!feature.isEnabled()) return;
        for (var reminder : reminders.findTop20ByStateAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc("QUEUED", Instant.now())) {
            reminder.setAttempts(reminder.getAttempts() + 1);
            try {
                var user = users.findById(reminder.getUserId()).filter(User::isEnabled).orElseThrow();
                var requester = users.findById(reminder.getRequestedBy()).filter(User::isEnabled).orElseThrow();
                if (requester.getRoles().stream().noneMatch(role -> role.getName() == RoleName.ADMIN || role.getName() == RoleName.FINANCE_MANAGER)) throw new IllegalStateException();
                var declaration = declarations.findByCollaborator_IdAndYearAndMonth(user.getId(), reminder.getYear(), reminder.getMonth()).orElse(null);
                if (declaration != null && declaration.getStatus() != CraDeclarationStatus.PENDING) { reminder.setState("SKIPPED"); continue; }
                var account = accounts.findById(reminder.getMailAccountId()).orElseThrow();
                emails.sendAsUser(requester.getId(), account, user.getEmail(), "Relance CRA - " + String.format("%02d/%d", reminder.getMonth(), reminder.getYear()),
                        "Bonjour " + user.getFirstName() + ",\n\nVotre CRA " + reminder.getMonth() + "/" + reminder.getYear()
                                + " est absent ou reste à déclarer. Merci de compléter vos activités puis de soumettre la déclaration.\n\n"
                                + requester.getFirstName() + " " + requester.getLastName(), null, null);
                reminder.setState("SENT"); reminder.setSentAt(Instant.now()); reminder.setError(null);
            } catch (Exception error) {
                reminder.setError("Envoi impossible : vérifier le compte expéditeur, le destinataire et la période.");
                if (reminder.getAttempts() >= 3) reminder.setState("FAILED");
                else reminder.setNextAttemptAt(Instant.now().plusSeconds(300L * reminder.getAttempts()));
            }
        }
    }
}