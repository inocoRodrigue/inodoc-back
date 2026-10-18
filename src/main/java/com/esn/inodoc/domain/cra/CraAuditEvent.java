package com.esn.inodoc.domain.cra;

import com.esn.inodoc.common.domain.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "cra_audit_events")
public class CraAuditEvent extends BaseEntity {
    private UUID declarationId;
    private UUID actorId;
    private String actorName;
    private String action;
    @Column(columnDefinition = "TEXT") private String reason;
    @Column(columnDefinition = "TEXT") private String beforeState;
    @Column(columnDefinition = "TEXT") private String afterState;
    public UUID getDeclarationId() { return declarationId; }
    public void setDeclarationId(UUID value) { declarationId = value; }
    public UUID getActorId() { return actorId; }
    public void setActorId(UUID value) { actorId = value; }
    public String getActorName() { return actorName; }
    public void setActorName(String value) { actorName = value; }
    public String getAction() { return action; }
    public void setAction(String value) { action = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
    public String getBeforeState() { return beforeState; }
    public void setBeforeState(String value) { beforeState = value; }
    public String getAfterState() { return afterState; }
    public void setAfterState(String value) { afterState = value; }
}