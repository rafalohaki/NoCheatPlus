package fr.neatmonster.nocheatplus;

import fr.neatmonster.nocheatplus.hooks.allviolations.AllViolationsHook;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.access.IViolationInfo;
import fr.neatmonster.nocheatplus.logging.LogManager;
import org.bukkit.entity.Player;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Tests for {@link AllViolationsHook}.
 */
public class TestAllViolationsHook {

    private static Player createPlayer(String name, String displayName) {
        InvocationHandler h = (proxy, method, args) -> {
            switch (method.getName()) {
                case "getName":
                    return name;
                case "getDisplayName":
                    return displayName;
                default:
                    Class<?> r = method.getReturnType();
                    if (r == boolean.class) return false;
                    if (r.isPrimitive()) return 0;
                    return null;
            }
        };
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class[]{Player.class}, h);
    }

    private static class DummyAPI extends PluginTests.UnitTestNoCheatPlusAPI {
        final LogManager logManager = (LogManager) Proxy.newProxyInstance(
                LogManager.class.getClassLoader(), new Class[]{LogManager.class}, (p, m, a) -> null);

        @Override
        public LogManager getLogManager() {
            return logManager;
        }
    }

    @Test
    public void testNullDisplayNameLogging() throws Exception {
        NCPAPIProvider.setNoCheatPlusAPI(new DummyAPI());
        AllViolationsHook hook = new AllViolationsHook();
        Method log = AllViolationsHook.class.getDeclaredMethod("log", CheckType.class, Player.class, IViolationInfo.class, boolean.class, boolean.class);
        log.setAccessible(true);
        IViolationInfo info = (IViolationInfo) Proxy.newProxyInstance(
                IViolationInfo.class.getClassLoader(), new Class[]{IViolationInfo.class},
                (p, m, a) -> {
                    switch (m.getName()) {
                        case "getTotalVl":
                        case "getAddedVl":
                            return 0.0;
                        default:
                            return null;
                    }
                });
        Player player = createPlayer("Dummy", null);
        // Should not throw.
        log.invoke(hook, CheckType.ALL, player, info, false, false);
    }
}
