/*
 * This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package fr.neatmonster.nocheatplus.utilities.build;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.net.URL;
import java.util.Map;

public class ResourceUtil {

    private ResourceUtil() {
    }

    /**
     * Fetch a resource from within the plugin JAR.
     * Might have a newline at the end.
     *
     * @param clazz
     *            reference class for resolving the JAR location
     * @param path
     *            the path inside the JAR
     * @return content of the resource or {@code null} if not available
     */
    public static String fetchResource(final Class<?> clazz, final String path) {
        final String className = clazz.getSimpleName() + ".class";
        final String classPath = clazz.getResource(className).toString();
        if (!classPath.startsWith("jar")) {
            return null;
        }
        final String absPath = classPath.substring(0, classPath.lastIndexOf('!') + 1)
                + "/" + path;
        try {
            final URL url = new URL(absPath);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader((InputStream) url.getContent(), StandardCharsets.UTF_8))) {
                final StringBuilder builder = new StringBuilder();
                String last = reader.readLine();
                while (last != null) {
                    builder.append(last).append('\n');
                    last = reader.readLine();
                }
                return builder.toString();
            }
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * New line separated entries, lines starting with '#' are ignored (trim +
     * check), otherwise ini-file style x=y.<br>
     * All keys and values are trimmed, lines without assignment still get added,
     * all mappings will be the empty string or some content.
     *
     * @param input
     * @param map
     */
    public static void parseToMap(final String input, final Map<String, String> map) {
        final String[] split = input.split("\n");
        for (final String line : split) {
            final String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            final String[] parts = line.split("=", 2);
            if (parts.length == 1) {
                map.put(parts[0].trim(), "");
            } else {
                map.put(parts[0].trim(), parts[1].trim());
            }
        }
    }

    /**
     * Parse a boolean value from a string.
     *
     * @param input
     *            the string to parse
     * @param preset
     *            value to return if parsing fails
     * @return the parsed value or the preset if parsing fails
     */
    public static Boolean getBoolean(final String input, final Boolean preset) {
        if (input == null) {
            return preset;
        }
        final String trimmed = input.trim().toLowerCase();
        if (trimmed.matches("1|true|yes")) {
            return true;
        } else if (trimmed.matches("0|false|no")) {
            return false;
        } else {
            return preset;
        }
    }
    /**
     * Parse an integer from a string.
     *
     * @param input
     *            the string to parse
     * @param preset
     *            value to return if parsing fails
     * @return the parsed integer or the preset if parsing fails
     */
    public static Integer getInteger(final String input, final Integer preset) {
        if (input == null) {
            return preset;
        }
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return preset;
        }
    }
}
