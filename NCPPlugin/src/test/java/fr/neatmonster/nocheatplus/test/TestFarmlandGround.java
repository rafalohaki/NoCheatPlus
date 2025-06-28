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
package fr.neatmonster.nocheatplus.test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import fr.neatmonster.nocheatplus.compat.BridgeMaterial;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import fr.neatmonster.nocheatplus.utilities.location.RichBoundsLocation;
import fr.neatmonster.nocheatplus.utilities.map.FakeBlockCache;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verify that farmland counts as ground in RichBoundsLocation.
 */
public class TestFarmlandGround {

    public TestFarmlandGround() {
        StaticLog.setUseLogManager(false);
        BlockTests.initBlockProperties();
        StaticLog.setUseLogManager(true);
    }

    private static World createWorld() {
        InvocationHandler h = (proxy, method, args) -> {
            if ("getName".equals(method.getName())) return "dummy";
            Class<?> r = method.getReturnType();
            if (r == boolean.class) return false;
            if (r.isPrimitive()) return 0;
            return null;
        };
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class[]{World.class}, h);
    }

    @Test
    public void testRichLocationOnFarmland() {
        FakeBlockCache bc = new FakeBlockCache();
        bc.set(0, 0, 0, BridgeMaterial.FARMLAND);
        World world = createWorld();
        Location loc = new Location(world, 0.5, 1.0, 0.5);
        RichBoundsLocation rloc = new RichBoundsLocation(bc);
        rloc.set(loc, 0.6, 1.8, 0.001);
        assertTrue("Farmland should count as ground.", rloc.isOnGround());
        bc.cleanup();
    }
}
