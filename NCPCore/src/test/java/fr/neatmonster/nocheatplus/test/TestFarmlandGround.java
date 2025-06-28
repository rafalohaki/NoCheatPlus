package fr.neatmonster.nocheatplus.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import fr.neatmonster.nocheatplus.compat.BridgeMaterial;
import fr.neatmonster.nocheatplus.utilities.location.RichBoundsLocation;
import fr.neatmonster.nocheatplus.utilities.map.BlockFlags;
import fr.neatmonster.nocheatplus.utilities.map.FakeBlockCache;

public class TestFarmlandGround {

    private static World createWorld() {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class[]{World.class},
                (proxy, method, args) -> {
                    if ("getName".equals(method.getName())) return "dummy";
                    Class<?> r = method.getReturnType();
                    if (r == boolean.class) return false;
                    if (r.isPrimitive()) return 0;
                    return null;
                });
    }

    @Test
    public void testRichBoundsLocationOnFarmland() {
        FakeBlockCache bc = new FakeBlockCache();
        bc.set(0, 0, 0, BridgeMaterial.FARMLAND);
        // Ensure farmland has ground flags similar to normal initialization
        BlockFlags.setBlockFlags(BridgeMaterial.FARMLAND,
                BlockFlags.F_GROUND | BlockFlags.F_GROUND_HEIGHT | BlockFlags.F_MIN_HEIGHT16_15 | BlockFlags.F_HEIGHT100);

        World world = createWorld();
        RichBoundsLocation loc = new RichBoundsLocation(bc);
        loc.set(new Location(world, 0.5, 1.0, 0.5), 0.6, 1.8, 0.001);

        assertTrue(loc.isOnGround(), "Expected farmland to count as ground");

        loc.cleanup();
        bc.cleanup();
    }
}
