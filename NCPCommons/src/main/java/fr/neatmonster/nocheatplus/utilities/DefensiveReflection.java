package fr.neatmonster.nocheatplus.utilities;

import java.util.Optional;

/**
 * Utility class providing defensive wrappers around reflection calls.
 */
public final class DefensiveReflection {

    private DefensiveReflection() {
        // Utility class
    }

    /**
     * Attempts to load the class by name using {@link ReflectionUtil#getClass(String)}
     * and wraps the result in an {@link Optional}.
     *
     * @param fullName fully qualified class name
     * @return optional containing the class if found
     */
    public static Optional<Class<?>> getClassOptional(final String fullName) {
        return Optional.ofNullable(ReflectionUtil.getClass(fullName));
    }
}
