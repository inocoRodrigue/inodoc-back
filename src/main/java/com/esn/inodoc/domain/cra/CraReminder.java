package com.esn.inodoc.domain.cra;

import com.esn.inodoc.common.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "cra_reminders", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "period_year", "period_month", "queued_date"}))
public class CraReminder extends BaseEntity {
    private UUID userId;
    private UUID requestedBy;
    private UUID mailAccountId;
    @Column(name = "period_year") private int year;
    @Column(name = "period_month") private int month;
    private LocalDate queuedDate;
    private String recipient;
    private String state = "QUEUED";
    private int attempts;
    private Instant nextAttemptAt = Instant.now();
    private Instant sentAt;
    @Column(columnDefinition = "TEXT") private String error;
    public UUID getUserId() { return userId; } public void setUserId(UUID value) { userId = value; }
    public UUID getRequestedBy() { return requestedBy; } public void setRequestedBy(UUID value) { requestedBy = value; }
    public UUID getMailAccountId() { return mailAccountId; } public void setMailAccountId(UUID value) { mailAccountId = value; }
    public int getYear() { return year; } public void setYear(int value) { year = value; }
    public int getMonth() { return month; } public void setMonth(int value) { month = value; }
    public LocalDate getQueuedDate() { return queuedDate; } public void setQueuedDate(LocalDate value) { queuedDate = value; }
    public String getRecipient() { return recipient; } public void setRecipient(String value) { recipient = value; }
    public String getState() { return state; } public void setState(String value) { state = value; }
    public int getAttempts() { return attempts; } public void setAttempts(int value) { attempts = value; }
    public Instant getNextAttemptAt() { return nextAttemptAt; } public void setNextAttemptAt(Instant value) { nextAttemptAt = value; }
    public Instant getSentAt() { return sentAt; } public void setSentAt(Instant value) { sentAt = value; }
    public String getError() { return error; } public void setError(String value) { error = value; }
}