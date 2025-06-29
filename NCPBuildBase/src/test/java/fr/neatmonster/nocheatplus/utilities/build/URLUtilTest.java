package fr.neatmonster.nocheatplus.utilities.build;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for the {@link URLUtil} class, focusing on the isJarURL method.
 */
@DisplayName("URLUtil.isJarURL() Tests")
public class URLUtilTest {

    /**
     * Provides a stream of URL strings that should be correctly identified as JAR URLs.
     */
    static Stream<String> validJarUrlProvider() {
        return Stream.of(
                "jar:file:/path/to/some-lib.jar!/resource.txt", // Standard JAR URL
                "file:/path/plugin.jar?version=1.0!/resource.txt", // JAR URL with a query string before the separator
                "jar:https://example.com/remote.jar!/com/example/MyClass.class" // Remote JAR URL
        );
    }

    @ParameterizedTest
    @MethodSource("validJarUrlProvider")
    @DisplayName("should return true for valid JAR URL strings")
    void shouldCorrectlyIdentifyValidJarUrls(String urlString) {
        assertTrue(URLUtil.isJarURL(urlString), "Expected '" + urlString + "' to be identified as a JAR URL.");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "file:/path/plugin.zip", // Not a .jar file
            "http://example.com/resource", // Standard HTTP URL
            "file:/path/to/a/file.txt", // File URL without a JAR separator
            "just-a-random-string" // Not a URL
    })
    @NullAndEmptySource
    @DisplayName("should return false for non-JAR URL strings")
    void shouldCorrectlyRejectNonJarUrls(String urlString) {
        assertFalse(URLUtil.isJarURL(urlString), "Expected '" + urlString + "' to be rejected as a non-JAR URL.");
    }

    @Test
    @DisplayName("should return true for a URL object representing a JAR")
    void shouldReturnTrueForJarUrlObject() throws MalformedURLException {
        // This test specifically verifies the overload that accepts a URL object.
        URL url = new URL("jar:file:/path/to/another-lib.jar!/res.txt");
        assertTrue(URLUtil.isJarURL(url), "Expected the URL object to be identified as a JAR URL.");
    }

    @Test
    @DisplayName("should return false for a non-JAR URL object")
    void shouldReturnFalseForNonJarUrlObject() throws MalformedURLException {
        // This test specifically verifies the overload that accepts a URL object.
        URL url = new URL("file:/path/to/some.zip");
        assertFalse(URLUtil.isJarURL(url), "Expected the non-JAR URL object to be rejected.");
    }
}