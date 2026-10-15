package com.esn.inodoc.scheduler;

import com.esn.inodoc.service.CompanySettingsService;
import com.esn.inodoc.service.DossierReminderService;
import com.esn.inodoc.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DossierValidityReminderTask {
    private static final Logger log = LoggerFactory.getLogger(DossierValidityReminderTask.class);
    private static final String SUBJECT = "Action requise : votre dossier de compétences est obsolète";

    private final CompanySettingsService companySettingsService;
    private final DossierReminderService dossierReminderService;
    private final EmailService emailService;

    public DossierValidityReminderTask(CompanySettingsService companySettingsService,
                                       DossierReminderService dossierReminderService,
                                       EmailService emailService) {
        this.companySettingsService = companySettingsService;
        this.dossierReminderService = dossierReminderService;
        this.emailService = emailService;
    }

    @Scheduled(cron = "0 30 22 * * FRI", zone = "Europe/Paris")
    public void sendWeeklyDossierReminders() {
        int staleAfterMonths = companySettingsService.get().staleDossierMonths();
        for (DossierReminderService.DossierReminder reminder
                : dossierReminderService.findDueReminders(staleAfterMonths)) {
            try {
                emailService.sendAsUser(reminder.userId(), reminder.mailAccount(), reminder.recipient(), SUBJECT,
                        emailBody(reminder.firstName(), reminder.lastName(), staleAfterMonths), null, null);
            } catch (RuntimeException exception) {
                log.warn("Échec du rappel d'obsolescence du dossier profileId={} cause={}",
                        reminder.profileId(), exception.getClass().getSimpleName());
            }
        }
    }

    private String emailBody(String firstName, String lastName, int staleAfterMonths) {
        return "Bonjour " + firstName + " " + lastName + ",\n\n"
                + "Votre dossier de compétences inoDoc n'a pas été mis à jour depuis plus de "
                + staleAfterMonths + " mois et est désormais obsolète.\n"
                + "Nous vous invitons à vous connecter à inoDoc et à actualiser la version master de votre dossier.\n\n"
                + "Cordialement,\nL'équipe inoDoc";
    }
}