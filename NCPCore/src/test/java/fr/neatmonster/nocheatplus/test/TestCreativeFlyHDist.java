package fr.neatmonster.nocheatplus.test;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;
import java.util.HashMap;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ItemStack;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.model.ModelFlying;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.checks.moving.player.CreativeFly;
import fr.neatmonster.nocheatplus.components.modifier.IAttributeAccess;
import fr.neatmonster.nocheatplus.components.registry.event.IGenericInstanceHandle;
import fr.neatmonster.nocheatplus.compat.MCAccess;
import fr.neatmonster.nocheatplus.utilities.location.PlayerLocation;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeTracker;

import org.junit.Before;
import org.junit.Test;

public class TestCreativeFlyHDist {

    static class SimpleHandle<T> implements IGenericInstanceHandle<T> {
        private final T handle;
        SimpleHandle(T h){ this.handle = h; }
        @Override public T getHandle(){ return handle; }
        @Override public void disableHandle(){}
    }

    static class DummyPlayerLocation extends PlayerLocation {
        boolean inWater=false, aboveStairs=false, reset=false;
        final Player player;
        DummyPlayerLocation(Player p) {
            super(new SimpleHandle<MCAccess>(null), null);
            this.player = p;
        }
        @Override public Player getPlayer(){ return player; }
        @Override public boolean isInWater(){ return inWater; }
        @Override public boolean isAboveStairs(){ return aboveStairs; }
        @Override public boolean isResetCond(){ return reset; }
    }

    private static Player createPlayer(final boolean gliding, final boolean riptiding, final boolean hasElytra) {
        PlayerInventory inv = (PlayerInventory) Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(),
                new Class<?>[]{PlayerInventory.class},
                (proxy, method, args) -> {
                    if ("getChestplate".equals(method.getName())) {
                        return hasElytra ? new ItemStack(Material.ELYTRA) : null;
                    }
                    Class<?> r = method.getReturnType();
                    if (r == boolean.class) return false;
                    if (r.isPrimitive()) return 0;
                    return null;
                });
        InvocationHandler handler = (proxy, method, args) -> {
            switch(method.getName()) {
                case "isGliding": return gliding;
                case "isRiptiding": return riptiding;
                case "getInventory": return inv;
                default:
                    Class<?> r = method.getReturnType();
                    if (r == boolean.class) return false;
                    if (r.isPrimitive()) return 0;
                    return null;
            }
        };
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, handler);
    }

    private static void setupAPI() throws Exception {
        IAttributeAccess attr = (IAttributeAccess)Proxy.newProxyInstance(IAttributeAccess.class.getClassLoader(),
                new Class<?>[]{IAttributeAccess.class}, (p,m,a) -> 1.0);
        MCAccess mc = (MCAccess)Proxy.newProxyInstance(MCAccess.class.getClassLoader(),
                new Class<?>[]{MCAccess.class}, (p,m,a) -> {
                    if ("getFasterMovementAmplifier".equals(m.getName())) return Double.POSITIVE_INFINITY;
                    Class<?> r = m.getReturnType();
                    if (r == boolean.class) return false;
                    if (r.isPrimitive()) return 0;
                    return null;
                });
        IGenericInstanceHandle<IAttributeAccess> attrHandle = new SimpleHandle<>(attr);
        IGenericInstanceHandle<MCAccess> mcHandle = new SimpleHandle<>(mc);
        Object api = Proxy.newProxyInstance(NCPAPIProvider.class.getClassLoader(),
                new Class<?>[]{fr.neatmonster.nocheatplus.components.NoCheatPlusAPI.class},
                (pr,m,a) -> {
                    if ("getGenericInstanceHandle".equals(m.getName())) {
                        Class<?> c = (Class<?>)a[0];
                        if (c == MCAccess.class) return mcHandle;
                        if (c == IAttributeAccess.class) return attrHandle;
                        return new SimpleHandle<>(null);
                    }
                    if ("getBlockChangeTracker".equals(m.getName())) return new BlockChangeTracker();
                    Class<?> r = m.getReturnType();
                    if (r == boolean.class) return false;
                    if (r.isPrimitive()) return 0;
                    return null;
                });
        Field f = NCPAPIProvider.class.getDeclaredField("noCheatPlusAPI");
        f.setAccessible(true);
        f.set(null, api);

        // Set DataManager.instance
        Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        uf.setAccessible(true);
        sun.misc.Unsafe un = (sun.misc.Unsafe) uf.get(null);
        Object pdm = un.allocateInstance(fr.neatmonster.nocheatplus.players.PlayerDataManager.class);
        Field eh = fr.neatmonster.nocheatplus.players.PlayerDataManager.class.getDeclaredField("executionHistories");
        eh.setAccessible(true);
        eh.set(pdm, new HashMap<>());
        Field dm = fr.neatmonster.nocheatplus.players.DataManager.class.getDeclaredField("instance");
        dm.setAccessible(true);
        dm.set(null, pdm);
    }

    @Before
    public void init() throws Exception {
        setupAPI();
    }

    private double[] invokeHdist(CreativeFly cf, Player p, PlayerLocation from, PlayerLocation to,
                                 double hDist, double yDist, boolean sprint, boolean fly,
                                 PlayerMoveData thisMove, PlayerMoveData lastMove,
                                 ModelFlying model, MovingData data) throws Exception {
        Method m = CreativeFly.class.getDeclaredMethod("hDist", Player.class, PlayerLocation.class, PlayerLocation.class,
                double.class, double.class, boolean.class, boolean.class, PlayerMoveData.class,
                PlayerMoveData.class, long.class, ModelFlying.class, MovingData.class, MovingConfig.class);
        m.setAccessible(true);
        return (double[]) m.invoke(cf, p, from, to, hDist, yDist, sprint, fly, thisMove, lastMove, 0L, model, data, null);
    }

    private MovingData newData() throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        sun.misc.Unsafe u = (sun.misc.Unsafe) f.get(null);
        MovingData data = (MovingData) u.allocateInstance(MovingData.class);
        return data;
    }

    @Test
    public void testNormalFlying() throws Exception {
        CreativeFly cf = new CreativeFly();
        Player p = createPlayer(false,false,false);
        DummyPlayerLocation from = new DummyPlayerLocation(p);
        DummyPlayerLocation to = new DummyPlayerLocation(p);
        PlayerMoveData thisMove = new PlayerMoveData();
        thisMove.hDistance = 0.8;
        PlayerMoveData lastMove = new PlayerMoveData();
        ModelFlying model = new ModelFlying().applyModifiers(false);
        MovingData data = newData();
        double[] res = invokeHdist(cf,p,from,to,thisMove.hDistance,0.0,false,true,thisMove,lastMove,model,data);
        assertEquals(0.6, res[0], 1e-6);
        assertEquals(0.2, res[1], 1e-6);
    }

    @Test
    public void testRipGlide() throws Exception {
        CreativeFly cf = new CreativeFly();
        Player p = createPlayer(true,true,true);
        DummyPlayerLocation from = new DummyPlayerLocation(p);
        DummyPlayerLocation to = new DummyPlayerLocation(p);
        PlayerMoveData thisMove = new PlayerMoveData();
        thisMove.hDistance = 1.5;
        PlayerMoveData lastMove = new PlayerMoveData();
        lastMove.toIsValid = true;
        lastMove.hDistance = 0.3;
        ModelFlying model = new ModelFlying().applyModifiers(false);
        MovingData data = newData();
        double[] res = invokeHdist(cf,p,from,to,thisMove.hDistance,0.0,false,false,thisMove,lastMove,model,data);
        assertEquals(9.9, res[0], 1e-6);
        assertEquals(0.0, res[1], 1e-6);
    }

    @Test
    public void testBunnyHop() throws Exception {
        CreativeFly cf = new CreativeFly();
        Player p = createPlayer(false,false,false);
        DummyPlayerLocation from = new DummyPlayerLocation(p);
        DummyPlayerLocation to = new DummyPlayerLocation(p);
        PlayerMoveData thisMove = new PlayerMoveData();
        thisMove.hDistance = 0.65;
        thisMove.touchedGroundWorkaround = true;
        PlayerMoveData lastMove = new PlayerMoveData();
        lastMove.toIsValid = true;
        lastMove.touchedGround = true;
        lastMove.hDistance = 0.3;
        ModelFlying model = new ModelFlying().applyModifiers(true);
        MovingData data = newData();
        data.walkSpeed = 0.2f;
        double[] res = invokeHdist(cf,p,from,to,thisMove.hDistance,1.0,false,false,thisMove,lastMove,model,data);
        assertEquals(0.6, res[0], 1e-6);
        assertEquals(0.0, res[1], 1e-6);
        assertEquals(9, data.bunnyhopDelay);
    }
}
