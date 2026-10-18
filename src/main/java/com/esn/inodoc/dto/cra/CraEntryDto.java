package com.esn.inodoc.dto.cra;

import java.time.LocalDate;
import com.esn.inodoc.domain.cra.CraWorkLocation;
import java.util.UUID;

public record CraEntryDto(LocalDate date, UUID projectId, String projectName, CraWorkLocation workLocation,
						  com.esn.inodoc.domain.cra.CraDayPart dayPart, UUID afternoonProjectId,
						  String afternoonProjectName, CraWorkLocation afternoonWorkLocation,
						  String clientName, String afternoonClientName) {
	public CraEntryDto(LocalDate date, UUID projectId, String projectName, CraWorkLocation workLocation,
					   com.esn.inodoc.domain.cra.CraDayPart dayPart, UUID afternoonProjectId,
					   String afternoonProjectName, CraWorkLocation afternoonWorkLocation) {
		this(date, projectId, projectName, workLocation, dayPart, afternoonProjectId, afternoonProjectName, afternoonWorkLocation, null, null);
	}
	public CraEntryDto(LocalDate date, UUID projectId, String projectName, CraWorkLocation workLocation) {
		this(date, projectId, projectName, workLocation, com.esn.inodoc.domain.cra.CraDayPart.FULL_DAY, null, null, null);
	}
}