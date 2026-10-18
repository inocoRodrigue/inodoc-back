package com.esn.inodoc.scheduler;
import com.esn.inodoc.service.cra.CraReportingService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
public class CraReminderTask {
    private final CraReportingService service;
    public CraReminderTask(CraReportingService service) { this.service = service; }
    @Scheduled(fixedDelayString = "60000")
    public void dispatch() { service.dispatch(); }
}