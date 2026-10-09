package com.esn.inodoc.service;

import com.esn.inodoc.controller.error.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;

@Service
public class ImageValidator {
    private static final int MAX_BYTES = 5 * 1024 * 1024;

    public record ValidatedImage(byte[] bytes, String contentType, String extension) {}

    public ValidatedImage validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw new BadRequestException("L'image doit contenir entre 1 octet et 5 Mo");
        }
        try {
            byte[] bytes;
            try (var stream = file.getInputStream()) {
                bytes = stream.readNBytes(MAX_BYTES + 1);
            }
            if (bytes.length > MAX_BYTES) throw new BadRequestException("L'image dépasse 5 Mo");
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new BadRequestException("Le fichier n'est pas une image lisible");
                var reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    if (!Set.of("jpeg", "jpg", "png", "webp").contains(format)) {
                        throw new BadRequestException("Formats acceptés : JPEG, PNG et WebP");
                    }
                    long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                    if (pixels <= 0 || pixels > 16_000_000) {
                        throw new BadRequestException("L'image dépasse 16 millions de pixels");
                    }
                    if (reader.read(0) == null) throw new BadRequestException("Image illisible");
                    String type = format.equals("jpg") ? "jpeg" : format;
                    return new ValidatedImage(bytes, "image/" + type, type);
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException ex) {
            throw new BadRequestException("Image endommagée ou illisible");
        }
    }
}
