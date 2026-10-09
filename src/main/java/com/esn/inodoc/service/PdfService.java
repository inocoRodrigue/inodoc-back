package com.esn.inodoc.service;

import com.esn.inodoc.domain.Certification;
import com.esn.inodoc.domain.CompetenceDossier;
import com.esn.inodoc.domain.DossierReference;
import com.esn.inodoc.domain.Education;
import com.esn.inodoc.domain.ExperienceCompany;
import com.esn.inodoc.domain.ExperienceProject;
import com.esn.inodoc.domain.LanguageSkill;
import com.esn.inodoc.domain.Profile;
import com.esn.inodoc.domain.SkillGroup;
import com.esn.inodoc.domain.TestResult;
import com.esn.inodoc.dto.company.CompanyContactDto;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfShading;
import com.lowagie.text.pdf.PdfTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Generation of the structured competence dossier as a PDF from a version.
 */
@Service
public class PdfService {
    private static final Logger log = LoggerFactory.getLogger(PdfService.class);

    // Palette aligned with the web version (identity: black ink + yellow)
    private static final Color BRAND_INK = new Color(19, 19, 19);      // #131313
    private static final Color BRAND_ACCENT = new Color(255, 202, 0);  // #ffca00
    private static final Color INK_SOFT = new Color(38, 38, 38);
    private static final Color CARD_BG = Color.WHITE;
    private static final Color CARD_BORDER = Color.WHITE;
    private static final Color HEADER_BLUE = new Color(145, 172, 223);

    private static final Font H1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BRAND_INK);
    private static final Font H2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, BRAND_INK);
    private static final Font H3 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BRAND_INK);
    private static final Font ROLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BRAND_INK);
    private static final Font BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BRAND_INK);
    private static final Font LABEL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Font.UNDERLINE, BRAND_INK);
    private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 14, INK_SOFT);
    private static final Font SMALL = FontFactory.getFont(FontFactory.HELVETICA, 12, BRAND_INK);
    private static final Font BRAND = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BRAND_INK);
    private static final DateTimeFormatter MONTH_YEAR =
            DateTimeFormatter.ofPattern("MM/yyyy", Locale.FRENCH);

    private final CompanySettingsService companySettingsService;
    private final S3ImageStorage storage;

    public PdfService(CompanySettingsService companySettingsService, S3ImageStorage storage) {
        this.companySettingsService = companySettingsService;
        this.storage = storage;
    }

    public byte[] generateDossierPdf(CompetenceDossier dossier) {
        Profile profile = dossier.getProfile();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.LETTER, 28, 28, 104, 40);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            PdfPTable header = companyHeader(document.right() - document.left());
            writer.setPageEvent(new PdfPageEventHelper() {
                private PdfTemplate pageCount;

                @Override
                public void onOpenDocument(PdfWriter current, Document page) {
                    pageCount = current.getDirectContent().createTemplate(40, 16);
                }

                @Override
                public void onEndPage(PdfWriter current, Document page) {
                    float top = page.getPageSize().getHeight() - 16;
                    var canvas = current.getDirectContent();
                    canvas.saveState();
                    canvas.roundRectangle(page.left(), top - 90, page.right() - page.left(), 90, 14);
                    canvas.clip();
                    canvas.newPath();
                    canvas.paintShading(PdfShading.simpleAxial(current, page.left() + 175, top - 45,
                            page.right(), top - 45, Color.WHITE, HEADER_BLUE));
                    canvas.restoreState();
                    header.writeSelectedRows(0, -1, page.left(), top, canvas);
                    float center = page.getPageSize().getWidth() / 2;
                    ColumnText.showTextAligned(canvas, Element.ALIGN_RIGHT,
                            new Phrase(current.getPageNumber() + "/", SMALL), center, 18, 0);
                    canvas.addTemplate(pageCount, center, 18);
                }

                @Override
                public void onCloseDocument(PdfWriter current, Document page) {
                    ColumnText.showTextAligned(pageCount, Element.ALIGN_LEFT,
                            new Phrase(String.valueOf(current.getPageNumber() - 1), SMALL), 0, 0, 0);
                }
            });
            document.open();

            // Header: initials only (client-side anonymization)
            addCentered(document, initials(profile), H1);
            if (hasText(dossier.getHeadline())) {
                addCentered(document, dossier.getHeadline(), ROLE);
            }
            if (dossier.getYearsOfExperience() != null) {
                addCentered(document, dossier.getYearsOfExperience() + " ans d'expérience", BOLD);
            }
            document.add(spacer());

            // Summary
            if (hasText(dossier.getSummary())) {
                section(document, "Profil");
                document.add(new Paragraph(dossier.getSummary(), NORMAL));
                document.add(spacer());
            }

            // Education & certifications
            if (!dossier.getEducations().isEmpty() || !dossier.getCertifications().isEmpty()) {
                section(document, "Formations & Certifications");
                for (Education edu : dossier.getEducations()) {
                    Paragraph p = new Paragraph();
                    p.add(new Phrase("\u2022 ", BOLD));
                    p.add(new Phrase(hasText(edu.getDegree()) ? edu.getDegree() : safe(edu.getSchool()), BOLD));
                    if (hasText(edu.getDegree()) && hasText(edu.getSchool())) {
                        p.add(new Phrase(" - " + edu.getSchool(), NORMAL));
                    }
                    if (hasText(edu.getYear())) {
                        p.add(new Phrase("  (" + edu.getYear() + ")", SMALL));
                    }
                    p.setSpacingAfter(6);
                    document.add(p);
                    if (hasText(edu.getLink())) {
                        document.add(new Paragraph("Lien : " + edu.getLink(), SMALL));
                    }
                    Image image = dossierImage(edu.getImageUrl());
                    if (image != null) document.add(image);
                }
                for (Certification cert : dossier.getCertifications()) {
                    Paragraph p = new Paragraph();
                    p.add(new Phrase("\u2022 ", BOLD));
                    p.add(new Phrase(safe(cert.getName()), BOLD));
                    if (hasText(cert.getIssuer())) {
                        p.add(new Phrase(" - " + cert.getIssuer(), NORMAL));
                    }
                    if (hasText(cert.getYear())) {
                        p.add(new Phrase("  (" + cert.getYear() + ")", SMALL));
                    }
                    document.add(p);
                    if (hasText(cert.getLink())) {
                        document.add(new Paragraph("Lien : " + cert.getLink(), SMALL));
                    }
                    Image image = dossierImage(cert.getImageUrl());
                    if (image != null) document.add(image);
                }
                document.add(spacer());
            }

            // Skills
            if (!dossier.getSkillGroups().isEmpty()) {
                section(document, "Compétences");
                PdfPTable skills = new PdfPTable(new float[]{1.2f, 3f});
                skills.setWidthPercentage(100);
                skills.setSplitLate(false);
                for (SkillGroup group : dossier.getSkillGroups()) {
                    for (Phrase content : List.of(new Phrase(safe(group.getCategory()), BOLD),
                            new Phrase(safe(group.getContent()), NORMAL))) {
                        PdfPCell cell = new PdfPCell(content);
                        cell.setBorderColor(BRAND_INK);
                        cell.setBorderWidth(0.6f);
                        cell.setPadding(4);
                        skills.addCell(cell);
                    }
                }
                document.add(skills);
                document.add(spacer());
            }

            // Languages
            if (!dossier.getLanguages().isEmpty()) {
                section(document, "Langues");
                PdfPTable languages = new PdfPTable(new float[]{1f, 3f});
                languages.setWidthPercentage(100);
                for (LanguageSkill language : dossier.getLanguages()) {
                    for (Phrase content : List.of(new Phrase(safe(language.getLanguage()), NORMAL),
                            new Phrase(safe(language.getLevel()), BOLD))) {
                        PdfPCell cell = new PdfPCell(content);
                        cell.setBorder(PdfPCell.NO_BORDER);
                        cell.setPadding(4);
                        languages.addCell(cell);
                    }
                }
                document.add(languages);
                document.add(spacer());
            }

            // Technical tests
            if (!dossier.getTestResults().isEmpty()) {
                section(document, "Tests techniques");
                for (TestResult test : dossier.getTestResults()) {
                    Paragraph head = new Paragraph();
                    head.add(new Phrase(safe(test.getName()), BOLD));
                    if (hasText(test.getScore())) {
                        head.add(new Phrase("   Score : " + test.getScore(), SMALL));
                    }
                    PdfPCell cell = cardCell();
                    cell.addElement(head);
                    if (hasText(test.getDescription())) {
                        cell.addElement(new Paragraph(test.getDescription(), NORMAL));
                    }
                    if (hasText(test.getLink())) {
                        cell.addElement(new Paragraph("Lien : " + test.getLink(), SMALL));
                    }
                    Image image = dossierImage(test.getImageUrl());
                    if (image != null) cell.addElement(image);
                    document.add(cardTable(cell));
                }
                document.add(spacer());
            }

            if (!dossier.getReferences().isEmpty()) {
                section(document, "Références");
                for (DossierReference ref : dossier.getReferences()) {
                    Paragraph name = new Paragraph();
                    name.add(new Phrase(safe(ref.getName()), BOLD));
                    String contact = List.of(Optional.ofNullable(ref.getEmail()).orElse(""),
                                    Optional.ofNullable(ref.getPhone()).orElse(""))
                            .stream().filter(this::hasText).collect(Collectors.joining("   |   "));
                    if (hasText(contact)) name.add(new Phrase("   " + contact, SMALL));
                    PdfPCell cell = cardCell();
                    cell.addElement(name);
                    if (hasText(ref.getDescription())) cell.addElement(new Paragraph(ref.getDescription(), NORMAL));
                    if (hasText(ref.getLink())) cell.addElement(new Paragraph("Lien : " + ref.getLink(), SMALL));
                    document.add(cardTable(cell));
                }
            }

            // Professional experiences
            if (!dossier.getExperiences().isEmpty()) {
                section(document, "Expériences professionnelles");
                for (ExperienceCompany exp : dossier.getExperiences()) {
                    String period = formatPeriod(exp.getStartDate(), exp.getEndDate());
                    PdfPTable company = new PdfPTable(new float[]{2f, 1.3f});
                    company.setWidthPercentage(100);
                    company.setSpacingBefore(8);
                    company.setSpacingAfter(6);
                    for (int column = 0; column < 2; column++) {
                        PdfPCell cell = new PdfPCell(new Phrase(column == 0
                                ? safe(exp.getCompany()).toUpperCase(Locale.FRENCH) : period, H3));
                        cell.setHorizontalAlignment(column == 0 ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
                        cell.setBorder(PdfPCell.BOTTOM);
                        cell.setBorderColor(BRAND_ACCENT);
                        cell.setPaddingBottom(5);
                        company.addCell(cell);
                    }
                    document.add(company);
                    if (hasText(exp.getRoleTitle())) {
                        document.add(new Paragraph(exp.getRoleTitle(), BOLD));
                    }
                    if (hasText(exp.getContext())) {
                        document.add(new Paragraph(exp.getContext(), NORMAL));
                    }
                    int index = 1;
                    for (ExperienceProject project : exp.getProjects()) {
                        PdfPCell cell = projectCell();
                        String projectHeading = "Projet #" + index
                                + (hasText(project.getName()) ? " : " + project.getName() : "");
                        cell.addElement(new Paragraph(projectHeading, BOLD));
                        addLabelled(cell, "Contexte général", project.getContext());
                        addLabelled(cell, "Équipe", project.getTeam());
                        addLabelled(cell, "Méthodologie", project.getMethodology());
                        if (hasText(project.getTasks())) {
                            cell.addElement(new Paragraph("Tâches :", BOLD));
                            com.lowagie.text.List tasks = new com.lowagie.text.List(false);
                            tasks.setIndentationLeft(18);
                            tasks.setListSymbol(new com.lowagie.text.Chunk("\u2022 ", NORMAL));
                            for (String task : project.getTasks().split("\\r?\\n")) {
                                if (!task.isBlank()) {
                                    tasks.add(new com.lowagie.text.ListItem(task.trim(), NORMAL));
                                }
                            }
                            cell.addElement(tasks);
                        }
                        addLabelled(cell, "Environnement technologique", project.getTechnologies());
                        document.add(projectTable(cell));
                        index++;
                    }
                }
            }

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Erreur lors de la generation du PDF", e);
        }
    }

    private void addLabelled(PdfPCell cell, String label, String value) {
        if (hasText(value)) {
            Paragraph p = new Paragraph();
            p.add(new Phrase(label + " : ", LABEL));
            p.add(new Phrase(value, NORMAL));
            cell.addElement(p);
        }
    }

    private void addCentered(Document document, String value, Font font) throws com.lowagie.text.DocumentException {
        Paragraph paragraph = new Paragraph(value, font);
        paragraph.setAlignment(Element.ALIGN_CENTER);
        document.add(paragraph);
    }

    /** Borderless content block for references and tests. */
    private PdfPCell cardCell() {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(CARD_BG);
        cell.setBorderColor(CARD_BORDER);
        cell.setBorder(PdfPCell.NO_BORDER);
        cell.setPadding(0);
        cell.setUseVariableBorders(true);
        return cell;
    }

    private PdfPTable cardTable(PdfPCell cell) {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setSpacingBefore(5);
        table.setSplitLate(false);
        table.addCell(cell);
        return table;
    }

    /** Plain project body aligned with the reference document. */
    private PdfPCell projectCell() {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(PdfPCell.NO_BORDER);
        cell.setPadding(0);
        return cell;
    }

    private PdfPTable projectTable(PdfPCell cell) {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setSpacingBefore(5);
        table.setSplitLate(false);
        table.addCell(cell);
        return table;
    }

    private PdfPTable companyHeader(float width) {
        CompanyContactDto company = companySettingsService.get();

        PdfPTable header = new PdfPTable(2);
        header.setTotalWidth(width);
        header.setLockedWidth(true);
        try {
            header.setWidths(new float[]{2.3f, 4.7f});
        } catch (Exception ignored) {
            // default widths
        }
        header.setSpacingAfter(10);

        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(PdfPCell.NO_BORDER);
        logoCell.setMinimumHeight(90);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        logoCell.setPadding(0);
        Image logo = brandLogo(company.logoUrl());
        if (logo != null) {
            logo.scaleToFit(172, 80);
            logoCell.addElement(logo);
        } else {
            logoCell.addElement(new Paragraph("inoco", BRAND));
        }
        header.addCell(logoCell);

        Paragraph contact = new Paragraph();
        addContactLine(contact, company.address());
        addContactLine(contact, company.phone() == null ? null : "Tél : " + company.phone());
        addContactLine(contact, company.email() == null ? null : "Mail : " + company.email());
        PdfPCell left = new PdfPCell(contact);
        left.setBorder(PdfPCell.NO_BORDER);
        left.setHorizontalAlignment(Element.ALIGN_RIGHT);
        left.setVerticalAlignment(Element.ALIGN_MIDDLE);
        left.setPaddingRight(10);
        contact.setAlignment(Element.ALIGN_RIGHT);
        header.addCell(left);
        return header;
    }

    /**
     * Loads the logo to display in the dossier header: the configured company
     * logo (http/https URL, e.g. stored on S3) if present, otherwise the
    * bundled reference INOCO logo from the classpath. Returns null if none is usable.
     */
    private Image brandLogo(String logoUrl) {
        Image storedLogo = dossierImage(logoUrl);
        if (storedLogo != null) return storedLogo;
        try (var in = getClass().getResourceAsStream("/inoco-reference-logo.png")) {
            if (in == null) {
                return null;
            }
            return Image.getInstance(in.readAllBytes());
        } catch (Exception e) {
            return null;
        }
    }

    private Image dossierImage(String imageUrl) {
        if (!storage.isStoredImage(imageUrl)) return null;
        try {
            var connection = java.net.URI.create(storage.signedUrl(imageUrl)).toURL().openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            final int maxBytes = 5 * 1024 * 1024;
            try (var input = connection.getInputStream()) {
                byte[] bytes = input.readNBytes(maxBytes + 1);
                if (bytes.length > maxBytes) return null;
                try (var imageInput = javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
                    var readers = javax.imageio.ImageIO.getImageReaders(imageInput);
                    if (!readers.hasNext()) return null;
                    var reader = readers.next();
                    try {
                        reader.setInput(imageInput, true, true);
                        long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                        if (pixels <= 0 || pixels > 16_000_000) return null;
                        Image image = Image.getInstance(reader.read(0), null);
                        image.scaleToFit(450, 440);
                        image.setSpacingBefore(6);
                        image.setSpacingAfter(8);
                        return image;
                    } finally {
                        reader.dispose();
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Dossier image omitted from PDF: type={}", ex.getClass().getSimpleName());
            return null;
        }
    }

    private void addContactLine(Paragraph target, String value) {
        if (hasText(value)) {
            Paragraph line = new Paragraph(value, SMALL);
            line.setAlignment(Element.ALIGN_RIGHT);
            target.add(line);
        }
    }

    private String initials(Profile p) {
        return java.util.Arrays.stream((safe(p.getFirstName()) + " " + safe(p.getLastName())).split("[\\s.]+"))
            .filter(part -> !part.isBlank()).map(this::initial).collect(Collectors.joining(""));
    }

    private String initial(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Character.toUpperCase(value.trim().charAt(0)) + ".";
    }

    private void section(Document document, String title) {
        PdfPTable heading = new PdfPTable(1);
        heading.setWidthPercentage(100);
        heading.setSpacingBefore(10);
        heading.setSpacingAfter(8);
        PdfPCell cell = new PdfPCell(new Phrase(title.toUpperCase(Locale.FRENCH), H2));
        cell.setBorder(PdfPCell.BOTTOM);
        cell.setBorderColor(BRAND_ACCENT);
        cell.setBorderWidth(0.6f);
        cell.setPadding(0);
        cell.setPaddingBottom(4);
        heading.addCell(cell);
        document.add(heading);
    }

    private Paragraph spacer() {
        Paragraph p = new Paragraph(" ", SMALL);
        p.setLeading(4);
        p.setSpacingAfter(0);
        return p;
    }

    private String formatPeriod(LocalDate start, LocalDate end) {
        if (start == null && end == null) {
            return "";
        }
        String startLabel = start != null ? MONTH_YEAR.format(start) : "";
        if (end == null) {
            return start != null ? "Depuis " + startLabel : "";
        }
        String endLabel = MONTH_YEAR.format(end);
        if (start == null) {
            return "Jusqu'a " + endLabel;
        }
        return startLabel + " - " + endLabel;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
