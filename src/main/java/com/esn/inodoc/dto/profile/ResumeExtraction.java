package com.esn.inodoc.dto.profile;

import com.esn.inodoc.dto.dossier.DossierUpsertRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ResumeExtraction(
        @NotNull @Valid Identity profile,
        @NotNull @Valid DossierUpsertRequest dossier,
        @NotNull @Size(max = 8) List<@Valid ResumeSuggestion> suggestions,
        boolean incomplete,
        @NotNull List<String> warnings) {

    public record Identity(
            @Size(max = 100) String firstName,
            @Size(max = 100) String lastName,
            @Email @Size(max = 255) String email,
            @Size(max = 100) String phone,
            @Size(max = 500) String address,
            @Size(max = 255) String location) {
    }
}
