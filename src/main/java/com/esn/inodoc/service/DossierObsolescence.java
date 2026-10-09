package com.esn.inodoc.service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

public final class DossierObsolescence {
    private DossierObsolescence() {
    }

    public static boolean isObsolete(boolean master, Instant updatedAt, int staleAfterMonths) {
        if (!master || updatedAt == null || staleAfterMonths < 1) {
            return false;
        }
        ZonedDateTime cutoff = ZonedDateTime.now(ZoneOffset.UTC).minusMonths(staleAfterMonths);
        return updatedAt.isBefore(cutoff.toInstant());
    }
}