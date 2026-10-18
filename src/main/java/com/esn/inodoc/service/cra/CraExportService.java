package com.esn.inodoc.service.cra;

import com.esn.inodoc.controller.error.BadRequestException;
import com.esn.inodoc.domain.cra.CraDayPart;
import com.esn.inodoc.dto.cra.*;
import com.esn.inodoc.repo.UserRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

@Service
public class CraExportService {
    private final CraDeclarationService declarations;
    private final UserRepository users;
    public CraExportService(CraDeclarationService declarations, UserRepository users) {
        this.declarations = declarations; this.users = users;
    }
    public String mime(String format) {
        return switch (format) {
            case "csv" -> "text/csv;charset=UTF-8";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "pdf" -> "application/pdf";
            default -> throw new BadRequestException("Format d'export invalide");
        };
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public void writePeriod(int year, int month, String format, String group, OutputStream output) throws IOException {
        var filter = new CraValidatedFilter(year, month, null, null, null, null, null, null, null, null, null, null, null, null);
        Map<UUID, Integer> dailyRates = new HashMap<>();
        Iterable<CraDeclarationDto> source = () -> new Iterator<>() {
            List<CraDeclarationDto> buffer = List.of(); int offset; int page; boolean finished;
            public boolean hasNext() {
                if (offset < buffer.size()) return true;
                if (finished) return false;
                var result = declarations.listValidated(filter, page++, 100);
                buffer = result.getContent(); offset = 0; finished = result.isLast();
                if (!buffer.isEmpty()) loadDailyRates(buffer.stream().map(CraDeclarationDto::collaboratorId).distinct().toList(), dailyRates);
                return !buffer.isEmpty();
            }
            public CraDeclarationDto next() { if (!hasNext()) throw new NoSuchElementException(); return buffer.get(offset++); }
        };
        write(source, format, group, dailyRates, output);
    }
    public void write(Iterable<CraDeclarationDto> source, String format, String group, OutputStream output) throws IOException {
        write(source, format, group, Map.of(), output);
    }
    @Transactional(readOnly = true)
    public void writeOwn(CraDeclarationDto declaration, String format, OutputStream output, boolean includeDailyRate) throws IOException {
        Map<UUID, Integer> dailyRates = new HashMap<>();
        if (includeDailyRate) loadDailyRates(List.of(declaration.collaboratorId()), dailyRates);
        write(List.of(declaration), format, "NONE", dailyRates, output);
    }
    void write(Iterable<CraDeclarationDto> source, String format, String group,
               Map<UUID, Integer> dailyRates, OutputStream output) throws IOException {
        mime(format);
        if (!Set.of("NONE", "PROJECT", "CLIENT", "COLLABORATOR").contains(group)) throw new BadRequestException("Regroupement invalide");
        String[] headers = group.equals("NONE")
                ? new String[]{"Collaborateur ID", "Collaborateur", "Email", "Periode", "Date", "Creneau", "Projet ID", "Projet", "Client", "Lieu", "Jours", "TJM EUR/jour", "Montant EUR", "Statut"}
                : group.equals("COLLABORATOR")
                    ? new String[]{"Collaborateur ID", "Collaborateur", "Email", "Jours", "TJM EUR/jour", "Montant EUR"}
                    : new String[]{"Identifiant", group.equals("PROJECT") ? "Projet" : "Client", "Jours", "Montant EUR"};
        try (var sink = sink(format, output, headers)) {
            Map<GroupKey, GroupTotal> totals = new LinkedHashMap<>();
            for (var declaration : source) for (var day : declaration.days()) {
                Integer dailyRate = dailyRates.get(declaration.collaboratorId());
                if (day.dayPart() != CraDayPart.AFTERNOON) activity(sink, totals, group, declaration, day, false, dailyRate);
                if (day.dayPart() != CraDayPart.MORNING) activity(sink, totals, group, declaration, day, true, dailyRate);
            }
            for (var entry : totals.entrySet()) {
                GroupKey key = entry.getKey(); GroupTotal total = entry.getValue();
                String units = Double.toString(total.units);
                String amount = total.valued ? total.amount.toPlainString() : "";
                if (group.equals("COLLABORATOR")) {
                    sink.row(new String[]{key.id(), key.name(), key.email(), units,
                            total.dailyRate == null ? "" : total.dailyRate.toString(), amount});
                } else {
                    sink.row(new String[]{key.id(), key.name(), units, amount});
                }
            }
        }
    }
    private record GroupKey(String id, String name, String email) {}
    private static final class GroupTotal {
        double units;
        BigDecimal amount = BigDecimal.ZERO;
        Integer dailyRate;
        boolean valued;
    }
    private void activity(RowSink sink, Map<GroupKey, GroupTotal> totals, String group, CraDeclarationDto declaration,
                          CraEntryDto day, boolean afternoon, Integer dailyRate) throws IOException {
        boolean secondary = afternoon && day.dayPart() == CraDayPart.FULL_DAY && day.afternoonProjectId() != null;
        UUID projectId = secondary ? day.afternoonProjectId() : day.projectId();
        String project = secondary ? day.afternoonProjectName() : day.projectName();
        var location = secondary ? day.afternoonWorkLocation() : day.workLocation();
        String client = secondary ? day.afternoonClientName() : day.clientName();
        if (client == null || client.isBlank()) client = "Client non renseigné";
        BigDecimal amount = dailyRate == null ? null : BigDecimal.valueOf(dailyRate).multiply(BigDecimal.valueOf(0.5));
        if (!group.equals("NONE")) {
            GroupKey key = group.equals("CLIENT") ? new GroupKey(client.trim().toLowerCase(Locale.ROOT), client.trim(), "")
                    : group.equals("PROJECT") ? new GroupKey(projectId.toString(), project, "")
                    : new GroupKey(declaration.collaboratorId().toString(), declaration.collaboratorName(), declaration.collaboratorEmail());
            GroupTotal total = totals.computeIfAbsent(key, ignored -> new GroupTotal());
            total.units += 0.5;
            if (dailyRate != null) { total.amount = total.amount.add(amount); total.valued = true; total.dailyRate = dailyRate; }
        } else sink.row(new String[]{declaration.collaboratorId().toString(), declaration.collaboratorName(), declaration.collaboratorEmail(),
                String.format("%04d-%02d", declaration.year(), declaration.month()), day.date().toString(), afternoon ? "APRES_MIDI" : "MATIN",
                projectId.toString(), project, client, location == null ? "" : location.name(), "0.5",
                dailyRate == null ? "" : dailyRate.toString(), amount == null ? "" : amount.toPlainString(), declaration.status().name()});
    }
    private void loadDailyRates(List<UUID> userIds, Map<UUID, Integer> dailyRates) {
        users.findWithProfileByIdIn(userIds).forEach(user -> {
            if (user.getProfile() != null && user.getProfile().getDailyRate() != null)
                dailyRates.put(user.getId(), user.getProfile().getDailyRate());
        });
    }
    private interface RowSink extends AutoCloseable {
        void row(String[] values) throws IOException;
        void close() throws IOException;
    }
    private RowSink sink(String format, OutputStream output, String[] headers) throws IOException {
        RowSink sink;
        if (format.equals("csv")) {
            output.write(new byte[]{(byte) 0xef, (byte) 0xbb, (byte) 0xbf});
            Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
            sink = new RowSink() {
                public void row(String[] values) throws IOException {
                    for (int index = 0; index < values.length; index++) {
                        if (index > 0) writer.write(';');
                        String value = values[index] == null ? "" : values[index];
                        String stripped = value.stripLeading();
                        if (!stripped.isEmpty() && "=+-@\t\r\n".indexOf(stripped.charAt(0)) >= 0) value = "'" + value;
                        writer.write('"'); writer.write(value.replace("\"", "\"\"")); writer.write('"');
                    }
                    writer.write("\r\n");
                }
                public void close() throws IOException { writer.flush(); }
            };
        } else if (format.equals("xlsx")) {
            SXSSFWorkbook book = new SXSSFWorkbook(100); var sheet = book.createSheet("CRA");
            for (int index = 0; index < headers.length; index++) sheet.setColumnWidth(index, 22 * 256);
            sheet.createFreezePane(0, 1);
            sink = new RowSink() {
                int rowIndex;
                public void row(String[] values) {
                    var row = sheet.createRow(rowIndex++);
                    for (int index = 0; index < values.length; index++) {
                        var cell = row.createCell(index);
                        boolean numeric = Set.of("Jours", "TJM EUR/jour", "Montant EUR").contains(headers[index]);
                        if (rowIndex > 1 && numeric && values[index] != null && !values[index].isBlank())
                            cell.setCellValue(Double.parseDouble(values[index]));
                        else cell.setCellValue(values[index] == null ? "" : values[index]);
                    }
                }
                public void close() throws IOException { try { book.write(output); } finally { book.close(); } }
            };
        } else {
            Document document = new Document(PageSize.A4.rotate());
            try {
                PdfWriter.getInstance(document, output).setCloseStream(false); document.open();
                document.add(new Paragraph("Comptes rendus d'activite", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)));
            }
            catch (DocumentException error) { throw new IOException(error); }
            PdfPTable table = new PdfPTable(headers.length); table.setWidthPercentage(100); table.setHeaderRows(1); table.setComplete(false);
            sink = new RowSink() {
                public void row(String[] values) throws IOException {
                    for (String value : values) table.addCell(new Phrase(value == null ? "" : value, FontFactory.getFont(FontFactory.HELVETICA, 7)));
                    if (table.size() >= 101) flush();
                }
                private void flush() throws IOException {
                    try { document.add(table); table.deleteBodyRows(); }
                    catch (DocumentException error) { throw new IOException(error); }
                }
                public void close() throws IOException { table.setComplete(true); try { flush(); } finally { document.close(); } }
            };
        }
        sink.row(headers); return sink;
    }
}