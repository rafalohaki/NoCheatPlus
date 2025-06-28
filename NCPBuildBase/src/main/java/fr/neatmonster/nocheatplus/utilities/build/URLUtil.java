package fr.neatmonster.nocheatplus.utilities.build;

import java.net.URL;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Utility methods for working with URLs.
 */
public final class URLUtil {

    private static final Pattern JAR_PATTERN = Pattern.compile("\\.jar(?=([!?#]|$))", Pattern.CASE_INSENSITIVE);

    private URLUtil() {
    }

    /**
     * Determine if the supplied path points to a JAR file. The check ignores
     * any trailing {@code !}, query or fragment segments.
     *
     * @param path
     *            the path to check
     * @return {@code true} if the path contains ".jar" before any of the
     *         optional segments
     */
    public static boolean isJarURL(String path) {
        if (path == null) {
            return false;
        }
        return JAR_PATTERN.matcher(path).find();
    }

    /**
     * Determine if the supplied URL points to a JAR file. The check ignores any
     * trailing {@code !}, query or fragment segments.
     *
     * @param url
     *            the URL to check
     * @return {@code true} if the URL refers to a JAR file
     */
    public static boolean isJarURL(URL url) {
        return url != null && isJarURL(url.toString());
    }
}
