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
package fr.neatmonster.nocheatplus.compat.cbreflect.reflect;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.compat.AlmostBoolean;
import fr.neatmonster.nocheatplus.config.ConfPaths;
import fr.neatmonster.nocheatplus.config.ConfigManager;
import fr.neatmonster.nocheatplus.logging.Streams;
import fr.neatmonster.nocheatplus.utilities.ReflectionUtil;
import fr.neatmonster.nocheatplus.utilities.StringUtil;

/**
 * More handy high level methods throwing one type of exception.
 * <p>
 * Instances of this class are not designed for modification after use. When a
 * {@link fr.neatmonster.nocheatplus.compat.cbreflect.BlockCacheCBReflect}
 * is created with a {@code ReflectHelper}, that helper should be treated as
 * immutable for the lifetime of the cache.
 * </p>
 * @author asofold
 *
 */
public class ReflectHelper {

    // Many possible exceptions are not yet caught.
    // In some places we should actually try-catch and fail() instead of using default values or returning null.

    /** Failure to use / apply [ / setup ? ]. */
    public static class ReflectFailureException extends RuntimeException {

        /**
         * 
         */
        private static final long serialVersionUID = -3934791920291782604L;

        public ReflectFailureException() {
            super();
        }

        public ReflectFailureException(ClassNotFoundException ex) {
            super(ex);
        }

        // Might add a sub-error enum or additional code support.

    }

    protected final ReflectBase reflectBase;

    protected final ReflectAxisAlignedBB reflectAxisAlignedBB;
    protected final ReflectBlockPosition reflectBlockPosition;
    protected final IReflectBlock reflectBlock;
    protected final ReflectMaterial reflectMaterial;
    protected final ReflectWorld reflectWorld;

    protected final ReflectDamageSource reflectDamageSource;
    protected final ReflectEntity reflectEntity;
    protected final ReflectEntity reflectLivingEntity;
    protected final ReflectPlayer reflectPlayer;

    private final double[] tempBounds = new double[6];

    public ReflectHelper() {
        try {
            this.reflectBase = new ReflectBase();
            this.reflectAxisAlignedBB = initAxisAlignedBB();
            this.reflectBlockPosition = initBlockPosition();
            this.reflectMaterial = new ReflectMaterial(this.reflectBase);
            this.reflectWorld = new ReflectWorld(reflectBase, reflectMaterial, reflectBlockPosition);
            this.reflectBlock = initBlock(reflectBlockPosition, reflectMaterial, reflectWorld);
            this.reflectDamageSource = new ReflectDamageSource(this.reflectBase);
            this.reflectEntity = new ReflectEntity(this.reflectBase, this.reflectAxisAlignedBB, this.reflectDamageSource);
            this.reflectLivingEntity = new ReflectLivingEntity(this.reflectBase, this.reflectAxisAlignedBB, this.reflectDamageSource);
            this.reflectPlayer = new ReflectPlayer(this.reflectBase, this.reflectAxisAlignedBB, this.reflectDamageSource);
        }
        catch (ClassNotFoundException ex) {
            throw new ReflectFailureException(ex);
        }
        logSetupIssues();
    }

    private ReflectAxisAlignedBB initAxisAlignedBB() {
        if (this.reflectBase.nmsPackageName == null) {
            return null;
        }
        try {
            Class<?> aabbClass = Class.forName(this.reflectBase.nmsPackageName + ".AxisAlignedBB");
            boolean hasAllFields =
                    ReflectionUtil.getField(aabbClass, "a", double.class) != null &&
                    ReflectionUtil.getField(aabbClass, "b", double.class) != null &&
                    ReflectionUtil.getField(aabbClass, "c", double.class) != null &&
                    ReflectionUtil.getField(aabbClass, "d", double.class) != null &&
                    ReflectionUtil.getField(aabbClass, "e", double.class) != null &&
                    ReflectionUtil.getField(aabbClass, "f", double.class) != null;
            if (hasAllFields) {
                return new ReflectAxisAlignedBB(reflectBase);
            }
        } catch (ClassNotFoundException ex1) {
            // ignore - axis aligned bounding box not present
        }
        return null;
    }

    private ReflectBlockPosition initBlockPosition() {
        try {
            return new ReflectBlockPosition(this.reflectBase);
        } catch (RuntimeException ex) {
            return null; // BlockPosition class not available
        }
    }

    private IReflectBlock initBlock(ReflectBlockPosition position, ReflectMaterial material,
            ReflectWorld world) throws ClassNotFoundException {
        ReflectBlock reflectBlockLatest = null;
        if (position != null) {
            try {
                reflectBlockLatest = new ReflectBlock(this.reflectBase, position, material, world);
            }
            catch (Throwable t) {
                // ignore - using ReflectBlockSix fallback
            }
        }
        if (reflectBlockLatest == null) {
            return new ReflectBlockSix(this.reflectBase, position);
        }
        return reflectBlockLatest;
    }

    private void logSetupIssues() {
        if (ConfigManager.getConfigFile().getBoolean(ConfPaths.LOGGING_EXTENDED_STATUS)) {
            List<String> parts = new LinkedList<String>();
            for (Field rootField : this.getClass().getDeclaredFields()) {
                if (rootField.isAnnotationPresent(MostlyHarmless.class)) {
                    continue;
                }
                boolean accessible = rootField.canAccess(this);
                if (!accessible) {
                    rootField.setAccessible(true);
                }
                Object obj = ReflectionUtil.get(rootField, this, null);
                if (obj == null) {
                    parts.add("(Not available: " + rootField.getName() + ")");
                }
                else if (rootField.getName().startsWith("reflect")) {
                    Class<?> clazz = obj.getClass();
                    for (Field field : clazz.getFields()) {
                        if (field.isAnnotationPresent(MostlyHarmless.class)) {
                            continue;
                        }
                        if (ReflectionUtil.get(field, obj, null) == null) {
                            parts.add(clazz.getName() + "." + field.getName());
                        }
                    }
                }
                if (!accessible) {
                    rootField.setAccessible(false);
                }
            }
            if (!reflectBlock.isFetchBoundsAvailable()) {
                parts.add("fetch-block-shape");
            }
            if (!parts.isEmpty()) {
                parts.add(0, "CompatCBReflect: The following properties could not be set:");
                NCPAPIProvider.getNoCheatPlusAPI().getLogManager().warning(Streams.INIT, StringUtil.join(parts, "\n"));
            }
        }
    }

    /**
     * Quick fail with exception. Used both for setup and runtime.
     * @throws ReflectFailureException Always.
     */
    private void fail() {
        throw new ReflectFailureException();
    }

    public Object getHandle(Player player) {
        // Maybe check for CraftPlayer with isAssignableFrom.
        if (this.reflectPlayer.obcGetHandle == null) {
            fail();
        }
        Object handle = ReflectionUtil.invokeMethodNoArgs(this.reflectPlayer.obcGetHandle, player);
        if (handle == null) {
            fail();
        }
        return handle;
    }

    public double nmsPlayer_getHealth(Object handle) {
        if (this.reflectPlayer.nmsGetHealth == null) {
            fail();
        }
        return ((Number) ReflectionUtil.invokeMethodNoArgs(this.reflectPlayer.nmsGetHealth, handle)).doubleValue();
    }

    public boolean nmsPlayer_dead(Object handle) {
        if (this.reflectPlayer.nmsDead == null) {
            fail();
        }
        return ReflectionUtil.getBoolean(this.reflectPlayer.nmsDead, handle, true);
    }

    /**
     * Set the value for the dead field.
     * @param handle
     * @param value
     */
    public void nmsPlayer_dead(Object handle, boolean value) {
        if (this.reflectPlayer.nmsDead == null || !ReflectionUtil.set(this.reflectPlayer.nmsDead, handle, value)) {
            fail();
        }
    }

    /**
     * Set the value for the dead field.
     * @param handle
     * @param value
     */
    public void nmsPlayer_deathTicks(Object handle, int value) {
        if (this.reflectPlayer.nmsDeathTicks == null || !ReflectionUtil.set(this.reflectPlayer.nmsDeathTicks, handle, value)) {
            fail();
        }
    }

    public boolean canDealFallDamage() {
        return this.reflectPlayer.nmsDamageEntity != null && this.reflectDamageSource.nmsFALL != null;
    }

    public void dealFallDamage(Player player, double damage) {
        if (this.reflectDamageSource.nmsFALL == null) {
            fail();
        }
        Object handle = getHandle(player);
        nmsPlayer_dealDamage(handle, this.reflectDamageSource.nmsFALL, damage);
    }

    public void nmsPlayer_dealDamage(Object handle, Object damage_source, double damage) {
        if (this.reflectPlayer.nmsDamageEntity == null) {
            fail();
        }
        if (this.reflectPlayer.nmsDamageEntityInt) {
            ReflectionUtil.invokeMethod(this.reflectPlayer.nmsDamageEntity, handle, damage_source, (int) damage);
        } else {
            ReflectionUtil.invokeMethod(this.reflectPlayer.nmsDamageEntity, handle, damage_source, (float) damage);
        }
    }

    public int getInvulnerableTicks(Player player) {
        if (this.reflectPlayer.nmsInvulnerableTicks == null) {
            fail();
        }
        Object handle = getHandle(player);
        return ReflectionUtil.getInt(this.reflectPlayer.nmsInvulnerableTicks, handle, player.getNoDamageTicks() / 2);
    }

    public void setInvulnerableTicks(Player player, int ticks) {
        if (this.reflectPlayer.nmsInvulnerableTicks == null) {
            fail();
        }
        Object handle = getHandle(player);
        if (!ReflectionUtil.set(this.reflectPlayer.nmsInvulnerableTicks, handle, ticks)) {
            fail();
        }
    }

    public Object getHandle(World world) {
        if (this.reflectWorld.obcGetHandle == null) {
            fail();
        }
        Object handle = ReflectionUtil.invokeMethodNoArgs(this.reflectWorld.obcGetHandle, world);
        if (handle == null) {
            fail();
        }
        return handle;
    }

    public Object nmsBlockPosition(int x, int y, int z) {
        if (this.reflectBlockPosition.new_nmsBlockPosition == null) {
            fail();
        }
        Object blockPos = ReflectionUtil.newInstance(this.reflectBlockPosition.new_nmsBlockPosition, x, y, z);
        if (blockPos == null) {
            fail();
        }
        return blockPos;
    }

    /**
     * 
     * @param id
     * @return Block instance (could be null).
     */
    public Object nmsBlock_getByMaterial(Material id) {
        if (reflectBlock == null) {
            fail();
        }
        return this.reflectBlock.nms_getByMaterial(id);
    }

    public Object nmsBlock_getMaterial(Object block) {
        if (reflectBlock == null) {
            fail();
        }
        return this.reflectBlock.nms_getMaterial(block);
    }

    public boolean nmsMaterial_isSolid(Object material) {
        if (this.reflectMaterial.nmsIsSolid == null) {
            fail();
        }
        return (Boolean) ReflectionUtil.invokeMethodNoArgs(this.reflectMaterial.nmsIsSolid, material);
    }

    public boolean nmsMaterial_isLiquid(Object material) {
        if (this.reflectMaterial.nmsIsLiquid == null) {
            fail();
        }
        return (Boolean) ReflectionUtil.invokeMethodNoArgs(this.reflectMaterial.nmsIsLiquid, material);
    }

    public AlmostBoolean isBlockSolid(Material id) {
        Object obj = nmsBlock_getByMaterial(id);
        if (obj == null) {
            return AlmostBoolean.MAYBE;
        }
        obj = nmsBlock_getMaterial(obj);
        if (obj == null) {
            return AlmostBoolean.MAYBE;
        }
        return AlmostBoolean.match(nmsMaterial_isSolid(obj));
    }

    public AlmostBoolean isBlockLiquid(Material id) {
        Object obj = nmsBlock_getByMaterial(id);
        if (obj == null) {
            return AlmostBoolean.MAYBE;
        }
        obj = nmsBlock_getMaterial(obj);
        if (obj == null) {
            return AlmostBoolean.MAYBE;
        }
        return AlmostBoolean.match(nmsMaterial_isLiquid(obj));
    }

    /**
     * Fetch the block shape for the given position in the given nms-world. (Not
     * a method in world types.)
     * 
     * @param nmsWorld
     * @param id
     * @param x
     * @param y
     * @param z
     * @return double[6] minX, minY, minZ, maxX, maxY, maxZ. Returns null for
     *         cases like air/unspecified.
     */
    public double[] nmsWorld_fetchBlockShape(final Object nmsWorld, 
            final Material id, final int x, final int y, final int z) {
        if (reflectBlock == null) {
            fail();
        }
        final Object nmsBlock = nmsBlock_getByMaterial(id);
        if (nmsBlock == null) {
            return null;
        }
        return reflectBlock.nms_fetchBounds(nmsWorld, nmsBlock, x, y, z);
    }

    public double getWidth(final Entity entity) {
        float width = -16f;
        if (reflectEntity.nmsWidth == null) {
            fail();
        }
        final Object handle = reflectEntity.getHandle(entity);
        if (handle != null) {
            width = ReflectionUtil.getFloat(reflectEntity.nmsWidth, handle, width);
        }
        if (width < 0f) {
            fail();
        }
        return (double) width;
    }

    public double getHeight(final Entity entity) {
        float floatHeight = -16f;
        final Object handle = reflectEntity.getHandle(entity); // Potentially distinguish between living and non-living classes.
        double height;
        if (handle == null) {
            fail();
        }
        if (reflectEntity.nmsLength != null) {
            floatHeight = Math.max(ReflectionUtil.getFloat(reflectEntity.nmsLength, handle, floatHeight), floatHeight);
        }
        if (reflectEntity.nmsHeight != null) {
            floatHeight = Math.max(ReflectionUtil.getFloat(reflectEntity.nmsHeight, handle, floatHeight), floatHeight);
        }
        height = (double) floatHeight;
        // Consider dropping the box for performance.
        if (reflectAxisAlignedBB != null && reflectEntity.nmsGetBoundingBox != null) {
            final Object box = ReflectionUtil.invokeMethodNoArgs(reflectEntity.nmsGetBoundingBox, handle);
            if (box != null) {
                // mcEntity.boundingBox.e - mcEntity.boundingBox.b
                final double y2 = ReflectionUtil.getDouble(reflectAxisAlignedBB.nms_maxY, box, Double.MAX_VALUE);
                final double y1 = ReflectionUtil.getDouble(reflectAxisAlignedBB.nms_minY, box, Double.MAX_VALUE);
                if (y1 != Double.MAX_VALUE && y2 != Double.MAX_VALUE) {
                    height = Math.max(y2 - y1, height);
                }
            }
        }
        if (height < 0.0) {
            fail();
        }
        // On success only: Check eye height (MCAccessBukkit is better than just eye height.).
        if (entity instanceof LivingEntity) {
            height = Math.max(height, ((LivingEntity) entity).getEyeHeight());
        }
        return height;
    }

    /**
     * Fetch the bounding box.
     * 
     * @param entity
     * @return A new double array {minX, minY, minZ, maxX, maxY, maxZ}. Not to
     *         be stored etc.
     * @throws ReflectFailureException
     *             On failure to fetch bounds.
     */
    public double[] getBounds(final Entity entity) {
        return getBounds(entity, new double[6]);
    }

    /**
     * Fetch the bounding box.
     * 
     * @param entity
     * @return The internally stored double array for bounds {minX, minY, minZ,
     *         maxX, maxY, maxZ}. Not to be stored etc.
     * @throws ReflectFailureException
     *             On failure to fetch bounds.
     */
    public double[] getBoundsTemp(final Entity entity) {
        return getBounds(entity, tempBounds);
    }

    /**
     * Fetch the bounding box.
     * 
     * @param entity
     * @param bounds
     *            The double[6+] array, which to fill values in to.
     * @return The passed bounds array filled with {minX, minY, minZ, maxX,
     *         maxY, maxZ}.
     * @throws ReflectFailureException
     *             On failure to fetch bounds.
     */
    public double[] getBounds(final Entity entity, final double[] bounds) {
        // Possibly fetch for legacy versions as well.
        if (reflectAxisAlignedBB == null || reflectEntity == null) {
            fail();
        }
        final Object aabb = ReflectionUtil.invokeMethodNoArgs(reflectEntity.nmsGetBoundingBox, reflectEntity.getHandle(entity));
        if (aabb == null) {
            fail();
        }
        reflectAxisAlignedBB.fillInValues(aabb, bounds);
        return bounds;
    }

}
