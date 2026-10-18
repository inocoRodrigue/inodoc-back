package com.esn.inodoc.service.cra;

import com.esn.inodoc.domain.cra.CraDayEntry;
import com.esn.inodoc.domain.cra.CraDayPart;
import com.esn.inodoc.dto.cra.CraDayAssignmentRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CraHalfDayTest {
    @Test
    void legacyRequestsAndEntitiesRemainFullDays() throws Exception {
        UUID project = UUID.randomUUID();
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var request = mapper.readValue("{\"date\":\"2026-10-01\",\"projectId\":\"" + project + "\"}", CraDayAssignmentRequest.class);
        assertEquals(CraDayPart.FULL_DAY, request.dayPart());
        assertEquals(CraDayPart.FULL_DAY, new CraDayEntry().getDayPart());
        assertEquals(CraDayPart.FULL_DAY, new CraDayAssignmentRequest(LocalDate.now(), project, null).dayPart());
    }

    @Test
    void halfDaysCountAsHalfAWorkingDayAndSplitProjectsArePreserved() {
        assertEquals(0.5, CraDayPart.MORNING.units());
        assertEquals(0.5, CraDayPart.AFTERNOON.units());
        assertEquals(1.0, CraDayPart.FULL_DAY.units());
        UUID second = UUID.randomUUID();
        var request = new CraDayAssignmentRequest(LocalDate.now(), UUID.randomUUID(), null, CraDayPart.FULL_DAY, second, null);
        assertEquals(second, request.afternoonProjectId());
    }
}