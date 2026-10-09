package com.esn.inodoc.domain;

/**
 * Content included in a competence dossier share email.
 */
public enum ShareEmailContent {
    /** Secure view link only. */
    LINK,
    /** Dossier as PDF attachment only. */
    PDF,
    /** View link and PDF attachment. */
    BOTH
}
