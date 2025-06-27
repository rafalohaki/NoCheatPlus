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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import fr.neatmonster.nocheatplus.actions.ActionList;
// We need the Check class to access its static field.
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.checks.blockinteract.BlockInteractData;
import fr.neatmonster.nocheatplus.checks.blockplace.Against;
import fr.neatmonster.nocheatplus.checks.blockplace.BlockPlaceConfig;
import fr.neatmonster.nocheatplus.checks.blockplace.BlockPlaceData;
import fr.neatmonster.nocheatplus.compat.BridgeMaterial;
import fr.neatmonster.nocheatplus.permissions.PermissionRegistry;
import fr.neatmonster.nocheatplus.permissions.RegisteredPermission;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.players.PlayerDataManager;
import fr.neatmonster.nocheatplus.worlds.WorldDataManager;

public class TestBlockPlaceAgainst {

    private static class TestableAgainst extends Against {
        @Override
        public ViolationData executeActions(ViolationData violationData) {
            violationData.forceCancel();
            return violationData;
        }
    }

    private BlockPlaceConfig config;
    private BlockPlaceData data;
    private Against against;

    @Before
    public void setup() throws Exception {
        fr.neatmonster.nocheatplus.compat.versions.ServerVersion.setMinecraftVersion("1.8");
        // Use Unsafe to initialize the config object for the test
        sun.misc.Unsafe unsafe;
        Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        uf.setAccessible(true);
        unsafe = (sun.misc.Unsafe) uf.get(null);

        config = (BlockPlaceConfig) unsafe.allocateInstance(BlockPlaceConfig.class);
        Field f = BlockPlaceConfig.class.getDeclaredField("againstActions");
        f.setAccessible(true);
        f.set(config, new ActionList(null));

        data = new BlockPlaceData();
        
        // ✅ FINAL FIX: The 'Check' class has a static 'dataManager' field that is null during tests.
        // We must create a valid PlayerDataManager and use reflection to inject it into this static field
        // before any Check (like 'Against') is instantiated.
        
        // 1. Create dependencies for PlayerDataManager.
        WorldDataManager wdm = mock(WorldDataManager.class);
        PermissionRegistry pr = mock(PermissionRegistry.class);
        
        // 2. Create the real PlayerDataManager instance.
        PlayerDataManager realDataManager = new PlayerDataManager(wdm, pr);

        // 3. Use reflection to set the static field: 'DataManager.instance'.
        Field dataManagerField = fr.neatmonster.nocheatplus.players.DataManager.class.getDeclaredField("instance");
        dataManagerField.setAccessible(true);
        
        dataManagerField.set(null, realDataManager);
        
        // 4. Now that the static dependency is met, we can safely create the check.
        this.against = new TestableAgainst();
    }

    private boolean runCheck(Material placedMat, Material againstMat, Material belowMat) {
        Block placed = mock(Block.class);
        Block below = mock(Block.class);
        when(placed.getRelative(BlockFace.DOWN)).thenReturn(below);
        when(below.getType()).thenReturn(belowMat);

        Block againstBlock = mock(Block.class);
        when(againstBlock.getType()).thenReturn(againstMat);
        when(againstBlock.getX()).thenReturn(0);
        when(againstBlock.getY()).thenReturn(0);
        when(againstBlock.getZ()).thenReturn(0);

        Player player = mock(Player.class);
        BlockInteractData biData = new BlockInteractData();
        biData.setLastBlock(againstBlock, Action.RIGHT_CLICK_BLOCK);
        biData.setLastIsCancelled(false);

        IPlayerData pData = mock(IPlayerData.class);
        when(pData.getGenericInstance(BlockInteractData.class)).thenReturn(biData);
        when(pData.hasPermission(any(RegisteredPermission.class), any(Player.class))).thenReturn(false);
        when(pData.isDebugActive(any())).thenReturn(false);

        boolean isInteract = !biData.getLastIsCancelled() && biData.matchesLastBlock(againstBlock);

        try (MockedStatic<fr.neatmonster.nocheatplus.utilities.map.BlockProperties> bpMock = mockStatic(fr.neatmonster.nocheatplus.utilities.map.BlockProperties.class)) {
            bpMock.when(() -> fr.neatmonster.nocheatplus.utilities.map.BlockProperties.isLiquid(any(Material.class)))
                  .thenAnswer(inv -> inv.getArgument(0) == Material.WATER);
            bpMock.when(() -> fr.neatmonster.nocheatplus.utilities.map.BlockProperties.isWaterPlant(any(Material.class)))
                  .thenReturn(false);
            bpMock.when(() -> fr.neatmonster.nocheatplus.utilities.map.BlockProperties.isAir(any(Material.class)))
                  .thenReturn(false);

            return against.check(player, placed, placedMat, againstBlock, isInteract, data, config, pData);
        }
    }

    @Test
    public void testLilyPadWithWaterBelowAllowed() {
        assertFalse(runCheck(BridgeMaterial.LILY_PAD, Material.WATER, Material.WATER));
    }

    @Test
    public void testLilyPadWithoutWaterBelowViolation() {
        assertTrue(runCheck(BridgeMaterial.LILY_PAD, Material.WATER, Material.STONE));
    }

}