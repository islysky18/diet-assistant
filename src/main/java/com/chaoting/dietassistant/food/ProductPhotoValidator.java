package com.chaoting.dietassistant.food;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

@Component
class ProductPhotoValidator {

    private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    private static final Set<String> HEIC_BRANDS = Set.of("heic", "heix", "heim", "heis");
    private static final Set<String> HEIF_BRANDS = Set.of("mif1");

    ProductPhotoFormat validate(MultipartFile photo) throws IOException {
        String extension = extension(photo.getOriginalFilename());
        String mime = photo.getContentType() == null ? "" : photo.getContentType().toLowerCase(Locale.ROOT);
        ProductPhotoFormat expected = switch (extension) {
            case "jpg", "jpeg" -> ProductPhotoFormat.JPEG;
            case "png" -> ProductPhotoFormat.PNG;
            case "heic" -> ProductPhotoFormat.HEIC;
            case "heif" -> ProductPhotoFormat.HEIF;
            default -> throw new InvalidPhotoException("Photo must be JPG, JPEG, PNG, HEIC, or HEIF.");
        };
        if (!expected.mimeType.equals(mime)) {
            throw new InvalidPhotoException("Photo MIME type does not match its extension.");
        }
        byte[] header;
        try (InputStream input = photo.getInputStream()) {
            header = input.readNBytes(4096);
        }
        ProductPhotoFormat actual = detect(header, expected);
        if (actual != expected) {
            throw new InvalidPhotoException("Photo contents do not match its extension and MIME type.");
        }
        return actual;
    }

    private ProductPhotoFormat detect(byte[] bytes, ProductPhotoFormat expected) {
        if (startsWith(bytes, JPEG)) return ProductPhotoFormat.JPEG;
        if (startsWith(bytes, PNG)) return ProductPhotoFormat.PNG;
        if (expected != ProductPhotoFormat.HEIC && expected != ProductPhotoFormat.HEIF) return null;
        Set<String> brands = readFtypBrands(bytes);
        if (expected == ProductPhotoFormat.HEIC && brands.stream().anyMatch(HEIC_BRANDS::contains)) return ProductPhotoFormat.HEIC;
        if (expected == ProductPhotoFormat.HEIF && brands.stream().anyMatch(HEIF_BRANDS::contains)) return ProductPhotoFormat.HEIF;
        return null;
    }

    private Set<String> readFtypBrands(byte[] bytes) {
        if (bytes.length < 16) return Set.of();
        long boxSize = Integer.toUnsignedLong(ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.BIG_ENDIAN).getInt());
        if (!"ftyp".equals(ascii(bytes, 4)) || boxSize < 16 || boxSize > bytes.length || (boxSize - 16) % 4 != 0) return Set.of();
        java.util.HashSet<String> brands = new java.util.HashSet<>();
        brands.add(ascii(bytes, 8));
        for (int offset = 16; offset + 4 <= boxSize; offset += 4) brands.add(ascii(bytes, offset));
        return Set.copyOf(brands);
    }

    private String ascii(byte[] bytes, int offset) {
        return new String(bytes, offset, 4, StandardCharsets.US_ASCII);
    }

    private boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (bytes[i] != prefix[i]) return false;
        return true;
    }

    private String extension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    static class InvalidPhotoException extends IllegalArgumentException {
        InvalidPhotoException(String message) { super(message); }
    }
}
