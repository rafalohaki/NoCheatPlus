package fr.neatmonster.nocheatplus.support;

import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Entity;

import fr.neatmonster.nocheatplus.utilities.ReflectionUtil;

/**
 * Central registry caching results of runtime feature detection.
 */
public final class FeatureSupportRegistry {

    /** Cache for feature support flags. */
    private static final Map<Feature, Boolean> SUPPORT = new EnumMap<>(Feature.class);

    private FeatureSupportRegistry() {
        // static only
    }

    /**
     * Determine if the given feature is supported.
     * Results are cached after the first check.
     *
     * @param feature the feature to query
     * @return {@code true} if the feature is supported
     */
    public static boolean isSupported(Feature feature) {
        Boolean cached = SUPPORT.get(feature);
        if (cached != null) {
            return cached.booleanValue();
        }
        boolean res;
        switch (feature) {
            case VEHICLE_MULTI_PASSENGER:
                res = detectVehicleMultiPassenger();
                break;
            default:
                res = false;
        }
        SUPPORT.put(feature, Boolean.valueOf(res));
        return res;
    }

    /**
     * For tests only: clear cached feature results.
     */
    public static void clearCache() {
        SUPPORT.clear();
    }

    private static boolean detectVehicleMultiPassenger() {
        Method getPassengers = ReflectionUtil.getMethodNoArgs(Entity.class, "getPassengers");
        if (getPassengers == null || !List.class.isAssignableFrom(getPassengers.getReturnType())) {
            return false;
        }
        Method addPassenger = ReflectionUtil.getMethod(Entity.class, "addPassenger", Entity.class);
        if (addPassenger == null) {
            return false;
        }
        Class<?> returnType = addPassenger.getReturnType();
        return returnType == boolean.class || returnType == void.class;
    }
}
