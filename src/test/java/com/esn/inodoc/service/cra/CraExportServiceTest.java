package com.esn.inodoc.service.cra;
import com.esn.inodoc.domain.cra.*;
import com.esn.inodoc.domain.Profile;
import com.esn.inodoc.dto.cra.*;
import com.esn.inodoc.repo.UserRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CraExportServiceTest {
    final CraDeclarationService declarations = mock(CraDeclarationService.class);
    final UserRepository users = mock(UserRepository.class);
    final CraExportService service = new CraExportService(declarations, users);
    final UUID primary = UUID.randomUUID(); final UUID secondary = UUID.randomUUID();
    CraDeclarationDto sample() {
        return new CraDeclarationDto(UUID.randomUUID(), UUID.randomUUID(), "=FORMULE", "user@example.test", 2026, 1,
                CraDeclarationStatus.VALIDATED, Instant.now(), Instant.now(), "Finance", List.of(new CraEntryDto(LocalDate.of(2026, 1, 1),
                primary, "Atlas", CraWorkLocation.REMOTE, CraDayPart.FULL_DAY, secondary, "Boreal", CraWorkLocation.CLIENT_SITE)), null);
    }
    @Test void csvIncludesBothProjectsAndNeutralizesSpreadsheetFormulas() throws Exception {
        var bytes = new ByteArrayOutputStream(); service.write(List.of(sample()), "csv", "NONE", bytes);
        String csv = bytes.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.contains("'=")); assertTrue(csv.contains("Atlas")); assertTrue(csv.contains("Boreal"));
        assertEquals(3, csv.lines().count());
    }
    @Test void excelContainsNumericHalfDaysAndBothSlots() throws Exception {
        var bytes = new ByteArrayOutputStream(); service.write(List.of(sample()), "xlsx", "NONE", bytes);
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertEquals(2, book.getSheetAt(0).getLastRowNum());
            assertEquals(0.5, book.getSheetAt(0).getRow(1).getCell(10).getNumericCellValue());
            assertEquals("Boreal", book.getSheetAt(0).getRow(2).getCell(7).getStringCellValue());
        }
    }
    @Test void emptyExportsRemainValidDocuments() throws Exception {
        var bytes = new ByteArrayOutputStream(); service.write(List.of(), "pdf", "NONE", bytes);
        try (var pdf = Loader.loadPDF(bytes.toByteArray())) { assertTrue(pdf.getNumberOfPages() > 0); }
        bytes.reset(); service.write(List.of(), "xlsx", "NONE", bytes);
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes.toByteArray()))) { assertEquals("Collaborateur ID", book.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()); }
    }
    @Test void clientSummariesCombineProjectsForTheSameClient() throws Exception {
        var source = sample();
        var activity = new CraEntryDto(LocalDate.of(2026, 1, 1), primary, "Atlas", null, CraDayPart.FULL_DAY, secondary, "Boreal", null, "Acme", "Acme");
        var declaration = new CraDeclarationDto(source.id(), source.collaboratorId(), source.collaboratorName(), source.collaboratorEmail(),
                source.year(), source.month(), source.status(), source.declaredAt(), source.validatedAt(), source.validatedByName(), List.of(activity), null);
        var bytes = new ByteArrayOutputStream(); service.write(List.of(declaration), "csv", "CLIENT", bytes);
        var csv = bytes.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(2, csv.lines().count()); assertTrue(csv.contains("Acme\";\"1.0"));
    }
    @Test void pdfIsReadableAndSummariesCountEachProjectHalfDay() throws Exception {
        var bytes = new ByteArrayOutputStream(); service.write(List.of(sample()), "pdf", "NONE", bytes);
        try (var pdf = Loader.loadPDF(bytes.toByteArray())) { assertTrue(new PDFTextStripper().getText(pdf).contains("Boreal")); }
        bytes.reset(); service.write(List.of(sample()), "csv", "PROJECT", bytes);
        var csv = bytes.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.contains("Atlas\";\"0.5")); assertTrue(csv.contains("Boreal\";\"0.5"));
    }
    @Test void detailedExportCalculatesHalfDayAmountFromDailyRate() throws Exception {
        var declaration = sample(); var rates = Map.of(declaration.collaboratorId(), 650);
        var bytes = new ByteArrayOutputStream(); service.write(List.of(declaration), "csv", "NONE", rates, bytes);
        var csv = bytes.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.contains("TJM EUR/jour"));
        assertTrue(csv.contains("650"));
        assertEquals(2, csv.lines().filter(line -> line.contains("\"325.0\"")).count());
        bytes.reset(); service.write(List.of(declaration), "xlsx", "COLLABORATOR", rates, bytes);
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes.toByteArray()))) {
            var row = book.getSheetAt(0).getRow(1);
            assertEquals(1.0, row.getCell(3).getNumericCellValue());
            assertEquals(650.0, row.getCell(4).getNumericCellValue());
            assertEquals(650.0, row.getCell(5).getNumericCellValue());
        }
    }
    @Test void monthlyExportLoadsRatesInBatchesAndWritesValorisation() throws Exception {
        var declaration = sample(); var user = new com.esn.inodoc.domain.User(); user.setId(declaration.collaboratorId());
        var profile = new Profile(); profile.setDailyRate(550); user.setProfile(profile);
        when(declarations.listValidated(any(), eq(0), eq(100)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(declaration)));
        when(users.findWithProfileByIdIn(List.of(user.getId()))).thenReturn(List.of(user));
        var bytes = new ByteArrayOutputStream(); service.writePeriod(2026, 1, "csv", "NONE", bytes);
        var csv = bytes.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(2, csv.lines().filter(line -> line.contains("\"550\";\"275.0\"")).count());
        verify(users).findWithProfileByIdIn(List.of(user.getId()));
    }
}