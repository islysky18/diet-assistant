package com.chaoting.dietassistant.food;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductPhotoValidatorTest {

    private final ProductPhotoValidator validator = new ProductPhotoValidator();

    @Test
    void acceptsHeicAndHeifIsoBmffBrands() throws Exception {
        assertThat(validator.validate(photo("front.heic", "image/heic", ftyp("heic", "mif1", "heic"))))
                .isEqualTo(ProductPhotoFormat.HEIC);
        assertThat(validator.validate(photo("facts.heif", "image/heif", ftyp("mif1", "mif1", "heix"))))
                .isEqualTo(ProductPhotoFormat.HEIF);
    }

    @Test
    void rejectsSequenceOnlyTruncatedAndSpoofedHeif() {
        assertThatThrownBy(() -> validator.validate(photo("facts.heif", "image/heif", ftyp("msf1", "msf1"))))
                .isInstanceOf(ProductPhotoValidator.InvalidPhotoException.class);
        assertThatThrownBy(() -> validator.validate(photo("facts.heic", "image/heic", new byte[]{0, 0, 0, 24, 'f', 't'})))
                .isInstanceOf(ProductPhotoValidator.InvalidPhotoException.class);
        assertThatThrownBy(() -> validator.validate(photo("facts.jpg", "image/jpeg", ftyp("heic", "heic"))))
                .isInstanceOf(ProductPhotoValidator.InvalidPhotoException.class);
        assertThatThrownBy(() -> validator.validate(photo("facts.heic", "image/heif", ftyp("heic", "heic"))))
                .isInstanceOf(ProductPhotoValidator.InvalidPhotoException.class);
    }

    private MockMultipartFile photo(String name, String mime, byte[] bytes) {
        return new MockMultipartFile("photo", name, mime, bytes);
    }

    private byte[] ftyp(String major, String... compatible) {
        ByteBuffer buffer = ByteBuffer.allocate(16 + compatible.length * 4).order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(buffer.capacity()).put("ftyp".getBytes(StandardCharsets.US_ASCII));
        buffer.put(major.getBytes(StandardCharsets.US_ASCII)).putInt(0);
        for (String brand : compatible) buffer.put(brand.getBytes(StandardCharsets.US_ASCII));
        return buffer.array();
    }
}
