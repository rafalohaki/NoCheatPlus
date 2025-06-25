package fr.neatmonster.nocheatplus.checks.fight;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.Test;
import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.actions.ActionFactory;
import fr.neatmonster.nocheatplus.actions.ActionFactoryFactory;
import fr.neatmonster.nocheatplus.components.NoCheatPlusAPI;
import fr.neatmonster.nocheatplus.worlds.WorldDataManager;
import fr.neatmonster.nocheatplus.actions.ActionList;

import fr.neatmonster.nocheatplus.config.DefaultConfig;
import fr.neatmonster.nocheatplus.config.ConfPaths;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.worlds.IWorldData;
import fr.neatmonster.nocheatplus.checks.ViolationData;

public class ReachTest {

    @Test
    public void testLoopFinishNullParams() {
        Reach r = new Reach();
        assertFalse(r.loopFinish(null, null, null, null, null, false, null, null, null));
    }

    private static class TestReach extends Reach {
        @Override
        public ViolationData executeActions(ViolationData vd) {
            vd.forceCancel();
            return vd;
        }
    }

    @Test
    public void testHandleViolationViaLoopFinish() {
        NoCheatPlusAPI api = mock(NoCheatPlusAPI.class, RETURNS_DEEP_STUBS);
        when(api.getActionFactoryFactory()).thenReturn(ActionFactory::new);
        when(api.getWorldDataManager()).thenReturn(new WorldDataManager());
        when(api.getPermissionRegistry()).thenReturn(new fr.neatmonster.nocheatplus.permissions.PermissionRegistry(100));
        try {
            java.lang.reflect.Method m = NCPAPIProvider.class.getDeclaredMethod("setNoCheatPlusAPI", NoCheatPlusAPI.class);
            m.setAccessible(true);
            m.invoke(null, api);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        IWorldData wd = mock(IWorldData.class);
        DefaultConfig cfg = new DefaultConfig() {
            @Override
            public void setActionFactory() {}

            @Override
            public void setActionFactory(ActionFactoryFactory aff) {}

            @Override
            public ActionList getOptimizedActionList(String path, fr.neatmonster.nocheatplus.permissions.RegisteredPermission permission) {
                return new ActionList(permission);
            }
            @Override
            public ActionList getDefaultActionList(String path, fr.neatmonster.nocheatplus.permissions.RegisteredPermission permission) {
                return new ActionList(permission);
            }
        };
        cfg.set(ConfPaths.FIGHT_REACH_IMPROBABLE_WEIGHT, 0.0);
        cfg.set(ConfPaths.FIGHT_REACH_PENALTY, 50);
        when(wd.getRawConfiguration()).thenReturn(cfg);
        when(wd.shouldAdjustToLag(any())).thenReturn(false);

        FightConfig cc = new FightConfig(wd);
        FightData data = new FightData(cc);

        Player player = mock(Player.class);
        when(player.getEyeHeight()).thenReturn(1.62);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);

        Entity damaged = mock(Entity.class);
        Location loc = new Location(null, 0, 0, 0);

        IPlayerData pData = mock(IPlayerData.class);
        when(pData.isDebugActive(any())).thenReturn(false);
        when(pData.hasPermission(eq(fr.neatmonster.nocheatplus.permissions.Permissions.ADMINISTRATION_DEBUG), any(Player.class))).thenReturn(false);

        ReachContext ctx = new ReachContext();
        ctx.distanceLimit = 3.0;
        ctx.distanceMin = 0.5;
        ctx.minResult = 4.0;

        TestReach tr = new TestReach();
        boolean result = tr.loopFinish(player, loc, damaged, ctx, null, false, data, cc, pData);
        assertTrue(result);
        assertTrue(data.reachVL > 0.0);
        assertTrue(data.attackPenalty.isPenalty());
    }
}

