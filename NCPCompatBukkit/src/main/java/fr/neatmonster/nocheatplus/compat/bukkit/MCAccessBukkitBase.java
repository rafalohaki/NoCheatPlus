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
package fr.neatmonster.nocheatplus.compat.bukkit;

import java.util.LinkedHashSet;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.ComplexLivingEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.potion.PotionEffectType;
import fr.neatmonster.nocheatplus.compat.AlmostBoolean;
import fr.neatmonster.nocheatplus.compat.BridgeEntityType;
import fr.neatmonster.nocheatplus.compat.BridgeHealth;
import fr.neatmonster.nocheatplus.compat.BridgePotionEffect;
import fr.neatmonster.nocheatplus.compat.MCAccess;
import fr.neatmonster.nocheatplus.utilities.PotionUtil;
import fr.neatmonster.nocheatplus.utilities.ReflectionUtil;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache;
import fr.neatmonster.nocheatplus.utilities.map.BlockFlags;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;
import fr.neatmonster.nocheatplus.utilities.map.MaterialUtil;

public class MCAccessBukkitBase implements MCAccess {

    // private AlmostBoolean entityPlayerAvailable = AlmostBoolean.MAYBE;
    protected final boolean bukkitHasGetHeightAndGetWidth;

    /**
     * Fill in already initialized blocks, to return false for guessItchyBlock.
     */
    protected final Set<Material> processedBlocks = new LinkedHashSet<Material>();

    /**
     * Constructor to let it fail.
     */
    public MCAccessBukkitBase() {
        // Additional features might fail if not supported.
        testItchyBlock();
        // Some features might not be available; checks should be deactivated based on an availability method.
        // Methods such as getHeight should eventually move to EntityAccessXY.
        bukkitHasGetHeightAndGetWidth = ReflectionUtil.getMethodNoArgs(Entity.class, "getHeight", double.class) != null
                && ReflectionUtil.getMethodNoArgs(Entity.class, "getWidth", double.class) != null;
    }

    @SuppressWarnings("deprecation")
    private boolean guessItchyBlockPre1_13(final Material mat) {
        return !mat.isOccluding() || !mat.isSolid() || mat.isTransparent();
    }

    protected boolean guessItchyBlock(final Material mat) {
        // General considerations first.
        if (processedBlocks.contains(mat)
                || BlockProperties.isAir(mat) || BlockProperties.isLiquid(mat)) {
            return false;
        }
        // Fully solid/ground blocks.
        final long flags = BlockFlags.getBlockFlags(mat);
        /*
         * Skip fully passable blocks (partially passable blocks may be itchy,
         * though slabs will be easy to handle).
         */
        if (BlockFlags.hasAnyFlag(flags, BlockFlags.F_IGN_PASSABLE)) {
            // Blocks with min_height might be fine if xz100 and a specific height are set.
            return !BlockFlags.hasNoFlags(flags,
                    BlockFlags.F_GROUND_HEIGHT
                    | BlockFlags.F_GROUND
                    | BlockFlags.F_SOLID);
        }

        long testFlags1 = (BlockFlags.F_SOLID | BlockFlags.F_XZ100);
        long testFlags2 = (BlockFlags.F_HEIGHT100);
        if (BlockFlags.hasAllFlags(flags, testFlags1) && BlockFlags.hasAnyFlag(flags, testFlags2)) {
            // Solid blocks with explicitly set bounds.
            return false;
        }

        // Fallback to the pre-1.13 implementation.
        return guessItchyBlockPre1_13(mat);
    }

    private void testItchyBlock() {
        // Use the basic implementation that works across versions.
        guessItchyBlockPre1_13(Material.AIR);
    }

    @Override
    public String getMCVersion() {
        // Bukkit API. Versions lower than MC 1.13 have their own module.
        // Return the supported MC version range.
        return "1.13-1.20"; // uh oh
    }

    @Override
    public String getServerVersionTag() {
        return "Bukkit-API";
    }

    @Override
    public CommandMap getCommandMap() {
        try{
            return (CommandMap) ReflectionUtil.invokeMethodNoArgs(Bukkit.getServer(), "getCommandMap");
        } catch (Throwable t) {
            // ignore - server does not expose a CommandMap
            return null;
        }
    }

    @Override
    public BlockCache getBlockCache() {
        return getBlockCache(null);
    }

    @Override
    public BlockCache getBlockCache(final World world) {
        return new BlockCacheBukkit(world);
    }

    @Override
    public double getHeight(final Entity entity) {
        double entityHeight;
        if (bukkitHasGetHeightAndGetWidth) {
            entityHeight = entity.getHeight();
        }
        else {
            entityHeight = 1.0;
        }
        if (entity instanceof LivingEntity) {
            return Math.max(((LivingEntity) entity).getEyeHeight(), entityHeight);
        } else {
            return entityHeight;
        }
    }

    @Override
    public AlmostBoolean isBlockSolid(final Material mat) {
        if (mat == null) {
            return AlmostBoolean.MAYBE;
        }
        else {
            return AlmostBoolean.match(mat.isSolid());
        }
    }

    private final double legacyGetWidth(final Entity entity) {
        // Values should be configurable and per-entity getters registered when possible.
        // Height values could be automated by spawning and checking entities.
        // Values taken from 1.7.10.
        final EntityType type = entity.getType();
        Double width = BridgeEntityType.LEGACY_ENTITY_WIDTH.get(type);
        if (width != null) return width;
        switch(type){
            // case COMPLEX_PART: // not supported yet
            //case ENDER_SIGNAL: // this.a(0.25F, 0.25F);
            //case FIREWORK: // this.a(0.25F, 0.25F);
            //case FISHING_HOOK: // this.a(0.25F, 0.25F);
            //case DROPPED_ITEM: // this.a(0.25F, 0.25F);
            case SNOWBALL: // (projectile) this.a(0.25F, 0.25F);
                return 0.25;
            case CHICKEN: // this.a(0.3F, 0.7F);
            case SILVERFISH: // this.a(0.3F, 0.7F);
                return 0.3f;
            case SMALL_FIREBALL: // this.a(0.3125F, 0.3125F);
            case WITHER_SKULL: // this.a(0.3125F, 0.3125F);
                return 0.3125f;
            case GHAST: // this.a(4.0F, 4.0F);
            //case SNOWMAN: // this.a(0.4F, 1.8F);
                return 0.4f;
            case ARROW: // this.a(0.5F, 0.5F);
            case BAT: // this.a(0.5F, 0.9F);
            case EXPERIENCE_ORB: // this.a(0.5F, 0.5F);
            case ITEM_FRAME: // hanging: this.a(0.5F, 0.5F);
            case PAINTING: // hanging: this.a(0.5F, 0.5F);
                return 0.5f;
            case PLAYER: // FAST RETURN
            case ZOMBIE:
            case SKELETON:
            case CREEPER:
            case ENDERMAN:
            case OCELOT:
            case BLAZE:
            case VILLAGER:
            case WITCH:
            case WOLF:
                return 0.6f; // (Default entity width.)
            case CAVE_SPIDER: // this.a(0.7F, 0.5F);
                return 0.7f;
            case COW: // this.a(0.9F, 1.3F);
            //case MUSHROOM_COW: // this.a(0.9F, 1.3F);
            case PIG: // this.a(0.9F, 0.9F);
            case SHEEP: // this.a(0.9F, 1.3F);
            case WITHER: // this.a(0.9F, 4.0F);
                return 0.9f;
            case SQUID: // this.a(0.95F, 0.95F);
                return 0.95f;
            //case PRIMED_TNT: // this.a(0.98F, 0.98F);
            //    return 0.98f;
            case FIREBALL: // (EntityFireball) this.a(1.0F, 1.0F);
                return 1.0f;
            case IRON_GOLEM: // this.a(1.4F, 2.9F);
            case SPIDER: // this.a(1.4F, 0.9F);
                return 1.4f;
            case BOAT: // this.a(1.5F, 0.6F);
                return 1.5f;
            //case ENDER_CRYSTAL: // this.a(2.0F, 2.0F);
            //    return 2.0f;
            case GIANT: // this.height *= 6.0F; this.a(this.width * 6.0F, this.length * 6.0F);
                return 3.6f; // (Better than nothing.)
            case ENDER_DRAGON: // this.a(16.0F, 8.0F);
                return 16.0f;
                // Variable size:
            case SLIME:
            case MAGMA_CUBE:
                if (entity instanceof Slime) {
                    // setSize(i): this.a(0.6F * (float) i, 0.6F * (float) i);
                    return 0.6f * ((Slime) entity).getSize();
                }
            default:
                break;
        }
        // Check by instance for minecarts (too many).
        if (entity instanceof Minecart) {
            return 0.98f; // this.a(0.98F, 0.7F);
        }
        // Latest Bukkit API.
        try {
            switch (type) {
                //case LEASH_HITCH: // hanging: this.a(0.5F, 0.5F);
                //    return 0.5f;
                case HORSE: // this.a(1.4F, 1.6F);
                    return 1.4f;
                    // 1.8
                case ENDERMITE: // this.setSize(0.4F, 0.3F);
                    return 0.4f;
                case ARMOR_STAND: // this.setSize(0.5F, 1.975F);
                    return 0.5f;
                case RABBIT: // this.setSize(0.6F, 0.7F);
                    return 0.6f;
                case GUARDIAN: // this.setSize(0.85F, 0.85F);
                    return 0.95f;
                default:
                    break;
            }
        } catch (Throwable t) {
            // ignore - entity width information not accessible
        }
        // Default entity width.
        return 0.6f;
    }

    @Override
    public double getWidth(final Entity entity) {
        if (bukkitHasGetHeightAndGetWidth) {
            return entity.getWidth();
        }
        else {
            return legacyGetWidth(entity);
        }
    }

    @Override
    public AlmostBoolean isBlockLiquid(final Material mat) {
        if (mat == null) {
            return AlmostBoolean.MAYBE;
        }
        else if (MaterialUtil.WATER.contains(mat) || MaterialUtil.LAVA.contains(mat)) {
            return AlmostBoolean.YES;
        }
        else {
            return AlmostBoolean.NO;
        }
    }

    @Override
    public AlmostBoolean isIllegalBounds(final Player player) {
        if (player.isDead()) {
            return AlmostBoolean.NO;
        }
        if (!player.isSleeping()) {
            // Sleeping state might not be relevant here.
            // (Might not be necessary.)
        }
        return AlmostBoolean.MAYBE;
    }

    @Override
    public double getJumpAmplifier(final Player player) {
        return PotionUtil.getPotionEffectAmplifier(player, BridgePotionEffect.JUMP_BOOST);
    }

    @Override
    public double getFasterMovementAmplifier(final Player player) {
        return PotionUtil.getPotionEffectAmplifier(player, PotionEffectType.SPEED);
    }

    @Override
    public int getInvulnerableTicks(final Player player) {
        return Integer.MAX_VALUE; // NOT SUPPORTED.
    }

    @Override
    public void setInvulnerableTicks(final Player player, final int ticks) {
        // IGNORE.
    }

    @Override
    public void dealFallDamage(final Player player, final double damage) {
        // Damage application may require accounting for armor or other modifiers.
        // Consider using setLastDamageCause when possible.
        BridgeHealth.damage(player, damage);
    }

    @Override
    public boolean isComplexPart(final Entity entity) {
        return entity instanceof ComplexEntityPart || entity instanceof ComplexLivingEntity;
    }

    @Override
    public boolean shouldBeZombie(final Player player) {
        // Not sure :) ...
        return BridgeHealth.getHealth(player) <= 0.0 && !player.isDead();
    }

    @Override
    public void setDead(final Player player, final int deathTicks) {
        // Immediately set the player's health to zero.
        BridgeHealth.setHealth(player, 0.0);
        // Consider adjusting no-damage ticks as well.
        BridgeHealth.damage(player, 1.0);
    }

    @Override
    public boolean hasGravity(final Material mat) {
        try{
            return mat.hasGravity();
        }
        catch(Throwable t) {
            // Backwards compatibility.
            switch(mat) {
                case SAND:
                case GRAVEL:
                    return true;
                default:
                    return false;
            }
        }
    }

    @Override
    public AlmostBoolean dealFallDamageFiresAnEvent() {
        return AlmostBoolean.NO; // Assumption.
    }

    @Override
    public boolean resetActiveItem(Player player) {
        return false; // Assumption.
    }

    //  @Override
    //  public void correctDirection(Player player) {
    //      // Reflection-based correction might be possible via CraftPlayer and EntityPlayer.
    //  }

}
