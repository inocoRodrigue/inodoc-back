package com.esn.inodoc.dto.cra;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class CraOperationsDto {
    private CraOperationsDto() {}
    public record Policy(boolean allowSelfApproval,
                         @NotNull @Size(max = 1000) Set<@NotNull LocalDate> holidays,
                         @Size(max = 1000) List<@NotNull @jakarta.validation.Valid DateRule> dateRules) {
        public Policy(boolean allowSelfApproval, Set<LocalDate> holidays) { this(allowSelfApproval, holidays, List.of()); }
    }
    public record DateRule(@NotNull LocalDate date, @NotNull com.esn.inodoc.domain.cra.CraDateRuleType type,
                           @Size(max = 255) String label, UUID projectId, String projectName) {}
    public record Reason(@NotBlank @Size(max = 1000) String reason) {}
    public record CalendarDay(LocalDate date, boolean morning, boolean afternoon, boolean holiday,
                              boolean locked, com.esn.inodoc.domain.cra.CraDateRuleType ruleType,
                              String label, UUID projectId, String projectName) {
        public CalendarDay(LocalDate date, boolean morning, boolean afternoon, boolean holiday) {
            this(date, morning, afternoon, holiday, !morning && !afternoon, null, null, null, null);
        }
    }
    public record Calendar(List<CalendarDay> days, double expectedUnits) {}
    public record History(UUID id, Instant createdAt, String actorName, String action, String reason,
                          String beforeState, String afterState) {}
    public record Monitor(UUID userId, String name, String email, UUID declarationId, String status,
                          double expectedUnits, double assignedUnits, double missingUnits) {}
    public record Statistics(int year, int month, long collaboratorCount, long declaredCount,
                             double expectedUnits, double assignedUnits, double missingUnits,
                             double completionPercent, double validatedAmount,
                             List<StatisticMetric> statuses, List<StatisticMetric> activityByDate,
                             List<StatisticMetric> revenueByClient, List<StatisticMetric> revenueByProject) {}
    public record StatisticMetric(String key, String label, double value, double amount) {}
    public record ReminderRequest(@NotNull @Size(min = 1, max = 100) List<@NotNull UUID> userIds,
                                  @NotNull UUID mailAccountId) {}
}