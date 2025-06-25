package fr.neatmonster.nocheatplus.test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.bukkit.World;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.components.modifier.IAttributeAccess;
import fr.neatmonster.nocheatplus.components.registry.event.IGenericInstanceHandle;
import fr.neatmonster.nocheatplus.compat.MCAccess;
import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.checks.moving.player.SurvivalFly;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.location.PlayerLocation;

import org.junit.Before;
import org.junit.Test;

public class TestSurvivalFlyHelpers {

    static class SimpleHandle<T> implements IGenericInstanceHandle<T> {
        private final T handle;
        SimpleHandle(T h){ this.handle = h; }
        @Override public T getHandle(){ return handle; }
        @Override public void disableHandle(){}
    }

    static class DummyPlayerLocation extends PlayerLocation {
        final Player player;
        final World world;
        DummyPlayerLocation(Player p) {
            super(new SimpleHandle<MCAccess>(null), null);
            this.player = p;
            this.world = createWorld();
        }
        @Override public Player getPlayer(){ return player; }
        @Override public org.bukkit.Location getLocation() { return new org.bukkit.Location(world,0,0,0); }
    }

    private static World createWorld() {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class[]{World.class}, (p,m,a) -> {
            Class<?> r = m.getReturnType();
            if (r == boolean.class) return false;
            if (r.isPrimitive()) return 0;
            return null;
        });
    }

    private static Player createPlayer() {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (proxy, method, args) -> {
            Class<?> r = method.getReturnType();
            if (r == boolean.class) return false;
            if (r.isPrimitive()) return 0;
            return null;
        });
    }

    private static void setupAPI() throws Exception {
        IAttributeAccess attr = (IAttributeAccess)Proxy.newProxyInstance(IAttributeAccess.class.getClassLoader(), new Class<?>[]{IAttributeAccess.class}, (p,m,a) -> 1.0);
        MCAccess mc = (MCAccess)Proxy.newProxyInstance(MCAccess.class.getClassLoader(), new Class<?>[]{MCAccess.class}, (p,m,a) -> null);
        IGenericInstanceHandle<IAttributeAccess> attrHandle = new SimpleHandle<>(attr);
        IGenericInstanceHandle<MCAccess> mcHandle = new SimpleHandle<>(mc);
        Object api = Proxy.newProxyInstance(NCPAPIProvider.class.getClassLoader(), new Class<?>[]{fr.neatmonster.nocheatplus.components.NoCheatPlusAPI.class}, (pr,m,a) -> {
            if ("getGenericInstanceHandle".equals(m.getName())) {
                Class<?> c = (Class<?>)a[0];
                if (c == MCAccess.class) return mcHandle;
                if (c == IAttributeAccess.class) return attrHandle;
                return new SimpleHandle<>(null);
            }
            if ("getBlockChangeTracker".equals(m.getName())) return null;
            Class<?> r = m.getReturnType();
            if (r == boolean.class) return false;
            if (r.isPrimitive()) return 0;
            return null;
        });
        java.lang.reflect.Field f = NCPAPIProvider.class.getDeclaredField("noCheatPlusAPI");
        f.setAccessible(true);
        f.set(null, api);

        Object pdm = mock(fr.neatmonster.nocheatplus.players.PlayerDataManager.class);
        java.lang.reflect.Field dm = fr.neatmonster.nocheatplus.players.DataManager.class.getDeclaredField("instance");
        dm.setAccessible(true);
        dm.set(null, pdm);
    }

    @Before
    public void init() throws Exception {
        setupAPI();
    }

    @Test
    public void testHelperMethodsAccessible() throws Exception {
        SurvivalFly sf = new SurvivalFly();
        PlayerMoveData move = new PlayerMoveData();
        move.from.inWeb = true;
        MovingData data = mock(MovingData.class);
        MovingConfig config = mock(MovingConfig.class);
        IPlayerData pData = mock(IPlayerData.class);
        Player player = createPlayer();
        PlayerLocation loc = new DummyPlayerLocation(player);
        Class<?> ctxClass = Class.forName("fr.neatmonster.nocheatplus.checks.moving.player.SurvivalFly$AllowedDistanceContext");
        Constructor<?> cons = ctxClass.getDeclaredConstructors()[0];
        cons.setAccessible(true);
        Object ctx = cons.newInstance(player, false, move, data, config, pData, loc, loc, false);
        Class<?> hsClass = Class.forName("fr.neatmonster.nocheatplus.checks.moving.player.SurvivalFly$HorizontalState");
        Constructor<?> hsCons = hsClass.getDeclaredConstructor(double.class);
        hsCons.setAccessible(true);
        Object state = hsCons.newInstance(1.0);
        String[] methods = {"applyWebModifiers", "applyPowderSnowModifiers", "applyBerryBushModifiers",
                "applySoulSandModifiers", "applySlimeBlockModifiers", "applyHoneyBlockModifiers",
                "applyStairsModifiers", "applyNoSlowPacketModifiers", "applyInvalidUseModifiers",
                "applyCollisionModifiers", "applyLiquidModifiers", "applyLeavingLiquidModifiers",
                "applySneakModifiers", "applyBlockingModifiers"};
        for (String name : methods) {
            for (Method method : SurvivalFly.class.getDeclaredMethods()) {
                if (method.getName().equals(name)) {
                    method.setAccessible(true);
                    if (method.getParameterCount() == 2) {
                        method.invoke(sf, ctx, state);
                    } else if (name.equals("applyHoneyBlockModifiers")) {
                        method.invoke(sf, ctx, state, 0.5d);
                    } else if (name.equals("applyStairsModifiers")) {
                        method.invoke(sf, ctx, state, 0.5d);
                    } else if (name.equals("applyLiquidModifiers")) {
                        method.invoke(sf, ctx, state, false, move, move, 0L);
                    } else if (name.equals("applyLeavingLiquidModifiers")) {
                        method.invoke(sf, ctx, state, false);
                    } else if (name.equals("applySneakModifiers")) {
                        method.invoke(sf, ctx, state, false, false);
                    } else if (name.equals("applyBlockingModifiers")) {
                        method.invoke(sf, ctx, state, false, false, move);
                    }
                }
            }
        }
        java.lang.reflect.Field processed = hsClass.getDeclaredField("processed");
        processed.setAccessible(true);
        assertTrue(processed.getBoolean(state));
    }
}
