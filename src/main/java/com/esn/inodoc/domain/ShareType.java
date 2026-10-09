package com.esn.inodoc.domain;

/**
 * Type of secure share link.
 */
public enum ShareType {
    /** View only, intended for clients. */
    CLIENT_READONLY,
    /** View + edit, intended for the collaborator or candidate. */
    COLLABORATOR_EDIT
}
