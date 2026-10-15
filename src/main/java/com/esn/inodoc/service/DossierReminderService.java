package com.esn.inodoc.service;

import com.esn.inodoc.domain.CompetenceDossier;
import com.esn.inodoc.domain.MailAccount;
import com.esn.inodoc.domain.Profile;
import com.esn.inodoc.domain.User;
import com.esn.inodoc.repo.MailAccountRepository;
import com.esn.inodoc.repo.ProfileRepository;
import com.esn.inodoc.repo.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DossierReminderService {
    private final ProfileRepository profiles;
    private final UserRepository users;
    private final MailAccountRepository mailAccounts;

    public DossierReminderService(ProfileRepository profiles, UserRepository users,
                                  MailAccountRepository mailAccounts) {
        this.profiles = profiles;
        this.users = users;
        this.mailAccounts = mailAccounts;
    }

    @Transactional(readOnly = true)
    public List<DossierReminder> findDueReminders(int staleAfterMonths) {
        if (staleAfterMonths < 1) {
            return List.of();
        }

        List<DossierReminder> reminders = new ArrayList<>();
        for (Profile profile : profiles.findAll()) {
            CompetenceDossier master = profile.getDossiers().stream()
                    .filter(CompetenceDossier::isMaster)
                    .findFirst()
                    .orElse(null);
            if (master == null || !DossierObsolescence.isObsolete(
                    true, master.getUpdatedAt(), staleAfterMonths)) {
                continue;
            }

            User owner = users.findByProfileId(profile.getId())
                    .filter(User::isEnabled)
                    .orElse(null);
            if (owner == null) {
                continue;
            }

            MailAccount mailAccount = mailAccounts.findByCreatedByAndActiveTrue(owner.getId())
                    .orElse(null);
            if (mailAccount != null) {
                reminders.add(new DossierReminder(owner.getId(), profile.getId(), owner.getEmail(),
                        profile.getFirstName(), profile.getLastName(), mailAccount));
            }
        }
        return reminders;
    }

    public record DossierReminder(UUID userId, UUID profileId, String recipient,
                                  String firstName, String lastName, MailAccount mailAccount) {
    }
}