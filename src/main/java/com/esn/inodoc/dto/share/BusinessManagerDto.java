package com.esn.inodoc.dto.share;

import java.util.UUID;

public record BusinessManagerDto(
        UUID id,
        String firstName,
        String lastName,
        String email) {
}