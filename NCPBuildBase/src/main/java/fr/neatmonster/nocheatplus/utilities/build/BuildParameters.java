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

import java.util.HashMap;
import java.util.Map;

/**
 * Support for parameters present or set at building time.
 * They are read from BuildParameters.properties
 * @author mc_dev
 *
 */
public class BuildParameters {

    /**
     * Raw contents of {@code BuildParameters.properties} as key-value map.
     */
    private static final Map<String, String> FILE_CONTENTS = new HashMap<String, String>();

    private BuildParameters() {
    }

    static {
        // Fetch file content from resources.
        String content = null;
        try {
            content = ResourceUtil.fetchResource(BuildParameters.class,
                    "BuildParameters.properties");
        } catch (Throwable t) {
            t.printStackTrace();
        }
        // Parse properties.
        if (content != null) {
            ResourceUtil.parseToMap(content, FILE_CONTENTS);
        }
    }

    //////////////////////
    // Auxiliary methods.
    /////////////////////

    /**
     * This gets the raw mapping value.
     * Might be something like "${...}" if the parameter has not been present
     * during building.
     * @param path
     * @param preset
     * @return
     */
    public static String getMappingValue(final String path, final String preset) {
        final String input = FILE_CONTENTS.get(path);
        if (input == null) {
            return preset;
        } else {
            return input;
        }
    }

    /**
     * Get a string mapping value, excluding missing maven build parameters like '${...}'.
     * @param path
     * @param preset
     * @return
     */
    public static String getString(final String path, final String preset) {
        final String input = FILE_CONTENTS.get(path);
        if (input == null) {
            return preset;
        } else if (input.startsWith("${") && input.endsWith("}")) {
            return preset;
        } else {
            return input;
        }
    }

    public static Boolean getBoolean(final String path, final Boolean preset) {
        final String input = FILE_CONTENTS.get(path);
        if (input == null) {
            return preset;
        } else {
            return ResourceUtil.getBoolean(input, preset);
        }
    }

    public static Integer getInteger(final String path, final Integer preset) {
        final String input = FILE_CONTENTS.get(path);
        if (input == null) {
            return preset;
        } else {
            return ResourceUtil.getInteger(input, preset);
        }
    }

    //////////////////////
    // Public members.
    //////////////////////

    /** Timestamp from build (maven). "?" if not present. */
    public static final String BUILD_TIME_STRING = getString("BUILD_TIMESTAMP", "?");

    /** Indicate something about where this was built. */
    public static final String BUILD_SERIES = getString("BUILD_SERIES", "?");

    /** The build number as given by Jenkins. Integer.MIN_VALUE if not present. */
    public static final int BUILD_NUMBER = getInteger("BUILD_NUMBER", Integer.MIN_VALUE);

    /**
     * Test level: more testing for higher levels. Defaults to 0.
     * <hr>
     * Currently only 0 and 1 are used.
     * Later there might be more levels and some general policy for level setup
     * (concerning rough time needed on some reference hardware, console output
     * etc.).
     * Compare to debugLevel. 
     * 
     */
    public static final int TEST_LEVEL = getInteger("TEST_LEVEL", 0);

    /**
     * Debug level: more debug output for higher levels. Defaults to 0.
     * <hr>
     * Currently only 0 and 1 are used. At some point this will follow some
     * guidelines (to be documented here):
     * <li>0 is meant for few output, just enough for user debug reports or
     * simple testing.</li>
     * <li>There are major levels every 100 units (100, 200, ....)</li>
     * <li>Consequently minor levels are between major levels to distinguish
     * minor differences like flags</li>
     * 
     */
    public static final int DEBUG_LEVEL = getInteger("DEBUG_LEVEL", 10000);

}
