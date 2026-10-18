package com.esn.inodoc.dto.cra;

import jakarta.validation.constraints.NotNull;
import com.esn.inodoc.domain.cra.CraWorkLocation;

import java.time.LocalDate;
import java.util.UUID;

public record CraDayAssignmentRequest(
	@NotNull LocalDate date,
	@NotNull UUID projectId,
	CraWorkLocation workLocation,
	com.esn.inodoc.domain.cra.CraDayPart dayPart,
	UUID afternoonProjectId,
	CraWorkLocation afternoonWorkLocation) {
	public CraDayAssignmentRequest {
		if (dayPart == null) dayPart = com.esn.inodoc.domain.cra.CraDayPart.FULL_DAY;
	}

	public CraDayAssignmentRequest(LocalDate date, UUID projectId, CraWorkLocation workLocation) {
		this(date, projectId, workLocation, com.esn.inodoc.domain.cra.CraDayPart.FULL_DAY, null, null);
	}
}