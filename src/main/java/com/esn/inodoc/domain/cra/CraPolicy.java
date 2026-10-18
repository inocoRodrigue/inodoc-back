package com.esn.inodoc.domain.cra;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

@Entity
@Table(name = "cra_policy")
public class CraPolicy {
    @Id private Integer id = 1;
    private boolean allowSelfApproval;
    @ElementCollection
    @CollectionTable(name = "cra_holidays", joinColumns = @JoinColumn(name = "policy_id"))
    @Column(name = "holiday_date")
    private Set<LocalDate> holidays = new HashSet<>();
    @ElementCollection
    @CollectionTable(name = "cra_date_rules", joinColumns = @JoinColumn(name = "policy_id"))
    @MapKeyColumn(name = "rule_date")
    private Map<LocalDate, CraDateRule> dateRules = new HashMap<>();
    public Map<LocalDate, CraDateRule> getDateRules() { return dateRules; }
    public void setDateRules(Map<LocalDate, CraDateRule> value) { dateRules = new HashMap<>(value); }
    public Integer getId() { return id; }
    public boolean isAllowSelfApproval() { return allowSelfApproval; }
    public void setAllowSelfApproval(boolean value) { allowSelfApproval = value; }
    public Set<LocalDate> getHolidays() { return holidays; }
    public void setHolidays(Set<LocalDate> value) { holidays = new HashSet<>(value); }
}