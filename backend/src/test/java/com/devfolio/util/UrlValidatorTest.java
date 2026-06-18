package com.devfolio.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UrlValidatorTest {

    @Test
    void validate_shouldAcceptAllowedHttpsHost() {
        assertNotNull(UrlValidator.validate("https://github.com/user/repo"));
    }

    @Test
    void validate_shouldRejectHttpUrl() {
        assertThrows(IllegalArgumentException.class,
                () -> UrlValidator.validate("http://github.com/user/repo"));
    }

    @Test
    void validate_shouldRejectUnknownHost() {
        assertThrows(IllegalArgumentException.class,
                () -> UrlValidator.validate("https://evil.com/image.png"));
    }

    @Test
    void validate_shouldRejectMetadataIp() {
        assertThrows(IllegalArgumentException.class,
                () -> UrlValidator.validate("https://169.254.169.254/latest/meta-data/"));
    }

    @Test
    void validate_shouldRejectMalformedUrl() {
        assertThrows(IllegalArgumentException.class,
                () -> UrlValidator.validate("not-a-url"));
    }

    @Test
    void getMaxFetchSize_shouldReturn2MB() {
        assertEquals(2 * 1024 * 1024, UrlValidator.getMaxFetchSize());
    }
}
