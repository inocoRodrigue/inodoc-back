package com.esn.inodoc.service;

import com.esn.inodoc.controller.error.BadRequestException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;

@Service
public class ResumeTextExtractor {
    static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    static final int MAX_TEXT_LENGTH = 100_000;

    public String extract(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_FILE_BYTES) {
            throw new BadRequestException("Le CV doit contenir entre 1 octet et 10 Mo");
        }
        String name = file.getOriginalFilename();
        String extension = name == null ? "" : name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!java.util.Set.of("pdf", "doc", "docx").contains(extension)) {
            throw new BadRequestException("Formats acceptés : PDF, DOC et DOCX");
        }
        try {
            byte[] bytes;
            try (var input = file.getInputStream()) {
                bytes = input.readNBytes((int) MAX_FILE_BYTES + 1);
            }
            if (bytes.length > MAX_FILE_BYTES) {
                throw new BadRequestException("Le CV dépasse 10 Mo");
            }
            String text = switch (extension) {
                case "pdf" -> extractPdf(bytes);
                case "doc" -> extractDoc(bytes);
                case "docx" -> extractDocx(bytes);
                default -> throw new IllegalStateException("Format non validé");
            };
            if (text == null || text.isBlank()) {
                throw new BadRequestException("Aucun texte lisible dans le CV. Les documents scannés nécessitent un OCR.");
            }
            if (text.length() > MAX_TEXT_LENGTH) {
                throw new BadRequestException("Le CV contient trop de texte (100 000 caractères maximum)");
            }
            return text;
        } catch (IOException | org.apache.poi.UnsupportedFileFormatException
                 | org.apache.poi.EmptyFileException | org.apache.poi.EncryptedDocumentException
                 | org.apache.poi.ooxml.POIXMLException ex) {
            throw new BadRequestException("Document illisible, protégé ou incompatible avec son extension");
        }
    }

    private String extractPdf(byte[] bytes) throws IOException {
        try (var document = Loader.loadPDF(bytes)) {
            if (document.getNumberOfPages() > 100 || !document.getCurrentAccessPermission().canExtractContent()) {
                throw new BadRequestException("PDF protégé ou supérieur à 100 pages");
            }
            var text = new StringBuilder();
            var writer = new java.io.Writer() {
                @Override
                public void write(char[] chars, int offset, int length) {
                    if (text.length() + length > MAX_TEXT_LENGTH) {
                        throw new BadRequestException("Le CV contient trop de texte (100 000 caractères maximum)");
                    }
                    text.append(chars, offset, length);
                }
                @Override public void flush() {}
                @Override public void close() {}
            };
            new PDFTextStripper().writeText(document, writer);
            return text.toString();
        }
    }

    private String extractDoc(byte[] bytes) throws IOException {
        try (var document = new HWPFDocument(new ByteArrayInputStream(bytes));
             var extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String extractDocx(byte[] bytes) throws IOException {
        validateArchive(bytes);
        ZipSecureFile.setMaxEntrySize(20L * 1024 * 1024);
        ZipSecureFile.setMaxTextSize(MAX_TEXT_LENGTH);
        try (var document = new XWPFDocument(new ByteArrayInputStream(bytes));
             var extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private void validateArchive(byte[] bytes) throws IOException {
        try (var zip = new java.util.zip.ZipInputStream(new ByteArrayInputStream(bytes))) {
            long expanded = 0;
            int entries = 0;
            byte[] buffer = new byte[8192];
            while (zip.getNextEntry() != null) {
                if (++entries > 1000) throw new BadRequestException("Le document Word contient trop de fichiers");
                int count;
                while ((count = zip.read(buffer)) != -1) {
                    expanded += count;
                    if (expanded > 25L * 1024 * 1024) {
                        throw new BadRequestException("Le document Word décompressé dépasse 25 Mo");
                    }
                }
            }
            if (entries == 0) throw new BadRequestException("Archive Word invalide");
        }
    }
}
