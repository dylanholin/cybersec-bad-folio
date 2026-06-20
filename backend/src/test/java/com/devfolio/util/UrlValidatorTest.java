package com.devfolio.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UrlValidatorTest {

    @Test
    void fetchContent_shouldRejectHttpUrl() {
        assertThrows(IllegalArgumentException.class,
                () -> UrlValidator.fetchContent("http://github.com/user/repo"));
    }

    @Test
    void fetchContent_shouldRejectUnknownHost() {
        assertThrows(IllegalArgumentException.class,
                () -> UrlValidator.fetchContent("https://evil.com/image.png"));
    }

    @Test
    void fetchContent_shouldRejectMetadataIp() {
        assertThrows(IllegalArgumentException.class,
                () -> UrlValidator.fetchContent("https://169.254.169.254/latest/meta-data/"));
    }

    @Test
    void fetchContent_shouldRejectMalformedUrl() {
        assertThrows(IllegalArgumentException.class,
                () -> UrlValidator.fetchContent("not-a-url"));
    }

    @Test
    void getMaxFetchSize_shouldReturn2MB() {
        assertEquals(2 * 1024 * 1024, UrlValidator.getMaxFetchSize());
    }
}
