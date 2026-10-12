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
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import com.lowagie.text.Chunk;
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
import com.lowagie.text.pdf.PdfPageEventHelper;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
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
    private static final Color PAGE_BACKGROUND = new Color(253, 252, 248);
    private static final Color CARD_BG = PAGE_BACKGROUND;
    private static final Color FIELD_TAG_BACKGROUND = new Color(226, 226, 226);

    private static final Font H1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 24, BRAND_INK);
    private static final Font EXPERIENCE_SECTION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BRAND_INK);
    private static final Font SECTION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
    private static final Font FIELD_TAG = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, BRAND_INK);
    private static final Font COMPANY = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, BRAND_INK);
    private static final Font BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11.5f, BRAND_INK);
    private static final Font EXPERTISE = FontFactory.getFont("Helvetica-Oblique", 11.5f, BRAND_INK);
    private static final Font CONTACT_LINK = FontFactory.getFont(FontFactory.HELVETICA, 9,
            Font.UNDERLINE, new Color(0, 102, 204));
    private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 11.5f, INK_SOFT);
    private static final Font SMALL = FontFactory.getFont(FontFactory.HELVETICA, 9, BRAND_INK);
    private static final Font YEARS = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 24, BRAND_ACCENT);

    private final CompanySettingsService companySettingsService;
    private final S3ImageStorage storage;

    public PdfService(CompanySettingsService companySettingsService, S3ImageStorage storage) {
        this.companySettingsService = companySettingsService;
        this.storage = storage;
    }

    public byte[] generateDossierPdf(CompetenceDossier dossier) {
        Profile profile = dossier.getProfile();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 72, 72, 72, 48);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            PdfPTable header = companyHeader(document.right() - document.left(), profile, dossier.getHeadline());
            writer.setPageEvent(new PdfPageEventHelper() {
                @Override
                public void onEndPage(PdfWriter current, Document page) {
                    float top = page.getPageSize().getHeight() - 16;
                    var under = current.getDirectContentUnder();
                    under.saveState();
                    under.setColorFill(PAGE_BACKGROUND);
                    under.rectangle(0, 0, page.getPageSize().getWidth(), page.getPageSize().getHeight());
                    under.fill();
                    under.setColorFill(BRAND_ACCENT);
                    under.circle(page.getPageSize().getWidth(), 0, 43.5f);
                    under.fill();
                    under.circle(page.getPageSize().getWidth() - 67.2f, 30f, 7.5f);
                    under.fill();
                    under.restoreState();
                    var canvas = current.getDirectContent();
                    header.writeSelectedRows(0, -1, page.left(), top, canvas);
                    Image logo = referenceLogo();
                    if (logo != null) {
                        logo.scaleToFit(80, 28);
                        logo.setAbsolutePosition(page.right() + 30 - logo.getScaledWidth(),
                            top - (50 + logo.getScaledHeight()) / 2 - 11);
                        try {
                            canvas.addImage(logo);
                        } catch (com.lowagie.text.DocumentException e) {
                            throw new IllegalStateException("Could not render dossier logo", e);
                        }
                    }
                }
            });
            document.open();

            // Hero: experience first, followed by the dossier headline.
            if (dossier.getYearsOfExperience() != null) {
                Paragraph years = new Paragraph(dossier.getYearsOfExperience() + " ANS EXP", YEARS);
                years.setSpacingAfter(8);
                document.add(years);
            }
            Paragraph headline = new Paragraph();
            headline.add(new Phrase(hasText(dossier.getHeadline()) ? dossier.getHeadline() : initials(profile), H1));
            if (hasText(dossier.getExpertiseDomain())) {
                headline.add(new Phrase("  " + dossier.getExpertiseDomain().toUpperCase(Locale.FRENCH), EXPERTISE));
            }
            document.add(headline);
            document.add(spacer());

            // Summary
            if (hasText(dossier.getSummary())) {
                section(document, "Résumé");
                document.add(new Paragraph(dossier.getSummary(), NORMAL));
                document.add(spacer());
            }

            // Education & certifications
            if (!dossier.getEducations().isEmpty() || !dossier.getCertifications().isEmpty()) {
                section(document, "Formations et certifications");
                if (!dossier.getEducations().isEmpty()) {
                    addCredentialHeading(document, "Diplôme :");
                }
                for (Education edu : dossier.getEducations()) {
                    Paragraph p = new Paragraph();
                    p.setIndentationLeft(66);
                    p.add(new Phrase("o ", SMALL));
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
                if (!dossier.getCertifications().isEmpty()) {
                    addCredentialHeading(document, "Formations et certifications :");
                }
                for (Certification cert : dossier.getCertifications()) {
                    Paragraph p = new Paragraph();
                    p.setIndentationLeft(66);
                    p.add(new Phrase("o ", SMALL));
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
                skills.setWidthPercentage(120);
                skills.setHorizontalAlignment(Element.ALIGN_CENTER);
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
                Paragraph experienceTitle = new Paragraph("EXPÉRIENCES", EXPERIENCE_SECTION);
                experienceTitle.setSpacingBefore(6);
                experienceTitle.setSpacingAfter(0);
                document.add(experienceTitle);
                document.add(goldThread());
                List<ExperienceCompany> experiences = dossier.getExperiences();
                for (int experienceIndex = 0; experienceIndex < experiences.size(); experienceIndex++) {
                    ExperienceCompany exp = experiences.get(experienceIndex);
                    document.add(experienceHeader(exp));
                    if (hasText(exp.getRoleTitle())) {
                        fieldTag(document, "Rôle");
                        document.add(new Paragraph(exp.getRoleTitle(), BOLD));
                    }
                    if (hasText(exp.getContext())) {
                        fieldTag(document, "Projet");
                        document.add(new Paragraph(exp.getContext(), NORMAL));
                    }
                    for (ExperienceProject project : exp.getProjects()) {
                        fieldTag(document, "Projet");
                        if (hasText(project.getName())) {
                            document.add(new Paragraph(project.getName(), BOLD));
                        }
                        addLabelled(document, "Contexte", project.getContext());
                        addLabelled(document, "Équipe", project.getTeam());
                        addLabelled(document, "Méthodologie", project.getMethodology());
                        if (hasText(project.getTasks())) {
                            fieldTag(document, "Tâches");
                            com.lowagie.text.List tasks = new com.lowagie.text.List(false);
                            tasks.setIndentationLeft(18);
                            tasks.setListSymbol(new com.lowagie.text.Chunk("\u2022 ", NORMAL));
                            for (String task : project.getTasks().split("\\r?\\n")) {
                                if (!task.isBlank()) {
                                    tasks.add(new com.lowagie.text.ListItem(task.trim(), NORMAL));
                                }
                            }
                            document.add(tasks);
                        }
                        if (hasText(project.getTechnologies())) {
                            fieldTag(document, "Environnement");
                            document.add(new Paragraph(project.getTechnologies(), NORMAL));
                        }
                    }
                    if (experienceIndex + 1 < experiences.size()) {
                        document.add(goldThread());
                    }
                }
            }

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Erreur lors de la generation du PDF", e);
        }
    }

    private void addLabelled(Document document, String label, String value) throws com.lowagie.text.DocumentException {
        if (hasText(value)) {
            Paragraph p = new Paragraph();
            p.add(new Phrase(label + " : ", BOLD));
            p.add(new Phrase(value, NORMAL));
            document.add(p);
        }
    }

    private void addCredentialHeading(Document document, String label) throws com.lowagie.text.DocumentException {
        Paragraph heading = new Paragraph();
        heading.setIndentationLeft(22);
        heading.add(new Phrase("\u2022    ", BOLD));
        heading.add(new Phrase(label, BOLD));
        document.add(heading);
    }

    private PdfPTable experienceHeader(ExperienceCompany experience) {
        PdfPTable header = new PdfPTable(new float[]{1f, 1f, 1f});
        header.setWidthPercentage(100);
        header.setSpacingBefore(2);
        header.setSpacingAfter(14);
        header.addCell(experienceCell(safe(experience.getCompany()), "pin", COMPANY));
        header.addCell(experienceCell(ExperiencePeriodFormatter.formatDuration(
                experience.getStartDate(), experience.getEndDate()), "timer"));
        header.addCell(experienceCell(formatPeriod(experience.getStartDate(), experience.getEndDate()), "calendar"));
        return header;
    }

    private PdfPCell experienceCell(String value, String icon) {
        return experienceCell(value, icon, BOLD);
    }

    private PdfPCell experienceCell(String value, String icon, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(value, font));
        cell.setBorder(PdfPCell.NO_BORDER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPaddingLeft(19);
        cell.setPaddingTop(3);
        cell.setPaddingBottom(3);
        cell.setPaddingRight(2);
        cell.setCellEvent((current, bounds, canvases) -> drawExperienceIcon(
                canvases[PdfPTable.BACKGROUNDCANVAS], bounds, icon));
        return cell;
    }

    private void drawExperienceIcon(com.lowagie.text.pdf.PdfContentByte canvas,
                                    com.lowagie.text.Rectangle bounds, String icon) {
        float centerX = bounds.getLeft() + 8;
        float centerY = bounds.getBottom() + bounds.getHeight() / 2;
        canvas.saveState();
        canvas.setColorStroke(BRAND_ACCENT);
        canvas.setColorFill(BRAND_ACCENT);
        canvas.setLineWidth(1.2f);
        if ("pin".equals(icon)) {
            canvas.circle(centerX, centerY + 2, 4);
            canvas.fill();
            canvas.moveTo(centerX - 3, centerY + 1);
            canvas.lineTo(centerX, centerY - 6);
            canvas.lineTo(centerX + 3, centerY + 1);
            canvas.fill();
            canvas.setColorFill(PAGE_BACKGROUND);
            canvas.circle(centerX, centerY + 2, 1.4f);
            canvas.fill();
        } else if ("timer".equals(icon)) {
            canvas.circle(centerX, centerY, 5);
            canvas.stroke();
            canvas.rectangle(centerX - 1.5f, centerY + 5.5f, 3, 2);
            canvas.stroke();
            canvas.moveTo(centerX, centerY);
            canvas.lineTo(centerX + 2.5f, centerY + 2);
            canvas.moveTo(centerX, centerY);
            canvas.lineTo(centerX, centerY + 3.5f);
            canvas.stroke();
        } else {
            canvas.rectangle(centerX - 5, centerY - 5, 10, 11);
            canvas.stroke();
            canvas.moveTo(centerX - 5, centerY + 2);
            canvas.lineTo(centerX + 5, centerY + 2);
            canvas.moveTo(centerX - 2.5f, centerY + 7);
            canvas.lineTo(centerX - 2.5f, centerY + 4.5f);
            canvas.moveTo(centerX + 2.5f, centerY + 7);
            canvas.lineTo(centerX + 2.5f, centerY + 4.5f);
            canvas.stroke();
        }
        canvas.restoreState();
    }

    /** Framed content block for references and tests. */
    private PdfPCell cardCell() {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(CARD_BG);
        cell.setBorder(PdfPCell.NO_BORDER);
        cell.setPadding(6);
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

    private PdfPTable companyHeader(float width, Profile profile, String headline) {
        CompanyContactDto company = companySettingsService.get();

        PdfPTable header = new PdfPTable(1);
        header.setTotalWidth(width);
        header.setLockedWidth(true);
        header.setSpacingAfter(10);

        Paragraph contact = new Paragraph();
        contact.add(new Phrase(initials(profile) + (hasText(headline) ? " - " + headline : ""), SMALL));
        Paragraph details = new Paragraph();
        if (hasText(company.email())) {
            Chunk email = new Chunk(company.email(), CONTACT_LINK);
            email.setAnchor("mailto:" + company.email());
            details.add(email);
        }
        if (hasText(company.phone())) {
            if (details.size() > 0) {
                details.add(new Phrase(" - ", SMALL));
            }
            details.add(new Phrase(company.phone(), SMALL));
        }
        contact.add(new Phrase("\n", SMALL));
        contact.add(details);
        PdfPCell left = new PdfPCell(contact);
        left.setBorder(PdfPCell.NO_BORDER);
        left.setHorizontalAlignment(Element.ALIGN_LEFT);
        left.setVerticalAlignment(Element.ALIGN_MIDDLE);
        left.setMinimumHeight(50);
        left.setPaddingTop(8);
        left.setPaddingRight(0);
        left.setPaddingBottom(0);
        left.setPaddingLeft(0);
        header.addCell(left);
        return header;
    }

    private Image referenceLogo() {
        try (var in = getClass().getResourceAsStream("/inodoc-logo.png")) {
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

    private String initials(Profile p) {
        return java.util.Arrays.stream((safe(p.getFirstName()) + " " + safe(p.getLastName())).split("[\\s.]+"))
            .filter(part -> !part.isBlank()).map(this::initial).collect(Collectors.joining());
    }

    private String initial(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return String.valueOf(Character.toUpperCase(value.trim().charAt(0)));
    }

    private void section(Document document, String title) {
        String label = title.toUpperCase(Locale.FRENCH);
        PdfPTable heading = new PdfPTable(1);
        heading.setSpacingBefore(10);
        heading.setSpacingAfter(8);
        heading.setHorizontalAlignment(Element.ALIGN_LEFT);
        Image referenceLabel = referenceLabel(title);
        if (referenceLabel != null) {
            heading.setTotalWidth(referenceLabel.getScaledWidth());
            heading.setLockedWidth(true);
            PdfPCell cell = new PdfPCell(referenceLabel, false);
            cell.setBorder(PdfPCell.NO_BORDER);
            cell.setPadding(0);
            heading.addCell(cell);
            document.add(heading);
            return;
        }
        float labelWidth = SECTION.getCalculatedBaseFont(false).getWidthPoint(label, SECTION.getSize()) + 34;
        heading.setTotalWidth(labelWidth);
        heading.setLockedWidth(true);
        PdfPCell cell = new PdfPCell(new Phrase(label, SECTION));
        cell.setBorder(PdfPCell.NO_BORDER);
        cell.setPaddingLeft(8);
        cell.setPaddingRight(24);
        cell.setPaddingTop(4);
        cell.setPaddingBottom(4);
        cell.setCellEvent((current, bounds, canvases) -> {
            var canvas = canvases[PdfPTable.BACKGROUNDCANVAS];
            float radius = bounds.getHeight() / 2;
            float centerY = bounds.getBottom() + radius;
            float centerX = bounds.getRight() - radius;
            canvas.saveState();
            canvas.setColorFill(BRAND_INK);
            canvas.roundRectangle(bounds.getLeft(), bounds.getBottom(), bounds.getWidth(),
                bounds.getHeight(), radius);
            canvas.fill();
            canvas.setColorFill(BRAND_ACCENT);
            canvas.circle(centerX, centerY, Math.max(5, radius - 2));
            canvas.fill();
            canvas.setColorStroke(BRAND_INK);
            canvas.setLineWidth(1);
            canvas.moveTo(centerX - 3, centerY);
            canvas.lineTo(centerX + 2, centerY);
            canvas.lineTo(centerX, centerY + 2);
            canvas.moveTo(centerX + 2, centerY);
            canvas.lineTo(centerX, centerY - 2);
            canvas.stroke();
            canvas.restoreState();
        });
        heading.addCell(cell);
        document.add(heading);
    }

    private void fieldTag(Document document, String title) throws com.lowagie.text.DocumentException {
        String label = title.toUpperCase(Locale.FRENCH);
        PdfPTable heading = new PdfPTable(1);
        heading.setHorizontalAlignment(Element.ALIGN_LEFT);
        heading.setSpacingBefore(6);
        heading.setSpacingAfter(2);
        Image referenceLabel = referenceLabel(title);
        if (referenceLabel != null) {
            heading.setTotalWidth(referenceLabel.getScaledWidth());
            heading.setLockedWidth(true);
            PdfPCell cell = new PdfPCell(referenceLabel, false);
            cell.setBorder(PdfPCell.NO_BORDER);
            cell.setPadding(0);
            heading.addCell(cell);
            document.add(heading);
            return;
        }
        float labelWidth = FIELD_TAG.getCalculatedBaseFont(false).getWidthPoint(label, FIELD_TAG.getSize()) + 32;
        heading.setTotalWidth(labelWidth);
        heading.setLockedWidth(true);
        PdfPCell cell = new PdfPCell(new Phrase(label, FIELD_TAG));
        cell.setBorder(PdfPCell.NO_BORDER);
        cell.setPaddingLeft(6);
        cell.setPaddingRight(23);
        cell.setPaddingTop(3);
        cell.setPaddingBottom(3);
        cell.setCellEvent((current, bounds, canvases) -> {
            var canvas = canvases[PdfPTable.BACKGROUNDCANVAS];
            float radius = bounds.getHeight() / 2;
            float centerY = bounds.getBottom() + radius;
            float centerX = bounds.getRight() - radius;
            canvas.saveState();
            canvas.setColorFill(FIELD_TAG_BACKGROUND);
            canvas.roundRectangle(bounds.getLeft(), bounds.getBottom(), bounds.getWidth(),
                    bounds.getHeight(), radius);
            canvas.fill();
            canvas.setColorFill(BRAND_INK);
            canvas.circle(centerX, centerY, Math.max(5, radius - 2));
            canvas.fill();
            canvas.setColorStroke(Color.WHITE);
            canvas.setLineWidth(1);
            canvas.moveTo(centerX - 3, centerY);
            canvas.lineTo(centerX + 3, centerY);
            canvas.lineTo(centerX + 1, centerY + 2);
            canvas.moveTo(centerX + 3, centerY);
            canvas.lineTo(centerX + 1, centerY - 2);
            canvas.stroke();
            canvas.restoreState();
        });
        heading.addCell(cell);
        document.add(heading);
    }

    private PdfPTable goldThread() {
        PdfPTable thread = new PdfPTable(1);
        Image image = referenceSvgImage("thread.svg", 330, 35);
        if (image != null) {
            float width = 247.5f;
            image.scaleToFit(width, width * 35f / 330f);
            thread.setTotalWidth(width);
            thread.setLockedWidth(true);
            thread.setHorizontalAlignment(Element.ALIGN_LEFT);
            thread.setSpacingBefore(18);
            thread.setSpacingAfter(10);
            PdfPCell cell = new PdfPCell(image, false);
            cell.setBorder(PdfPCell.NO_BORDER);
            cell.setPadding(0);
            thread.addCell(cell);
            return thread;
        }
        thread.setWidthPercentage(100);
        thread.setSpacingBefore(18);
        thread.setSpacingAfter(10);
        PdfPCell cell = new PdfPCell();
        cell.setBorder(PdfPCell.NO_BORDER);
        cell.setPadding(0);
        cell.setFixedHeight(26);
        cell.setCellEvent((current, bounds, canvases) -> {
            var canvas = canvases[PdfPTable.BACKGROUNDCANVAS];
            float scale = 247.5f / 330f;
            float svgBottom = bounds.getBottom() + (bounds.getHeight() - 35f * scale) / 2;
            float originX = bounds.getLeft();
            canvas.saveState();
            canvas.setColorStroke(BRAND_ACCENT);
            canvas.setLineWidth(5f * scale);
            canvas.setLineCap(1);
            canvas.moveTo(originX + 2.50043f * scale, svgBottom + (35f - 17.2757f) * scale);
            canvas.curveTo(originX + 29.8338f * scale, svgBottom + (35f - 1.77573f) * scale,
                originX + 97.5004f * scale, svgBottom + (35f + 6.22426f) * scale,
                originX + 164f * scale, svgBottom + (35f - 17.2757f) * scale);
            canvas.curveTo(originX + 228.651f * scale, svgBottom + (35f - 40.122f) * scale,
                originX + 287.834f * scale, svgBottom + (35f - 32.7757f) * scale,
                originX + 317.5f * scale, svgBottom + (35f - 17.2757f) * scale);
            canvas.stroke();
            canvas.setColorFill(BRAND_ACCENT);
            canvas.circle(originX + 325.5f * scale, svgBottom + (35f - 11.2757f) * scale, 4f * scale);
            canvas.fill();
            canvas.restoreState();
        });
        thread.addCell(cell);
        return thread;
    }

    private Image referenceLabel(String title) {
        return switch (title.toLowerCase(Locale.FRENCH)) {
            case "résumé" -> referenceSvgImage("summary.svg", 105, 28);
            case "formations et certifications" -> referenceSvgImage("education.svg", 282, 28);
            case "compétences" -> referenceSvgImage("skills.svg", 155, 28);
            case "rôle" -> referenceSvgImage("role.svg", 83, 28);
            case "projet" -> referenceSvgImage("project.svg", 102, 28);
            case "tâches" -> referenceSvgImage("tasks.svg", 103, 28);
            case "environnement" -> referenceSvgImage("environment.svg", 170, 28);
            default -> null;
        };
    }

    private Image referenceSvgImage(String fileName, int width, int height) {
        try (InputStream source = getClass().getResourceAsStream("/dossier-labels/" + fileName)) {
            if (source == null) return null;
            float rasterScale = 4f;
            PNGTranscoder transcoder = new PNGTranscoder();
            transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, width * rasterScale);
            transcoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, height * rasterScale);
            ByteArrayOutputStream raster = new ByteArrayOutputStream();
            transcoder.transcode(new TranscoderInput(source), new TranscoderOutput(raster));
            Image image = Image.getInstance(raster.toByteArray());
            image.scalePercent(18.75f);
            return image;
        } catch (Exception e) {
            log.warn("Dossier SVG omitted from PDF: asset={}, type={}", fileName,
                    e.getClass().getSimpleName());
            return null;
        }
    }

    private Paragraph spacer() {
        Paragraph p = new Paragraph(" ", SMALL);
        p.setLeading(4);
        p.setSpacingAfter(0);
        return p;
    }

    private String formatPeriod(LocalDate start, LocalDate end) {
        return ExperiencePeriodFormatter.formatPeriod(start, end);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
