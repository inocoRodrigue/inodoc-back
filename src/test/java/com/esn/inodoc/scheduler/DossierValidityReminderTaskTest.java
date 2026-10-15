package com.esn.inodoc.scheduler;

import com.esn.inodoc.domain.CompetenceDossier;
import com.esn.inodoc.domain.MailAccount;
import com.esn.inodoc.domain.Profile;
import com.esn.inodoc.domain.User;
import com.esn.inodoc.dto.company.CompanyContactDto;
import com.esn.inodoc.repo.MailAccountRepository;
import com.esn.inodoc.repo.ProfileRepository;
import com.esn.inodoc.repo.UserRepository;
import com.esn.inodoc.service.CompanySettingsService;
import com.esn.inodoc.service.DossierReminderService;
import com.esn.inodoc.service.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class DossierValidityReminderTaskTest {
    @Test
    void sendsWeeklyReminderFromTheOwnersMailAccountToTheOwner() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User owner = new User();
        owner.setId(userId);
        owner.setEmail("person@example.test");

        Profile profile = new Profile();
        profile.setId(profileId);
        profile.setFirstName("Alex");
        profile.setLastName("Martin");
        CompetenceDossier master = new CompetenceDossier();
        master.setMaster(true);
        ReflectionTestUtils.setField(master, "updatedAt",
                java.time.ZonedDateTime.now(ZoneOffset.UTC).minusMonths(13).toInstant());
        profile.addDossier(master);

        MailAccount account = new MailAccount();
        account.setCreatedBy(userId);
        account.setEmail("person@gmail.test");

        ProfileRepository profiles = mock(ProfileRepository.class);
        UserRepository users = mock(UserRepository.class);
        MailAccountRepository mailAccounts = mock(MailAccountRepository.class);
        CompanySettingsService companySettings = mock(CompanySettingsService.class);
        EmailService email = mock(EmailService.class);
        when(profiles.findAll()).thenReturn(List.of(profile));
        when(users.findByProfileId(profileId)).thenReturn(Optional.of(owner));
        when(mailAccounts.findByCreatedByAndActiveTrue(userId)).thenReturn(Optional.of(account));
        when(companySettings.get()).thenReturn(
                new CompanyContactDto(null, null, null, null, null, 12));

        var reminders = new DossierReminderService(profiles, users, mailAccounts);
        var task = new DossierValidityReminderTask(companySettings, reminders, email);
        task.sendWeeklyDossierReminders();

        verify(email).sendAsUser(eq(userId), same(account), eq("person@example.test"),
                contains("obsolète"), contains("12 mois"), isNull(), isNull());
        Scheduled scheduled = DossierValidityReminderTask.class
                .getMethod("sendWeeklyDossierReminders")
                .getAnnotation(Scheduled.class);
        assertEquals("0 30 22 * * FRI", scheduled.cron());
        assertEquals("Europe/Paris", scheduled.zone());
    }

    @Test
    void skipsProfilesWithoutAnActiveGmailConnection() {
        UUID profileId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User owner = new User();
        owner.setId(userId);
        owner.setEmail("person@example.test");
        Profile profile = new Profile();
        profile.setId(profileId);
        CompetenceDossier master = new CompetenceDossier();
        master.setMaster(true);
        ReflectionTestUtils.setField(master, "updatedAt",
                java.time.ZonedDateTime.now(ZoneOffset.UTC).minusMonths(13).toInstant());
        profile.addDossier(master);

        var profiles = mock(ProfileRepository.class);
        var users = mock(UserRepository.class);
        var mailAccounts = mock(MailAccountRepository.class);
        when(profiles.findAll()).thenReturn(List.of(profile));
        when(users.findByProfileId(profileId)).thenReturn(Optional.of(owner));
        when(mailAccounts.findByCreatedByAndActiveTrue(userId)).thenReturn(Optional.empty());

        assertEquals(0, new DossierReminderService(profiles, users, mailAccounts)
                .findDueReminders(12).size());
    }
}