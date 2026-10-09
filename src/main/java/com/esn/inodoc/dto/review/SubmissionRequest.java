package com.esn.inodoc.dto.review;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Saves an employee's answers. Submitted answers remain editable while the
 * campaign is open and become read-only when it closes.
 */
public record SubmissionRequest(
        JsonNode answers,
        boolean submit) {
}
