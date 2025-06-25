package fr.neatmonster.nocheatplus.compat.meta;

import java.util.Optional;

import fr.neatmonster.nocheatplus.compat.cbreflect.reflect.ReflectBase;
import fr.neatmonster.nocheatplus.compat.cbreflect.reflect.ReflectHelper.ReflectFailureException;
import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.logging.Streams;

/**
 * Factory class for creating {@link BridgeCrossPlugin} instances in a
 * fail-safe manner.
 */
public final class BridgeCrossPluginLoader {

    private BridgeCrossPluginLoader() {
        // Utility class
    }

    /**
     * Attempt to create a new {@link BridgeCrossPlugin} using a reflective base.
     *
     * @return an optional containing the plugin instance if initialization
     *         succeeds
     */
    public static Optional<BridgeCrossPlugin> load() {
        return createReflectBase().map(BridgeCrossPlugin::new);
    }

    static Optional<ReflectBase> createReflectBase() {
        try {
            return Optional.of(new ReflectBase());
        } catch (ReflectFailureException e) {
            NCPAPIProvider.getNoCheatPlusAPI().getLogManager().debug(Streams.STATUS, e);
            return Optional.empty();
        } catch (RuntimeException e) {
            NCPAPIProvider.getNoCheatPlusAPI().getLogManager().debug(Streams.STATUS, e);
            return Optional.empty();
        }
    }
}
