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
package fr.neatmonster.nocheatplus.utilities.map;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.InputMismatchException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.BubbleColumn;

import fr.neatmonster.nocheatplus.utilities.InventoryUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.checks.moving.util.MovingUtil;
import fr.neatmonster.nocheatplus.compat.AlmostBoolean;
import fr.neatmonster.nocheatplus.compat.Bridge1_13;
import fr.neatmonster.nocheatplus.compat.Bridge1_9;
import fr.neatmonster.nocheatplus.compat.BridgeEnchant;
import fr.neatmonster.nocheatplus.compat.BridgeMaterial;
import fr.neatmonster.nocheatplus.compat.BridgePotionEffect;
import fr.neatmonster.nocheatplus.compat.MCAccess;
import fr.neatmonster.nocheatplus.compat.blocks.BlockPropertiesSetup;
import fr.neatmonster.nocheatplus.compat.blocks.init.BlockInit;
import fr.neatmonster.nocheatplus.compat.blocks.init.vanilla.VanillaBlocksFactory;
import fr.neatmonster.nocheatplus.components.registry.event.IHandle;
import fr.neatmonster.nocheatplus.config.ConfPaths;
import fr.neatmonster.nocheatplus.config.RawConfigFile;
import fr.neatmonster.nocheatplus.config.WorldConfigProvider;
import fr.neatmonster.nocheatplus.logging.LogManager;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import fr.neatmonster.nocheatplus.logging.Streams;
import fr.neatmonster.nocheatplus.utilities.PotionUtil;
import fr.neatmonster.nocheatplus.utilities.StringUtil;
import fr.neatmonster.nocheatplus.utilities.collision.BlockPositionContainer;
import fr.neatmonster.nocheatplus.utilities.collision.ICollidePassable;
import fr.neatmonster.nocheatplus.utilities.collision.PassableAxisTracing;
import fr.neatmonster.nocheatplus.utilities.collision.PassableRayTracing;
import fr.neatmonster.nocheatplus.utilities.location.PlayerLocation;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache.IBlockCacheNode;


/**
 * Properties of blocks.
 * 
 * Likely to be added:
 * - reading (all) properties from files.
 * - reading (all) the default properties from a file too.
 *
 */
@SuppressWarnings("deprecation")
public class BlockProperties {

    /** Tolerance for floating point comparisons. */
    private static final double EPSILON = 1.0E-6;

    /** Cached Material values from InventoryUtil. */
    private static final Material[] ALL_MATERIALS = InventoryUtil.ALL_MATERIALS;

    /**
     * The Enum ToolType.
     *
     * @author asofold
     * @deprecated Will be replaced by the new
     *             {@code ToolDefinition} concept provided by the history
     *             service. Removal planned for version 2.0.
     *             <p>
     *             Migration: create a {@code ToolDefinition} via the history
     *             service and use it when resolving block interactions.
     *             </p>
     */
    public static enum ToolType {
        /** The none. */
        NONE,
        /** The sword. */
        SWORD,
        /** The shears. */
        SHEARS,
        /** The spade. */
        SPADE,
        /** The axe. */
        AXE,
        /** The pickaxe. */
        PICKAXE,
        /** The hoe. */
        HOE,
    }

    /**
     * The Enum MaterialBase.
     *
     * @author asofold
     * @deprecated Will be replaced by {@code ToolDefinition} from the history
     *             service. Scheduled for removal in 2.0.
     *             <p>
     *             Migration: obtain material properties from the history
     *             service and refer to {@code ToolDefinition} instead of this
     *             enum.
     *             </p>
     */
    public static enum MaterialBase {
        /** The none. */
        NONE(0, 1f),
        /** The wood. */
        WOOD(1, 2f),
        /** The stone. */
        STONE(2, 4f),
        /** The iron. */
        IRON(3, 6f),
        /** The diamond. */
        DIAMOND(4, 8f),
        /** The netherite */
        NETHERITE(5, 9f),
        /** The gold. */
        GOLD(6, 12f);

        /** Index for array. */
        public final int index;

        /** The break multiplier. */
        public final float breakMultiplier;

        /**
         * Instantiates a new material base.
         *
         * @param index
         *            the index
         * @param breakMultiplier
         *            the break multiplier
         */
        private MaterialBase(int index, float breakMultiplier) {
            this.index = index;
            this.breakMultiplier = breakMultiplier;
        }

        /**
         * Gets the by id.
         *
         * @param id
         *            the id
         * @return the by id
         * @deprecated Nothing to do with ids. Use the history service to obtain
         *             material bases. Removal planned for version 2.0.
         *             <p>
         *             Migration: call
         *             {@code HistoryService#getMaterialBaseById(int)} instead.
         *             </p>
         */
        @Deprecated
        public static final MaterialBase getById(final int id) {
            return getByIndex(id);
        }

        /**
         * Get the index of this base material within the relevant materials or
         * breaking times array.
         * 
         * @param index
         * @return
         */
        public static final MaterialBase getByIndex(final int index) {
            for (final MaterialBase base : MaterialBase.values()) {
                if (base.index == index) {
                    return base;
                }
            }
            throw new IllegalArgumentException("Bad index: " + index);
        }
    }

    /**
     * Properties of a tool.
     * 
     * @deprecated Will be replaced by {@code ToolDefinition}. Scheduled for
     *             removal in 2.0.
     *             <p>
     *             Migration: transition your code to use
     *             {@code ToolDefinition} objects fetched from the history
     *             service.
     *             </p>
     */
    public static class ToolProps {
        
        /** The tool type. */
        public final ToolType toolType;

        /** The material base. */
        public final MaterialBase materialBase;

        /**
         * Instantiates a new tool props.
         *
         * @param toolType
         *            the tool type
         * @param materialBase
         *            the material base
         */
        public ToolProps(ToolType toolType, MaterialBase materialBase) {
            this.toolType = toolType;
            this.materialBase = materialBase;
        }

        /* (non-Javadoc)
         * @see java.lang.Object#toString()
         */
        public String toString() {
            return "ToolProps("+toolType + "/"+materialBase+")";
        }

        /**
         * Validate.
         */
        public void validate() {
            if (toolType == null) {
                throw new IllegalArgumentException("ToolType must not be null.");
            }
            if (materialBase == null) {
                throw new IllegalArgumentException("MaterialBase must not be null");
            }
        }
    }

    /**
     * Properties of a block.
     * 
     * @deprecated Will be replaced by the {@code BlockDefinition} API in the
     *             history service. Will be removed in version 2.0.
     *             <p>
     *             Migration: use {@code BlockDefinition} from the history
     *             service instead of this class.
     *             </p>
     */
    public static class BlockProps{

        /** The tool. */
        public final ToolProps tool;

        /** The breaking times. */
        public final long[] breakingTimes;

        /** The hardness. */
        public final float hardness;

        /** Factor 2 = 2 times faster. */
        public final float efficiencyMod;

        /** Indicate block can be harvested by not using tool */
        public final boolean requireCorrectTool;
        
        /** Indicate to use modern module or not */
        public final boolean pureHardness;

        /**
         * Instantiates a new block props.
         *
         * @param tool
         *            The tool type that allows access to breaking times other
         *            than MaterialBase.NONE.
         * @param hardness
         *            the hardness
         */
        public BlockProps(ToolProps tool, float hardness) {
            this(tool, hardness, 1, false);
        }

        /**
         * Instantiates a new block props.
         *
         * @param tool
         *            The tool type that allows access to breaking times other
         *            than MaterialBase.NONE.
         * @param hardness
         *            the hardness
         * @param requireCorrectTool
         *            false if block can be collected using bare hand to mine and vice versa
         */
        public BlockProps(ToolProps tool, float hardness, boolean requireCorrectTool) {
            this(tool, hardness, 1, requireCorrectTool);
        }

        /**
         * Instantiates a new block props.
         *
         * @param tool
         *            The tool type that allows access to breaking times other
         *            than MaterialBase.NONE.
         * @param hardness
         *            the hardness
         * @param efficiencyMod
         *            the efficiency mod
         * @param requireCorrectTool
         *            false if block can be collected using bare hand to mine and vice versa
         */
        public BlockProps(ToolProps tool, float hardness, float efficiencyMod, boolean requireCorrectTool) {
            pureHardness = true;
            this.tool = tool;
            this.hardness = hardness;
            this.requireCorrectTool = requireCorrectTool;
            breakingTimes = new long[7];
            breakingTimes[0] = (long) (1000f * 5f * hardness);
            boolean notool = tool.materialBase == null || tool.toolType == null || tool.toolType == ToolType.NONE;//|| tool.materialBase == MaterialBase.NONE;

            if (notool || !requireCorrectTool) {
                breakingTimes[0] *= 0.3;
            }

            for (int i = 1; i < 7; i++) {
                if (notool) {
                    breakingTimes[i] = breakingTimes[0];
                } 
                else if (hardness > 0.0) {
                    float speed = MaterialBase.getById(i).breakMultiplier;
                    float damage = speed / hardness;
                    damage /= isRightToolMaterial(null, tool.materialBase, MaterialBase.getById(i), true) ? 30f : 100f;
                    breakingTimes[i] = damage >= 1 ? 0 : Math.round(1 / damage) * 50;
                }
                else {
                    breakingTimes[i] = 0;
                }
            }
            this.efficiencyMod = efficiencyMod;
        }

        /**
         * Instantiates a new block props.
         *
         * @param tool
         *            The tool type that allows access to breaking times other
         *            than MaterialBase.NONE.
         * @param hardness
         *            the hardness
         * @param breakingTimes
         *            The breaking times (NONE, WOOD, STONE, IRON, DIAMOND,
         *            NETHERITE, GOLD)
         */
        public BlockProps(ToolProps tool, float hardness, long[] breakingTimes) {
            this(tool, hardness, breakingTimes, 1f);
        }

        /**
         * Instantiates a new block props.
         *
         * @param tool
         *            The tool type that allows access to breaking times other
         *            than MaterialBase.NONE.
         * @param hardness
         *            the hardness
         * @param breakingTimes
         *            The breaking times (NONE, WOOD, STONE, IRON, DIAMOND,
         *            NETHERITE, GOLD)
         * @param efficiencyMod
         *            the efficiency mod
         */
        public BlockProps(ToolProps tool, float hardness, long[] breakingTimes, float efficiencyMod) {
            this.pureHardness = false;
            this.requireCorrectTool = false;
            this.tool = tool;
            this.breakingTimes = breakingTimes;
            this.hardness = hardness;
            this.efficiencyMod = efficiencyMod;
        }

        /* (non-Javadoc)
         * @see java.lang.Object#toString()
         */
        public String toString() {
            return "BlockProps(" + hardness + " / " + tool.toString() + " / " + Arrays.toString(breakingTimes) + ")";
        }

        /**
         * Validate.
         */
        public void validate() {
            if (breakingTimes == null) {
                throw new IllegalArgumentException("Breaking times must not be null.");
            }
            if (breakingTimes.length != 7) {
                throw new IllegalArgumentException("Breaking times length must match the number of available tool types (7).");
            }
            if (tool == null)  {
                throw new IllegalArgumentException("Tool must not be null.");
            }
            tool.validate();
        }
    }

    /**
     * Key for the specific block breaking time override table, mapping to the
     * breaking time. Aim at configuration defaults, stating the more or less
     * exact side conditions.
     * 
     * @author asofold
     *
     */
    public static class BlockBreakKey {

        private Material blockType = null;

        private ToolType toolType = null;

        private MaterialBase materialBase = null;

        /** Efficiency enchantment. */
        private Integer efficiency = null;

        // NOTE: Thinkable: head in liquid, attributes, player enchantments.

        // NOTE: SHOULD: Read from config.
        /*
         * NOTE: COULD: add support for a command to auto track these entries
         * and create config entries automatically. Should change methods to use
         * this class as input (best with full side conditions).
         */

        /**
         * Empty constructor, no properties set.
         */
        public BlockBreakKey() {}

        /**
         * Copy constructor.
         * 
         * @param key
         */
        public BlockBreakKey(BlockBreakKey key) {
            blockType = key.blockType;
            toolType = key.toolType;
            materialBase = key.materialBase;
            efficiency = key.efficiency;
        }

        public BlockBreakKey blockType(Material blockType) {
            this.blockType = blockType;
            return this;
        }

        public Material blockType() {
            return blockType;
        }

        public BlockBreakKey toolType(ToolType toolType) {
            this.toolType = toolType;
            return this;
        }

        public ToolType toolType() {
            return toolType;
        }

        public BlockBreakKey materialBase(MaterialBase materialBase) {
            this.materialBase = materialBase;
            return this;
        }

        public MaterialBase materialBase() {
            return materialBase;
        }

        public BlockBreakKey efficiency(int efficiency) {
            this.efficiency = efficiency;
            return this;
        }

        public int efficiency() {
            return efficiency;
        }

        /**
         * Add properties defined in a (config line of) string.
         * 
         * @param def
         * @return
         * @throws All sorts of exceptions (number format, enum constants, runtime).
         */
        public BlockBreakKey fromString(String def) {
            String[] parts = def.split(":");
            // First fully parse:
            if (parts.length != 4) {
                throw new IllegalArgumentException("Accept key definition with 4 parts only, input: " + def);
            }
            Material blockType = Material.matchMaterial(parts[0]);
            ToolType toolType = ToolType.valueOf(parts[1].toUpperCase());
            MaterialBase materialBase = MaterialBase.valueOf(parts[2].toUpperCase());
            int efficiency = Integer.parseInt(parts[3]);
            return this.blockType(blockType).toolType(toolType).materialBase(materialBase).efficiency(efficiency);
        }

        @Override
        public int hashCode() {
            return (blockType == null ? 0 : blockType.hashCode() * 11)
                    ^ (toolType == null ? 0 : toolType.hashCode() * 137)
                    ^ (materialBase == null ? 0 : materialBase.hashCode() * 1193)
                    ^ (efficiency == null ? 0 : efficiency.hashCode() * 12791);
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj instanceof BlockBreakKey) {
                final BlockBreakKey other = (BlockBreakKey) obj;
                // NOTE: Some should be equals later.
                return blockType == other.blockType
                        && Objects.equals(efficiency, other.efficiency) // fastest first.
                        && toolType == other.toolType
                        && materialBase == other.materialBase;
            }
            return false;
        }

        @Override
        public String toString() {
            return "BlockBreakKey(blockType=" + blockType + "toolType=" + toolType + "materialBase=" + materialBase + " efficiency=" + efficiency + ")";
        }
    }

    /**
     * Convenience method excluding null as air result.
     *
     * @param mat
     *            the mat.
     * @return true, if is air
     */
    public static final boolean isActuallyAir(final Material mat) {
        // NOTE: Flags rather?
        return mat != null && isAir(mat);
    }

    /**
     * Convenience method including null checks.
     *
     * @param mat
     *            the mat.
     * @return true, if is air
     */
    public static final boolean isAir(final Material mat) {
        // NOTE: Flags rather?
        return mat == null || mat == Material.AIR
                // Assume the compiler throws away further null values.
                || mat == BridgeMaterial.VOID_AIR
                || mat == BridgeMaterial.CAVE_AIR
                ;
    }

    /**
     * Convenience method including null checks.
     *
     * @param stack
     *            the stack
     * @return true, if is air
     */
    public static final boolean isAir(final ItemStack stack) {
        return stack == null || isAir(stack.getType());
    }

    /**
     * Test if the id is rails and if data means ascending.
     *
     * @param mat
     *            the mat.
     * @param data
     *            the data
     * @return true, if is ascending rails
     */
    public static final boolean isAscendingRails(final Material mat, final int data) {
        return isRails(mat) && (data & 7) > 1;
    }

    /**
     * Climbable material that needs to be attached to a block, to allow players
     * to climb up.<br>
     * Currently only applies to vines. There is no flag for such, yet.
     *
     * @param mat
     *            the mat.
     * @return true, if is attached climbable
     */
    public static final boolean isAttachedClimbable(final Material mat) {
        if ((BlockFlags.getBlockFlags(mat) & BlockFlags.F_CLIMBUPABLE) != 0) return false;
        return mat == Material.VINE;
    }

    /**
     * Checks if is blue ice.
     *
     * @param mat
     *            the mat.
     * @return true, if is blue ice
     */
    public static final boolean isBlueIce(final Material mat) {
        return (BlockFlags.getBlockFlags(mat) & BlockFlags.F_BLUE_ICE) != 0;
    }

    /**
     * Checks if is carpet.
     *
     * @param mat
     *            the mat.
     * @return true, if is carpet
     */
    public static final boolean isCarpet(final Material mat) {
        return (BlockFlags.getBlockFlags(mat) & BlockFlags.F_CARPET) != 0;
    }

    /**
     * Test if is chest
     * 
     * @param mat
     *            the mat
     * @return true, if is chest
     */
    public static final boolean isChest(final Material mat) {
        return mat != null && (mat == Material.CHEST || mat == Material.TRAPPED_CHEST || mat == Material.ENDER_CHEST);
    }

    /**
     * Checks if is climbable.
     *
     * @param mat
     *            the mat.
     * @return true, if is climbable
     */
    public static final boolean isClimbable(final Material mat) {
        return (BlockFlags.getBlockFlags(mat) & BlockFlags.F_CLIMBABLE) != 0;
    }

    /**
     * Test if the block is a containter.
     *
     * @param mat
     *            the mat.
     * @return true, if is container
     */
    public static boolean isContainer(final Material mat) {
        return mat != null && (mat == Material.CHEST
                            || mat == Material.ENDER_CHEST
                            || mat == Material.DISPENSER
                            || mat == Material.DROPPER
                            || mat == Material.TRAPPED_CHEST
                            || mat == Material.HOPPER
                            || mat == BridgeMaterial.BARREL
                            // Ugly.
                            || mat.toString().endsWith("SHULKER_BOX"));
    }

    /**
     * Might hold true for liquids too. NOTE: ENSURE IT DOESN'T.
     *
     * @param mat
     *            the mat.
     * @return true, if is ground
     */
    public static final boolean isGround(final Material mat) {
        return (BlockFlags.getBlockFlags(mat) & BlockFlags.F_GROUND) != 0;
    }

    /**
     * Might hold true for liquids too. NOTE: ENSURE IT DOESN'T.
     *
     * @param mat
     *            the mat.
     * @param ignoreFlags
     *            flags to ignore
     * @return true, if is ground
     */
    public static final boolean isGround(final Material mat, final long ignoreFlags) {
        final long flags = BlockFlags.getBlockFlags(mat);
        return (flags & BlockFlags.F_GROUND) != 0 && (flags & ignoreFlags) == 0;
    }

    /**
     * Checks if is ice.
     *
     * @param mat
     *            the mat.
     * @return true, if is ice
     */
    public static final boolean isIce(final Material mat) {
        return (BlockFlags.getBlockFlags(mat) & BlockFlags.F_ICE) != 0;
    }

    /**
     * Checks if is leaves.
     *
     * @param mat
     *            the mat.
     * @return true, if is leaves
     */
    public static final boolean isLeaves(final Material mat) {
        return (BlockFlags.getBlockFlags(mat) & BlockFlags.F_LEAVES) != 0;
    }

    /**
     * Checks if is liquid.
     *
     * @param mat
     *            the mat.
     * @return true, if is liquid
     */
    public static final boolean isLiquid(final Material mat) {
        return (BlockFlags.getBlockFlags(mat) & BlockFlags.F_LIQUID) != 0;
    }

    /**
     * Checks if is PowderSnow.
     *
    * @param mat
     *            the mat.
     * @return true, if is powder now
     */
    public static final boolean isPowderSnow(final Material mat) {
        return (BlockFlags.getBlockFlags(mat) & BlockFlags.F_POWDERSNOW) != 0;
    }

    /**
     * All rail types a minecart can move on.
     *
     * @param mat
     *            the mat.
     * @return true, if is rails
     */
    public static final boolean isRails(final Material mat) {
        return (BlockFlags.getBlockFlags(mat) & BlockFlags.F_RAILS) != 0; 
    }

    /**
     * Convenience method including null check.
     *
     * @param mat
     *            the mat.
     * @return true, if is Scaffolding
     */
    public static final boolean isScaffolding(final Material mat) {
        return mat != null && mat == BridgeMaterial.SCAFFOLDING;
    }

    /**
     * Convenience method including null check.
     *
     * @param type
     *            the type
     * @return true, if is Scaffolding
     */
    public static final boolean isScaffolding(final ItemStack stack) {
        return stack != null && isScaffolding(stack.getType());
    }

    /**
     * Might hold true for liquids too.
     *
     * @param mat
     *            the mat.
     * @return true, if is solid
     */
    public static final boolean isSolid(final Material mat) {
        return (BlockFlags.getBlockFlags(mat) & BlockFlags.F_SOLID) != 0;
    }

    /**
     * Checks if is stairs.
     *
     * @param mat
     *            the mat.
     * @return true, if is stairs
     */
    public static final boolean isStairs(final Material mat) {
        return (BlockFlags.getBlockFlags(mat) & BlockFlags.F_STAIRS) != 0;
    }

    /**
     * Test for water type of blocks.
     * 
     * @param mat
     *            the mat.
     * @return true, if is water
     */
    public static final boolean isWater(final Material mat) {
        return (BlockFlags.getBlockFlags(mat) & BlockFlags.F_WATER) != 0;
    }

    /**
     * Checks if is apart of 1.13 water blocks.
     *
     * @param mat
     *            the mat.
     * @return true, if is liquid
     */
    public static final boolean isWaterPlant(final Material mat) {
        return (BlockFlags.getBlockFlags(mat) & BlockFlags.F_WATER_PLANT) != 0;
    }

    public static final boolean isDoor(final Material mat) {
        return MaterialUtil.ALL_DOORS.contains(mat);
    }

    /** Liquid height if no solid/full blocks are above. */
    protected static final double LIQUID_HEIGHT_LOWERED = 8/9f;

    /** Properties by block type.*/
    protected static final Map<Material, BlockProps> blocks = new HashMap<Material, BlockProps>();

    /** Map for the tool properties. */
    protected static final Map<Material, ToolProps> tools = new LinkedHashMap<Material, ToolProps>(50, 0.5f);

    /** Direct overrides for specific side conditions.*/
    private static Map<BlockBreakKey, Long> breakingTimeOverrides = new HashMap<BlockProperties.BlockBreakKey, Long>();

    /** Lookup table for minimal ground heights defined via flags. */
    private static final Map<Long, Double> MIN_HEIGHT_LOOKUP = new HashMap<Long, Double>();

    /** Mask for all supported minimal height flags. */
    private static final long MIN_HEIGHT_MASK;

    static {
        long mask = 0;
        MIN_HEIGHT_LOOKUP.put(BlockFlags.F_MIN_HEIGHT16_1, 0.0625);
        mask |= BlockFlags.F_MIN_HEIGHT16_1;
        MIN_HEIGHT_LOOKUP.put(BlockFlags.F_MIN_HEIGHT8_1, 0.125);
        mask |= BlockFlags.F_MIN_HEIGHT8_1;
        MIN_HEIGHT_LOOKUP.put(BlockFlags.F_MIN_HEIGHT4_1, 0.25);
        mask |= BlockFlags.F_MIN_HEIGHT4_1;
        MIN_HEIGHT_LOOKUP.put(BlockFlags.F_MIN_HEIGHT16_5, 0.3125);
        mask |= BlockFlags.F_MIN_HEIGHT16_5;
        MIN_HEIGHT_LOOKUP.put(BlockFlags.F_MIN_HEIGHT16_7, 0.4375);
        mask |= BlockFlags.F_MIN_HEIGHT16_7;
        MIN_HEIGHT_LOOKUP.put(BlockFlags.F_MIN_HEIGHT16_9, 0.5625);
        mask |= BlockFlags.F_MIN_HEIGHT16_9;
        MIN_HEIGHT_LOOKUP.put(BlockFlags.F_MIN_HEIGHT8_5, 0.625);
        mask |= BlockFlags.F_MIN_HEIGHT8_5;
        MIN_HEIGHT_LOOKUP.put(BlockFlags.F_MIN_HEIGHT16_11, 0.6875);
        mask |= BlockFlags.F_MIN_HEIGHT16_11;
        MIN_HEIGHT_LOOKUP.put(BlockFlags.F_MIN_HEIGHT16_13, 0.8125);
        mask |= BlockFlags.F_MIN_HEIGHT16_13;
        MIN_HEIGHT_LOOKUP.put(BlockFlags.F_MIN_HEIGHT16_14, 0.875);
        mask |= BlockFlags.F_MIN_HEIGHT16_14;
        MIN_HEIGHT_LOOKUP.put(BlockFlags.F_MIN_HEIGHT16_15, 0.9375);
        mask |= BlockFlags.F_MIN_HEIGHT16_15;
        MIN_HEIGHT_MASK = mask;
    }

    /** Breaking time for indestructible materials. */
    public static final long indestructible = Long.MAX_VALUE;

    /** Default tool properties (inappropriate tool). */
    public static final ToolProps noTool = new ToolProps(ToolType.NONE, MaterialBase.NONE);

    /** The Constant woodSword. */
    public static final ToolProps woodSword = new ToolProps(ToolType.SWORD, MaterialBase.WOOD);

    /** The Constant woodSpade. */
    public static final ToolProps woodSpade = new ToolProps(ToolType.SPADE, MaterialBase.WOOD);

    /** The Constant woodPickaxe. */
    public static final ToolProps woodPickaxe = new ToolProps(ToolType.PICKAXE, MaterialBase.WOOD);

    /** The Constant woodAxe. */
    public static final ToolProps woodAxe = new ToolProps(ToolType.AXE, MaterialBase.WOOD);

    /** The Constant woodHoe. */
    public static final ToolProps woodHoe = new ToolProps(ToolType.HOE, MaterialBase.WOOD);

    /** The Constant stonePickaxe. */
    public static final ToolProps stonePickaxe = new ToolProps(ToolType.PICKAXE, MaterialBase.STONE);

    /** The Constant ironPickaxe. */
    public static final ToolProps ironPickaxe = new ToolProps(ToolType.PICKAXE, MaterialBase.IRON);

    /** The Constant diamondPickaxe. */
    public static final ToolProps diamondPickaxe = new ToolProps(ToolType.PICKAXE, MaterialBase.DIAMOND);

    /** Times for instant breaking. */
    static final long[] instantTimes = secToMs(0);

    /** The Constant indestructibleTimes. */
    private static final long[] indestructibleTimes = new long[] {indestructible, indestructible, indestructible, indestructible, indestructible, indestructible, indestructible}; 

    /** Instantly breakable. */ 
    public static final BlockProps instantType = new BlockProps(noTool, 0, instantTimes);

    /** The Constant glassType. */
    public static final BlockProps glassType = new BlockProps(noTool, 0.3f);

    /** The Constant gravelType. */
    public static final BlockProps gravelType = new BlockProps(woodSpade, 0.6f);

    /** Stone type blocks (hardness 1.5f). */
    public static final BlockProps stoneTypeI = new BlockProps(woodPickaxe, 1.5f, true);
    
    /** Stone type blocks (hardness 2f). */
    public static final BlockProps stoneTypeII = new BlockProps(woodPickaxe, 2f, true);

    /** The Constant woodType. */
    public static final BlockProps woodType = new BlockProps(woodAxe, 2f);

    /** The Constant brickType. */
    public static final BlockProps brickType = new BlockProps(woodPickaxe, 2f, true);

    /** The Constant coalType. */
    public static final BlockProps coalType = new BlockProps(woodPickaxe, 3f, true);

    /** The Constant goldBlockType. */
    public static final BlockProps goldBlockType = new BlockProps(woodPickaxe, 3f, true);

    /** The Constant ironBlockType. */
    public static final BlockProps ironBlockType = new BlockProps(woodPickaxe, 5f, true);

    /** The Constant diamondBlockType. */
    public static final BlockProps diamondBlockType = new BlockProps(woodPickaxe, 5f, true);

    /** The Constant hugeMushroomType. */
    public static final BlockProps hugeMushroomType = new BlockProps(woodAxe, 0.2f);

    /** The Constant leafType. */
    public static final BlockProps leafType = new BlockProps(noTool, 0.2f);

    /** The Constant sandType. */
    public static final BlockProps sandType = new BlockProps(woodSpade, 0.5f);

    /** The Constant leverType. */
    public static final BlockProps leverType = new BlockProps(noTool, 0.5f);

    /** The Constant sandStoneType. */
    public static final BlockProps sandStoneType = new BlockProps(woodPickaxe, 0.8f, true);

    /** The Constant chestType. */
    public static final BlockProps chestType = new BlockProps(woodAxe, 2.5f);

    /** The Constant woodDoorType. */
    public static final BlockProps woodDoorType = new BlockProps(woodAxe, 3.0f);

    /** The Constant dispenserType. */
    public static final BlockProps dispenserType = new BlockProps(woodPickaxe, 3.5f, true);

    /** The Constant ironDoorType. */
    public static final BlockProps ironDoorType = new BlockProps(woodPickaxe, 5f, true);

    /** The Constant indestructibleType. */
    public static final BlockProps indestructibleType = new BlockProps(noTool, -1f, indestructibleTimes);

    /** Returned if unknown. */
    private static BlockProps defaultBlockProps = instantType;

    /** The rt ray. */
    private static ICollidePassable rtRay = null;

    /** The rt axis. */
    private static ICollidePassable rtAxis = null;

    /** The block cache. */
    private static WrapBlockCache wrapBlockCache = null; 

    /** The p loc. */
    private static PlayerLocation pLoc = null;

    /** The world minimum block Y */
    private static int minWorldY = 0;

    /** Trap door is climbable with ladder underneath, both facing distinct. */
    private static boolean specialCaseTrapDoorAboveLadder = false;

    /** Penalty factor for block break duration if under water. */
    protected static float breakPenaltyInWater = 5f;
    
    /** Penalty factor for block break duration if not on ground. */
    static float breakPenaltyOffGround = 5f;

    /** The Constant useLoc. */
    private static final Location useLoc = new Location(null, 0, 0, 0);

    /**
     * Initialize blocks and tools properties. This can be called at any time
     * during runtime.
     *
     * @param mcAccess
     *            If mcAccess implements BlockPropertiesSetup,
     *            mcAccess.setupBlockProperties will be called directly after
     *            basic initialization but before the configuration is applied.
     * @param worldConfigProvider
     *            the world config provider
     */
    public static void init(final IHandle<MCAccess> mcAccess, final WorldConfigProvider<?> worldConfigProvider) {

        wrapBlockCache = new WrapBlockCache();
        rtRay = new PassableRayTracing();
        rtAxis = new PassableAxisTracing();
        pLoc = new PlayerLocation(mcAccess, null);
        final Set<String> blocksFeatures = new LinkedHashSet<String>(); // getClass().getName() or some abstract.
        try {
            initTools(mcAccess, worldConfigProvider);
            initBlocks(mcAccess, worldConfigProvider);
            blocksFeatures.add("BlocksMC1_4");
            // Extra hand picked setups.
            try {
                blocksFeatures.addAll(new VanillaBlocksFactory().setupVanillaBlocks(worldConfigProvider));
            }
            catch (Throwable t) {
                StaticLog.logSevere("Could not initialize vanilla blocks: " + t.getClass().getSimpleName() + " - " + t.getMessage());
                StaticLog.logSevere(t);
            }
            // Allow mcAccess to setup block properties.
            final MCAccess handle = mcAccess.getHandle();
            if (handle instanceof BlockPropertiesSetup) {
                try {
                    ((BlockPropertiesSetup) handle).setupBlockProperties(worldConfigProvider);
                    blocksFeatures.add(handle.getClass().getSimpleName());
                }
                catch (Throwable t) {
                    StaticLog.logSevere("McAccess.setupBlockProperties (" + handle.getClass().getSimpleName() + ") could not execute properly: " + t.getClass().getSimpleName() + " - " + t.getMessage());
                    StaticLog.logSevere(t);
                }
            }
            // NOTE: Add registry for further BlockPropertiesSetup instances.
        }
        catch (Throwable t) {
            StaticLog.logSevere(t);
        }
        // Override feature tags for blocks.
        NCPAPIProvider.getNoCheatPlusAPI().setFeatureTags("blocks", blocksFeatures);
    }

    /**
     * Inits the tools.
     *
     * @param mcAccess
     *            the mc access
     * @param worldConfigProvider
     *            the world config provider
     */
    private static void initTools(final IHandle<MCAccess> mcAccess, final WorldConfigProvider<?> worldConfigProvider) {
        tools.clear();
        tools.put(BridgeMaterial.WOODEN_SWORD, new ToolProps(ToolType.SWORD, MaterialBase.WOOD));
        tools.put(BridgeMaterial.WOODEN_SHOVEL, new ToolProps(ToolType.SPADE, MaterialBase.WOOD));
        tools.put(BridgeMaterial.WOODEN_PICKAXE, new ToolProps(ToolType.PICKAXE, MaterialBase.WOOD));
        tools.put(BridgeMaterial.WOODEN_AXE, new ToolProps(ToolType.AXE, MaterialBase.WOOD));
        tools.put(BridgeMaterial.WOODEN_HOE, new ToolProps(ToolType.HOE, MaterialBase.WOOD));

        tools.put(Material.STONE_SWORD, new ToolProps(ToolType.SWORD, MaterialBase.STONE));
        tools.put(BridgeMaterial.STONE_SHOVEL, new ToolProps(ToolType.SPADE, MaterialBase.STONE));
        tools.put(Material.STONE_PICKAXE, new ToolProps(ToolType.PICKAXE, MaterialBase.STONE));
        tools.put(Material.STONE_AXE, new ToolProps(ToolType.AXE, MaterialBase.STONE));
        tools.put(Material.STONE_HOE, new ToolProps(ToolType.HOE, MaterialBase.STONE));

        tools.put(Material.IRON_SWORD, new ToolProps(ToolType.SWORD, MaterialBase.IRON));
        tools.put(BridgeMaterial.IRON_SHOVEL, new ToolProps(ToolType.SPADE, MaterialBase.IRON));
        tools.put(Material.IRON_PICKAXE, new ToolProps(ToolType.PICKAXE, MaterialBase.IRON));
        tools.put(Material.IRON_AXE, new ToolProps(ToolType.AXE, MaterialBase.IRON));
        tools.put(Material.IRON_HOE, new ToolProps(ToolType.HOE, MaterialBase.IRON));

        tools.put(Material.DIAMOND_SWORD, new ToolProps(ToolType.SWORD, MaterialBase.DIAMOND));
        tools.put(BridgeMaterial.DIAMOND_SHOVEL, new ToolProps(ToolType.SPADE, MaterialBase.DIAMOND));
        tools.put(Material.DIAMOND_PICKAXE, new ToolProps(ToolType.PICKAXE, MaterialBase.DIAMOND));
        tools.put(Material.DIAMOND_AXE, new ToolProps(ToolType.AXE, MaterialBase.DIAMOND));
        tools.put(Material.DIAMOND_HOE, new ToolProps(ToolType.HOE, MaterialBase.DIAMOND));

        tools.put(BridgeMaterial.GOLDEN_SWORD, new ToolProps(ToolType.SWORD, MaterialBase.GOLD));
        tools.put(BridgeMaterial.GOLDEN_SHOVEL, new ToolProps(ToolType.SPADE, MaterialBase.GOLD));
        tools.put(BridgeMaterial.GOLDEN_PICKAXE, new ToolProps(ToolType.PICKAXE, MaterialBase.GOLD));
        tools.put(BridgeMaterial.GOLDEN_AXE, new ToolProps(ToolType.AXE, MaterialBase.GOLD));
        tools.put(BridgeMaterial.GOLDEN_HOE, new ToolProps(ToolType.HOE, MaterialBase.GOLD));

        if (BridgeMaterial.NETHERITE_SWORD != null) {
            tools.put(BridgeMaterial.NETHERITE_SWORD, new ToolProps(ToolType.SWORD, MaterialBase.NETHERITE));
            tools.put(BridgeMaterial.NETHERITE_SHOVEL, new ToolProps(ToolType.SPADE, MaterialBase.NETHERITE));
            tools.put(BridgeMaterial.NETHERITE_PICKAXE, new ToolProps(ToolType.PICKAXE, MaterialBase.NETHERITE));
            tools.put(BridgeMaterial.NETHERITE_AXE, new ToolProps(ToolType.AXE, MaterialBase.NETHERITE));
            tools.put(BridgeMaterial.NETHERITE_HOE, new ToolProps(ToolType.HOE, MaterialBase.NETHERITE));
        }

        tools.put(Material.SHEARS, new ToolProps(ToolType.SHEARS, MaterialBase.NONE));
    }

    private static void setBlock(Material material, BlockProps props) {
        try {
            if (!material.isBlock()) {
                // Misconfigured materials are common; log at debug level.
                StaticLog.logDebug("Material is not a block: " + material);
            }
        } catch (Throwable t) {
            StaticLog.logSevere("Failed to validate block material: " + material);
            StaticLog.logSevere(t);
            // Don't re-throw to preserve stability in production.
            // Developers can catch severe logs during testing.
        }
        blocks.put(material, props);
    }

    /**
     * Inits the blocks.
     *
     * @param mcAccessHandle
     *            the mc access handle
     * @param worldConfigProvider
     *            the world config provider
     */
    private static void initBlocks(final IHandle<MCAccess> mcAccessHandle, final WorldConfigProvider<?> worldConfigProvider) {
        final MCAccess mcAccess = mcAccessHandle.getHandle();
        // Reset tool props.
        blocks.clear();
        
        /**
         * Clean up pending.
         * Move 1.14 and 1.12 blocks to to an extra setup class.
         * 
         */
        //////////////////////////////////////////////////////
        // Initialize block flags                           //
        //////////////////////////////////////////////////////
        // Generic initialization.
        for (Material mat : ALL_MATERIALS) {
            BlockFlags.blockFlags.put(mat, 0L);
            if (mcAccess.isBlockLiquid(mat).decide()) {
                // NOTE: do not set BlockFlags.F_GROUND for liquids ?
                BlockFlags.setFlag(mat, BlockFlags.F_LIQUID);
                if (mcAccess.isBlockSolid(mat).decide()) BlockFlags.setFlag(mat, BlockFlags.F_SOLID);
            }
            else if (mcAccess.isBlockSolid(mat).decide()) {
                BlockFlags.setFlag(mat, BlockFlags.F_SOLID | BlockFlags.F_GROUND);
            }
        }

        BlockProps props;

        registerSlabs(mcAccess, worldConfigProvider);
        registerRedstoneComponents();
        registerFluidBlocks(mcAccess);
        // --- split former registerMiscSpecialBlocks() into focused helpers ---
        registerClimbables();
        registerGroundOverrides();
        registerPistonOverrides();
        registerIgnorePassables();
        registerFenceHeights();
        registerThinFences();
        registerSpecialStaticFlags();
        registerInstantBreakables();
        registerPassableInstantBreakables();
        registerLeafBlocks();
        registerBedBlocks();

        //////////////////////////////////////////////////////////////////
        // Set block break properties.                                  //
        //////////////////////////////////////////////////////////////////
        setBlock(Material.LADDER, new BlockProps(noTool, 0.4f));

        setBlock(Material.CACTUS, new BlockProps(noTool, 0.4f));

        setBlock(Material.BEACON, new BlockProps(noTool, 3f)); 

        setBlock(Material.DRAGON_EGG, new BlockProps(noTool, 3f)); // Former: coalType.

        setBlock(BridgeMaterial.MELON, new BlockProps(noTool, 1));

        setBlock(Material.SPONGE, new BlockProps(noTool, 0.6f));

        setBlock(Material.NETHERRACK, new BlockProps(woodPickaxe, 0.4f, true));

        setBlock(Material.STONE_BUTTON,new BlockProps(woodPickaxe, 0.5f, true));

        setBlock(Material.ICE, new BlockProps(woodPickaxe, 0.5f));
        
        setBlock(Material.BREWING_STAND, new BlockProps(woodPickaxe, 0.5f, true));

        setBlock(Material.ENDER_CHEST, new BlockProps(woodPickaxe, 22.5f, true));

        setBlock(Material.SNOW, new BlockProps(getToolProps(BridgeMaterial.WOODEN_SHOVEL), 0.1f));

        setBlock(Material.SNOW_BLOCK, new BlockProps(getToolProps(BridgeMaterial.WOODEN_SHOVEL), 0.2f));

        setBlock(Material.NOTE_BLOCK, new BlockProps(woodAxe, 0.8f));

        setBlock(Material.BOOKSHELF, new BlockProps(woodAxe, 1.5f));

        setBlock(BridgeMaterial.COBWEB, new BlockProps(woodSword, 4, true));

        setBlock(Material.OBSIDIAN, new BlockProps(diamondPickaxe, 50, true));


        // Instantly breakable, leaf and bed blocks are registered via helpers

        registerHugeMushroomBlocks();
        registerGlassBlocks();
        registerPressurePlates();
        registerSandAndGravelBlocks();
        registerLeverAndPistonBlocks();
        registerMiscBlockSets();
        registerOreAndMineralBlocks();
        registerToolSensitiveBlocks();
        registerIndestructibleBlocks();
        registerSpecialBlocks();
        registerBoundsAndPassables();
    }

    /**
     * Set breaking time overrides for specific side conditions. Starting at
     * efficiency 0, the breaking time is fetched from the given times array,
     * with efficiency level being the index, always starting at 0.
     * 
     * @param baseKey
     * @param times
     */
    public static void setBreakingTimeOverridesByEfficiency(final BlockBreakKey baseKey, final long... times) {
        for (int i = 0; i < times.length; i++) {
            setBreakingTimeOverride(new BlockBreakKey(baseKey).efficiency(i), times[i]);
        }
    }

    /**
     * Dump blocks.
     *
     * @param all
     *            the all
     */
    public static void dumpBlocks(boolean all) {
        // Dump name-based setup.
        //StaticLog.logDebug("All non-legacy Material found: " + StringUtil.join(BridgeMaterial.getKeySet(), ", "));
        //StaticLog.logDebug("Dump Material collections:");
        //MaterialUtil.dumpStaticSets(MaterialUtil.class, Level.FINE);

        // Check for initialized block breaking data.
        /*
         * NOTE: Possibly switch to a class per block, to see what/if-anything
         * is initialized, including flags.
         */
        final LogManager logManager = NCPAPIProvider.getNoCheatPlusAPI().getLogManager();
        List<String> missing = new LinkedList<String>();
        List<String> allBlocks = new LinkedList<String>();
        if (all) {
            allBlocks.add("Dump block properties:");
            allBlocks.add("--- Present entries -------------------------------");
        }
        List<String> tags = new ArrayList<String>();
        for (Material temp : ALL_MATERIALS) {
            String mat;
            try {
                if (!temp.isBlock()) {
                    continue;
                }
                mat = temp.toString();
            }
            catch (Exception e) {
                mat = "?";
            }
            tags.clear();
            BlockFlags.addFlagNames(BlockFlags.getBlockFlags(temp), tags);
            String tagsJoined = tags.isEmpty() ? "" : " / " + StringUtil.join(tags, "+");
            if (blocks.get(temp) == null) {
                if (mat.equals("?")) {
                    continue;
                }
                missing.add("* BLOCK BREAKING (" + mat + tagsJoined + ") ");
            }
            else {
                if (BlockFlags.getBlockFlags(temp) == 0L && !isAir(temp)) {
                    missing.add("* FLAGS (" + mat + tagsJoined + ") " + getBlockProps(temp).toString());
                }
                if (all) {
                    allBlocks.add(": (" + mat + tagsJoined + ") " + getBlockProps(temp).toString());
                }
            }
        }
        if (all) {
            logManager.info(Streams.DEFAULT_FILE, StringUtil.join(allBlocks, "\n"));
        }
        if (!missing.isEmpty()) {
            missing.add(0, "--- Missing entries -------------------------------");
            missing.add(0, "The block data is incomplete:");
            logManager.warning(Streams.INIT, StringUtil.join(missing,  "\n"));
        }
    }


    /**
     * Sec to ms.
     *
     * @param s1
     *            the s1
     * @param s2
     *            the s2
     * @param s3
     *            the s3
     * @param s4
     *            the s4
     * @param s5
     *            the s5
     * @param s6
     *            the s6
     * @return the long[]
     */
    public static long[] secToMs(final double s1, final double s2, final double s3, final double s4, final double s5, final double s6, final double s7) {
        return new long[] { (long) (s1 * 1000d), (long) (s2 * 1000d), (long) (s3 * 1000d), (long) (s4 * 1000d), (long) (s5 * 1000d), (long) (s6 * 1000d), (long) (s7 * 1000d) };
    }

    /**
     * Sec to ms.
     *
     * @param s1
     *            the s1
     * @return the long[]
     */
    public static long[] secToMs(final double s1) {
        final long v = (long) (s1 * 1000d);
        return new long[]{v, v, v, v, v, v, v};
    }

    /**
     * Gets the tool props.
     *
     * @param stack
     *            the stack
     * @return the tool props
     */
    public static ToolProps getToolProps(final ItemStack stack) {
        if (stack == null) {
            return noTool;
        }
        else {
            return getToolProps(stack.getType());
        }
    }

    /**
     * Gets the tool props.
     *
     * @param mat
     *            the mat
     * @return the tool props
     */
    public static ToolProps getToolProps(final Material mat) {
        final ToolProps props = tools.get(mat);
        if (props == null) {
            return noTool;
        }
        else {
            return props;
        }
    }

    /**
     * Gets the block props.
     *
     * @param stack
     *            the stack
     * @return the block props
     */
    public static BlockProps getBlockProps(final ItemStack stack) {
        if (stack == null) {
            return defaultBlockProps;
        }
        else {
            return getBlockProps(stack.getType());
        }
    }

    /**
     * Gets the block props.
     *
     * @param mat
     *            the mat
     * @return the block props
     */
    public static BlockProps getBlockProps(final Material mat) {
        if (mat == null || !blocks.containsKey(mat)) {
            return defaultBlockProps;
        }
        else {
            return blocks.get(mat);
        }
    }

    /**
     * Gets the block props.
     *
     * @param blockId
     *            the block id
     * @return the block props
     */
    public static BlockProps getBlockProps(final String blockId) {
        return getBlockProps(BlockProperties.getMaterial(blockId));
    }

    /**
     * Set a breaking time override for specific side conditions. Copies the key
     * for internal storage.
     * 
     * @param key
     * @param breakingTime
     *            The breaking time in milliseconds.
     */
    public static void setBreakingTimeOverride(final BlockBreakKey key, long breakingTime) {
        breakingTimeOverrides.put(new BlockBreakKey(key), breakingTime);
    }

    /**
     * Get a breaking time override for specific side conditions.
     * 
     * @param key
     * @return The breaking time in milliseconds or null, if not set.
     */
    public static Long getBreakingTimeOverride(final BlockBreakKey key) {
        return breakingTimeOverrides.get(key);
    }

    /**
     * Convenience method.
     *
     * @param BlockType
     *            the block type
     * @param player
     *            the player
     * @return the breaking duration
     */
    public static long getBreakingDuration(final Material BlockType, final Player player) {
        if (player == null) {
            return 0L;
        }
        final PlayerInventory inventory = player.getInventory();
        final long res = getBreakingDuration(BlockType,
                Bridge1_9.getItemInMainHand(player),
                inventory != null ? inventory.getHelmet() : null,
                player, player.getLocation(useLoc));
        useLoc.setWorld(null);
        return res;
    }

    /**
     * Convenience method.
     * 
     * @param blockId
     * @param itemInHand
     *            May be null.
     * @param helmet
     *            May be null.
     * @param player
     * @param location
     * @return
     */
    public static long getBreakingDuration(final Material blockId, final ItemStack itemInHand, final ItemStack helmet, final Player player, final Location location) {
        return getBreakingDuration(blockId, itemInHand, helmet, player, MovingUtil.getEyeHeight(player), location);
    }

    /**
     * Convenience method.
     * @param blockId
     * @param itemInHand
     * @param helmet
     * @param player
     * @param eyeHEight
     * @param location
     * @return
     */
    public static long getBreakingDuration(final Material blockId, final ItemStack itemInHand, final ItemStack helmet, 
                                           final Player player, final double eyeHeight, final Location location) {

        final BlockCache blockCache = wrapBlockCache.getBlockCache();
        blockCache.setAccess(location.getWorld());
        pLoc.setBlockCache(blockCache);
        pLoc.set(location, player, 0.3);
        // On ground.
        final boolean onGround = pLoc.isOnGround();
        // Head in water.
        final int bx = pLoc.getBlockX();
        final int bz = pLoc.getBlockZ();
        final double y = pLoc.getY() + eyeHeight;
        final int by = Location.locToBlock(y);
        final Material headId = blockCache.getType(bx, by, bz);
        final long headFlags = BlockFlags.getBlockFlags(headId);
        final boolean inWater;
        if ((headFlags & BlockFlags.F_WATER) == 0) {
            inWater = false;
        }
        else {
            // Check real bounding box collision.
            // (Not sure which to use here.)
            final int data8 = (blockCache.getData(bx, by, bz) & 0xF) % 8;
            final double level;
            if ((data8 & 8) != 0) {
                level = 1.0;
            }
            else {
                level = 1.0 - 0.125 * (1.0 + data8);
            }
            inWater = y - by < level; // <= ? ...
        }
        blockCache.cleanup();
        pLoc.cleanup();
        // Haste (faster digging).
        final double haste = PotionUtil.getPotionEffectAmplifier(player, BridgePotionEffect.HASTE);
        final double fatigue = PotionUtil.getPotionEffectAmplifier(player, BridgePotionEffect.MINING_FATIGUE);
        final double conduit = Bridge1_13.getConduitPowerAmplifier(player);
        return getBreakingDuration(blockId, itemInHand, onGround, inWater,
                helmet != null && helmet.containsEnchantment(BridgeEnchant.AQUA_AFFINITY), 
                Double.isInfinite(haste) ? 0 : 1 + (int) haste, 
                Double.isInfinite(fatigue) ? 0 : 1 + (int) fatigue,
                Double.isInfinite(conduit) ? 0 : 1 + (int) conduit        
                );
    }

    /**
     * Get the normal breaking duration, including enchantments, and tool
     * properties.
     *
     * @param blockId
     *            the block id
     * @param itemInHand
     *            the item in hand
     * @param onGround
     *            the on ground
     * @param inWater
     *            the in water
     * @param aquaAffinity
     *            the aqua affinity
     * @param haste
     *            the haste
     * @param conduit
     *            the conduit power
     * @return the breaking duration
     */
    public static long getBreakingDuration(final Material blockId, final ItemStack itemInHand, 
                                           final boolean onGround, final boolean inWater, final boolean aquaAffinity, 
                                           final int haste, final int fatigue, final int conduit) {

        // NOTE: more configurability / load from file for blocks (i.e. set for shears etc.
        if (isAir(itemInHand)) {
            return getBreakingDuration(blockId, getBlockProps(blockId), noTool, onGround, inWater, aquaAffinity, 0, haste, fatigue, conduit); // Nor efficiency do apply.
        }
        else {
            int efficiency = 0;
            if (itemInHand.containsEnchantment(BridgeEnchant.EFFICIENCY)) {
                efficiency = itemInHand.getEnchantmentLevel(BridgeEnchant.EFFICIENCY);
            }
            return getBreakingDuration(blockId, getBlockProps(blockId), getToolProps(itemInHand.getType()), 
                                       onGround, inWater, aquaAffinity, efficiency, haste, fatigue, conduit);
        }
    }

    /**
     * 
     * @param blockId
     * @param blockProps
     * @param toolProps
     * @param onGround
     * @param inWater
     * @param aquaAffinity
     * @param efficiency
     * @return the breaking duration
     * @deprecated Public method not containing haste, fatigue. Use
     *             {@link fr.neatmonster.nocheatplus.history.HistoryService#getBreakingDuration(Material, BlockProps, ToolProps, boolean, boolean, boolean, int, int, int, int)}
     *             instead. Removal scheduled for 2.0.
     *             <p>
     *             Migration: call the history service method providing haste,
     *             fatigue and other modifiers as needed.
     *             </p>
     */
    public static long getBreakingDuration(final Material blockId, final BlockProps blockProps, final ToolProps toolProps,
                                           final  boolean onGround, final boolean inWater, boolean aquaAffinity, int efficiency) {
        return getBreakingDuration(blockId, blockProps, toolProps, onGround, inWater, aquaAffinity, efficiency, 0, 0, 0);
    }

    /**
     * Gets the breaking duration.
     * 
     * @param blockId
     * @param blockProps
     * @param toolProps
     * @param onGround
     * @param inWater
     * @param aquaAffinity
     * @param efficiency
     * @param haste
     *        Amplifier of haste potion effect (assume > 0 for effect there
     *            at all, so 1 is haste I, 2 is haste II,...).
     * @param fatigue
     *        Amplifier of mining fatigue potion effect (assume > 0 for effect there
     *            at all, so 1 is fatigue I, 2 is fatigue II,...).
     * @param conduit
     *        Amplifier of conduit power potion effect (assume > 0 for effect there
     *            at all, so 1 is conduit power I, 2 is conduit power II,...).
     * @return
     */
    public static long getBreakingDuration(final Material blockId, final BlockProps blockProps, final ToolProps toolProps,
                                           final boolean onGround, final boolean inWater, boolean aquaAffinity,
                                           int efficiency, int haste, int fatigue, int conduit) {
        // First check for direct breaking time overrides.
        final BlockBreakKey bbKey = new BlockBreakKey();
        bbKey.blockType(blockId).toolType(toolProps.toolType).materialBase(toolProps.materialBase).efficiency(efficiency);
        Long override = breakingTimeOverrides.get(bbKey);
        if (override != null) {
            float mult = getBlockBreakingPenaltyMultiplier(onGround, inWater, aquaAffinity);
            return mult == 1.0f ? override : (long) (mult * override);
        }

        BreakContext ctx = determineBaseDuration(blockId, blockProps, toolProps, efficiency);
        ctx = applySpecialToolCases(blockId, toolProps, ctx);

        return calculateFinalDuration(blockId, blockProps, toolProps, ctx.duration,
                                      ctx.validTool, ctx.rightTool, ctx.pureHardness,
                                      onGround, inWater, aquaAffinity, efficiency,
                                      haste, fatigue, conduit);
    }
    
    /**
     * @param onGround
     * @param inWater
     * @param aquaAffinity
     * @return the multiplier
     */
    private static float getBlockBreakingPenaltyMultiplier(final boolean onGround, final boolean inWater, final boolean aquaAffinity) {
        float mult = 1f;
        if (inWater && !aquaAffinity) {
            mult *= breakPenaltyInWater;
        }
        if (!onGround) {
            mult *= breakPenaltyOffGround;
        }
        return mult;
    }

    /** Simple container for mutable break calculation data. */
    private static final class BreakContext {
        long duration;
        boolean validTool;
        boolean rightTool;
        boolean pureHardness;

        BreakContext(long duration, boolean validTool, boolean rightTool, boolean pureHardness) {
            this.duration = duration;
            this.validTool = validTool;
            this.rightTool = rightTool;
            this.pureHardness = pureHardness;
        }

        BreakContext(BreakContext other) {
            this.duration = other.duration;
            this.validTool = other.validTool;
            this.rightTool = other.rightTool;
            this.pureHardness = other.pureHardness;
        }
    }

    @FunctionalInterface
    private interface BreakModifier {
        BreakContext apply(Material blockId, ToolProps toolProps, BreakContext ctx);
    }

    private static final List<BreakModifier> SPECIAL_CASE_MODIFIERS = new ArrayList<>();

    static {
        SPECIAL_CASE_MODIFIERS.add((blockId, tool, ctx) -> {
            if (tool.toolType == ToolType.SHEARS && blockId == BridgeMaterial.COBWEB) {
                ctx.duration = 400;
                ctx.validTool = true;
                ctx.rightTool = true;
                ctx.pureHardness = false;
            }
            return ctx;
        });
        SPECIAL_CASE_MODIFIERS.add((blockId, tool, ctx) -> {
            if (tool.toolType == ToolType.SHEARS && MaterialUtil.WOOL_BLOCKS.contains(blockId)) {
                ctx.duration = 240;
                ctx.validTool = true;
                ctx.rightTool = true;
                ctx.pureHardness = false;
            }
            return ctx;
        });
        SPECIAL_CASE_MODIFIERS.add((blockId, tool, ctx) -> {
            if (tool.toolType == ToolType.SHEARS && isLeaves(blockId)) {
                ctx.duration = 0;
                ctx.validTool = true;
            }
            return ctx;
        });
        SPECIAL_CASE_MODIFIERS.add((blockId, tool, ctx) -> {
            if (tool.toolType == ToolType.SWORD && (blockId == Material.JACK_O_LANTERN || blockId.name().endsWith("PUMPKIN") || blockId == Material.MELON)) {
                ctx.validTool = true;
                ctx.rightTool = true;
                ctx.duration = 1000;
                ctx.pureHardness = false;
            }
            return ctx;
        });
        SPECIAL_CASE_MODIFIERS.add((blockId, tool, ctx) -> {
            if (tool.toolType == ToolType.SWORD && (blockId == Material.COCOA || blockId == Material.VINE || isLeaves(blockId))) {
                ctx.validTool = true;
                ctx.rightTool = true;
                ctx.duration = 200;
                ctx.pureHardness = false;
            }
            return ctx;
        });
        SPECIAL_CASE_MODIFIERS.add((blockId, tool, ctx) -> {
            if (tool.toolType == ToolType.SWORD && blockId.name().equals("BAMBOO")) {
                ctx.validTool = true;
                ctx.duration = 0;
            }
            return ctx;
        });
    }

    private static BreakContext determineBaseDuration(Material blockId, BlockProps blockProps, ToolProps toolProps, int efficiency) {
        boolean validTool = isValidTool(blockId, blockProps, toolProps, efficiency);
        boolean rightTool = isRightToolMaterial(blockId, blockProps, toolProps, validTool);
        long duration = validTool ? blockProps.breakingTimes[toolProps.materialBase.index]
                                  : blockProps.breakingTimes[0];
        if (validTool && efficiency > 0) {
            duration = (long) (duration / blockProps.efficiencyMod);
        }
        return new BreakContext(duration, validTool, rightTool, blockProps.pureHardness);
    }

    /**
     * Apply tool-specific overrides registered as {@link BreakModifier}s.
     * Each modifier receives a fresh copy of the current context to keep
     * mutation local and returns an updated context.
     */
    private static BreakContext applySpecialToolCases(Material blockId, ToolProps toolProps, BreakContext ctx) {
        for (BreakModifier bm : SPECIAL_CASE_MODIFIERS) {
            ctx = bm.apply(blockId, toolProps, new BreakContext(ctx));
        }
        return ctx;
    }

    private static double applyFatigue(double speed, int fatigue) {
        if (fatigue <= 0) {
            return speed;
        }
        switch (fatigue) {
            case 1:
                return speed * 0.3;
            case 2:
                return speed * 0.09;
            case 3:
                return speed * 0.027;
            default:
                return speed * 0.00081;
        }
    }

    private static long calculateFinalDuration(Material blockId, BlockProps blockProps, ToolProps toolProps,
                                               long duration, boolean isValidTool, boolean isRightTool, boolean pureHardness,
                                               boolean onGround, boolean inWater, boolean aquaAffinity,
                                               int efficiency, int haste, int fatigue, int conduit) {
        if (duration <= 0) {
            return Math.max(0, duration);
        }

        double hardness = blockProps.hardness;
        double damage;
        if (!pureHardness) {
            float tick = duration / 50f;
            damage = 1 / tick;
            hardness = 1 / damage / ((isRightTool || !blockProps.requireCorrectTool ? 30 : 100)
                    / (isValidTool ? getToolMultiplier(blockId, blockProps, toolProps) : 1.0));
        }
        if (hardness <= 0.0) {
            return Math.max(0, duration);
        }

        double speed = isValidTool ? getToolMultiplier(blockId, blockProps, toolProps) : 1.0;
        if (isValidTool && efficiency > 0) {
            speed += efficiency * efficiency + 1;
        }
        if (haste > 0 || conduit > 0) {
            speed *= 1 + 0.2 * Math.max(haste, conduit);
        }
        speed = applyFatigue(speed, fatigue);
        speed /= getBlockBreakingPenaltyMultiplier(onGround, inWater, aquaAffinity);
        damage = speed / hardness;
        damage /= isRightTool || !blockProps.requireCorrectTool ? 30.0 : 100.0;
        if (damage > 1) {
            return 0;
        }
        return Math.round(1 / damage) * 50;
    }

    /**
     * Check if the tool is officially appropriate for the block id
     *
     * @param blockId
     *            the block id
     * @param blockProps
     *            the block props
     * @param toolProps
     *            the tool props
     * @param efficiency
     *            the efficiency
     * @return true, if is valid tool
     */
    public static boolean isValidTool(final Material blockId, final BlockProps blockProps, 
                                      final ToolProps toolProps, final int efficiency) {
        return blockProps.tool.toolType == toolProps.toolType && blockProps.tool != noTool;
    }

    /**
     * Convenient method
     * 
     * @param blockId
     * @param blockProps
     * @param toolProps
     * @param isValidTool
     * @return
     */
    public static boolean isRightToolMaterial(final Material blockId, final BlockProps blockProps, final ToolProps toolProps, final boolean isValidTool) {
        return isRightToolMaterial(blockId, blockProps.tool.materialBase, toolProps.materialBase, isValidTool);
    }

    /**
     * 
     * @param blockId
     *            the block id
     * @param blockMat
     *            the minimum material that block required to be drop 
     * @param toolMat
     *            the material of the tool to test
     * @param isValidTool
     *            is match with correct tool type
     * @return true, if reach the minimum require of the block
     */
    public static boolean isRightToolMaterial(final Material blockId, final MaterialBase blockMat, final MaterialBase toolMat, final boolean isValidTool) {
        if (blockMat == MaterialBase.WOOD) {
            switch(toolMat) {
            case DIAMOND:
            case GOLD:
            case IRON:
            case NETHERITE:
            case STONE:
            case WOOD:
                return isValidTool;
            default:
                return false;
            }
        }
        if (blockMat == MaterialBase.STONE) {
            switch(toolMat) {
            case DIAMOND:
            case IRON:
            case NETHERITE:
            case STONE:
                return isValidTool;
            default:
                return false;
            }
        }
        if (blockMat == MaterialBase.IRON) {
            switch(toolMat) {
            case DIAMOND:
            case IRON:
            case NETHERITE:
                return isValidTool;
            default:
                return false;
            }
        }
        if (blockMat == MaterialBase.DIAMOND) {
            switch(toolMat) {
            case DIAMOND:
            case NETHERITE:
                return isValidTool;
            default:
                return false;
            }
        }
        return isValidTool;
    }

    /**
     * Get the speed multiplier from tool (No valid tool check, no null material base check)
     * 
     * @param blockId
     *            the block id
     * @param blockProps
     *            the block props
     * @param toolProps
     *            the tool props
     * @return the multiplier
     */
    public static float getToolMultiplier(final Material blockId, final BlockProps blockProps, final ToolProps toolProps) {
        if (toolProps.toolType == ToolType.SWORD) {
            if (blockId == BridgeMaterial.COBWEB) {
                return 15f;
            }
            return 1.5f;
        }
        if (toolProps.toolType == ToolType.SHEARS) {
            if (blockId == BridgeMaterial.COBWEB) {
                return 15f;
            }
            else if (MaterialUtil.WOOL_BLOCKS.contains(blockId)) {
                return 5f;
            }
            // No need as duration = 0
            //else if (isLeaves(blockId)) {
            //    return 15f;
            //}
            return 1.5f;
        }
        return toolProps.materialBase.breakMultiplier;
    }

    /**
     * Access API for setting tool properties.<br>
     * NOTE: No guarantee that this harmonizes with internals and workarounds,
     * currently.
     *
     * @param itemId
     *            the item id
     * @param toolProps
     *            the tool props
     */
    public static void setToolProps(Material itemId, ToolProps toolProps) {
        if (toolProps == null) {
            throw new NullPointerException("ToolProps must not be null");
        }
        toolProps.validate();
        // No range check.
        tools.put(itemId, toolProps);
    }

    /**
     * Access API to set a blocks properties. NOTE: No guarantee that this
     * harmonizes with internals and workarounds, currently.
     *
     * @param blockId
     *            the block id
     * @param blockProps
     *            the block props
     */
    public static void setBlockProps(String blockId, BlockProps blockProps) {
        setBlockProps(BlockProperties.getMaterial(blockId), blockProps);
    }

    /**
     * Access API to set a blocks properties. NOTE: No guarantee that this
     * harmonizes with internals and workarounds, currently.
     *
     * @param blockId
     *            the block id
     * @param blockProps
     *            the block props
     */
    public static void setBlockProps(Material blockId, BlockProps blockProps) {
        if (blockProps == null) {
            throw new NullPointerException("BlockProps must not be null");
        }
        blockProps.validate();
        setBlock(blockId, blockProps);
    }

    /**
     * Checks if is valid tool.
     *
     * @param blockType
     *            the block type
     * @param itemInHand
     *            the item in hand
     * @return true, if is valid tool
     */
    public static boolean isValidTool(final Material blockType, final ItemStack itemInHand) {
        final BlockProps blockProps = getBlockProps(blockType);
        final ToolProps toolProps = getToolProps(itemInHand);
        final int efficiency = itemInHand == null ? 0 : itemInHand.getEnchantmentLevel(BridgeEnchant.EFFICIENCY);
        return isValidTool(blockType, blockProps, toolProps, efficiency);
    }

    /**
     * Gets the default block props.
     *
     * @return the default block props
     */
    public static BlockProps getDefaultBlockProps() {
        return defaultBlockProps;
    }

    /**
     * Feeding null will cause an npe - will validate.
     *
     * @param blockProps
     *            the new default block props
     */
    public static void setDefaultBlockProps(BlockProps blockProps) {
        blockProps.validate();
        BlockProperties.defaultBlockProps = blockProps;
    }

    /**
     * Simple checking method, heavy. No isIllegal check.
     *
     * @param player
     *            the player
     * @param location
     *            the location
     * @param yOnGround
     *            the y on ground
     * @return true, if is in liquid
     */
    public static boolean isInLiquid(final Player player, final Location location, final double yOnGround) {
        // Bit fat workaround, maybe put the object through from check listener ?
        final BlockCache blockCache = wrapBlockCache.getBlockCache();
        blockCache.setAccess(location.getWorld());
        pLoc.setBlockCache(blockCache);
        pLoc.set(location, player, yOnGround);
        final boolean res = pLoc.isInLiquid();
        blockCache.cleanup();
        pLoc.cleanup();
        return res;
    }

    /**
     * Simple checking method, heavy. No isIllegal check.
     *
     * @param player
     *            the player
     * @param location
     *            the location
     * @param yOnGround
     *            the y on ground
     * @return true, if is in web
     */
    public static boolean isInWeb(final Player player, final Location location, final double yOnGround) {
        // Bit fat workaround, maybe put the object through from check listener ?
        final BlockCache blockCache = wrapBlockCache.getBlockCache();
        blockCache.setAccess(location.getWorld());
        pLoc.setBlockCache(blockCache);
        pLoc.set(location, player, yOnGround);
        final boolean res = pLoc.isInWeb();
        blockCache.cleanup();
        pLoc.cleanup();
        return res;
    }

    /**
     * Simple checking method, heavy. No isIllegal check.
     *
     * @param player
     *            the player
     * @param location
     *            the location
     * @param yOnGround
     *            the y on ground
     * @return true, if is on ground
     */
    public static boolean isOnGround(final Player player, final Location location, final double yOnGround) {
        // Bit fat workaround, maybe put the object through from check listener ?
        final BlockCache blockCache = wrapBlockCache.getBlockCache();
        blockCache.setAccess(location.getWorld());
        pLoc.setBlockCache(blockCache);
        pLoc.set(location, player, yOnGround);
        final boolean res = pLoc.isOnGround();
        blockCache.cleanup();
        pLoc.cleanup();
        return res;
    }

    /**
     * Simple checking method, heavy. No isIllegal check.
     *
     * @param player
     *            the player
     * @param location
     *            the location
     * @param yOnGround
     *            the y on ground
     * @return true, if is on ground or reset cond
     */
    public static boolean isOnGroundOrResetCond(final Player player, final Location location, 
            final double yOnGround) {
        final BlockCache blockCache = wrapBlockCache.getBlockCache();
        blockCache.setAccess(location.getWorld());
        pLoc.setBlockCache(blockCache);
        pLoc.set(location, player, yOnGround);
        final boolean res = pLoc.isOnGroundOrResetCond();
        blockCache.cleanup();
        pLoc.cleanup();
        return res;
    }

    /**
     * Simple checking method, heavy. No isIllegal check.
     *
     * @param player
     *            the player
     * @param location
     *            the location
     * @param yOnGround
     *            the y on ground
     * @return true, if is reset cond
     */
    public static boolean isResetCond(final Player player, final Location location, final double yOnGround) {
        final BlockCache blockCache = wrapBlockCache.getBlockCache();
        blockCache.setAccess(location.getWorld());
        pLoc.setBlockCache(blockCache);
        pLoc.set(location, player, yOnGround);
        final boolean res = pLoc.isResetCond();
        blockCache.cleanup();
        pLoc.cleanup();
        return res;
    }

    /**
     * Get the (legacy) data value for the block.
     *
     * @param block
     *            the block
     * @return the data
     */
    public static int getData(final Block block) {
        return block.getData();
    }

    /**
     * Straw-man method to hide warnings.
     *
     * @param id
     *            the id
     * @return the material
     */
    public static Material getMaterial(final String id) {
        return Material.valueOf(id);
    }

    /**
     * Auxiliary check for if this block is climbable and allows climbing up.
     * Does not account for jumping off ground etc.
     *
     * @param cache
     *            the cache
     * @param x
     *            the x
     * @param y
     *            the y
     * @param z
     *            the z
     * @return true, if successful
     */
    public static final boolean canClimbUp(final BlockCache cache, final int x, final int y, final int z) {
        final Material id = cache.getType(x, y, z);
        if ((BlockFlags.getBlockFlags(id) & BlockFlags.F_CLIMBABLE) == 0) {
            return false;
        }
        if (id == Material.LADDER) {
            return true;
        }
        // The direct way is a problem (backwards compatibility to before 1.4.5-R1.0).
        if ((BlockFlags.getBlockFlags(cache.getType(x + 1, y, z)) & BlockFlags.F_SOLID) != 0) {
            return true;
        }
        if ((BlockFlags.getBlockFlags(cache.getType(x - 1, y, z)) & BlockFlags.F_SOLID) != 0) {
            return true;
        }
        if ((BlockFlags.getBlockFlags(cache.getType(x, y, z + 1)) & BlockFlags.F_SOLID) != 0) {
            return true;
        }
        if ((BlockFlags.getBlockFlags(cache.getType(x, y, z - 1)) & BlockFlags.F_SOLID) != 0) {
            return true;
        }
        return false;
    }

    /**
     * Get the facing direction as BlockFace, unified approach with attached to
     * = opposite of facing. Likely the necessary flags are just set where it's
     * been needed so far.
     *
     * @param flags
     *            the flags
     * @param data
     *            the data
     * @return Return null, if facing can not be determined.
     */
    public static final BlockFace getFacing(final long flags, final int data) {
        if ((flags & BlockFlags.F_FACING_LOW3D2_NSWE) != 0L) {
            switch(data & 7) {
                case 3:
                    return BlockFace.SOUTH;
                case 4:
                    return BlockFace.WEST;
                case 5:
                    return BlockFace.EAST;
                default: // 2 and invalid states.
                    return BlockFace.NORTH;
            }

        }
        else if ((flags & BlockFlags.F_ATTACHED_LOW2_SNEW) != 0L) {
            switch (data & 3) {
                case 0:
                    return BlockFace.NORTH; // Attached to BlockFace.SOUTH;
                case 1:
                    return BlockFace.SOUTH; // Attached to BlockFace.NORTH;
                case 2:
                    return BlockFace.WEST; // Attached to BlockFace.EAST;
                case 3:
                    return BlockFace.EAST; // Attached to BlockFace.WEST;
            }
        }
        return null;
    }

    /**
     * Special case: Trap door above ladder attached to lower and of block, same
     * facing. Thus the trap door can be climbed up like a ladder.<br>
     * Suggested fast precondition checks are for nearby flags covering this and
     * below:
     * <ul>
     * <li>F_PASSABLE_X4 (trap door at these coordinates).</li>
     * <li>F_CLIMBABLE (ladder below).</li>
     * </ul>
     *
     * @param access
     *            the access
     * @param x
     *            the x
     * @param y
     *            the y
     * @param z
     *            the z
     * @return true, if is trap door above ladder special case
     */
    public static final boolean isTrapDoorAboveLadderSpecialCase(final BlockCache access, final int x, final int y, final int z) {
        // Special case activation.
        if (!isSpecialCaseTrapDoorAboveLadder()) {
            return false;
        }
        // Basic flags and facing for trap door.
        final long flags1 = BlockFlags.getBlockFlags(access.getType(x, y, z));
        if (!MaterialUtil.ALL_TRAP_DOORS.contains(access.getType(x, y, z))) {
            return false;
        }
        // NOTE: Really confine to trap door types (add a flag or something else)?
        final int data1 = access.getData(x, y, z);
        // (Trap door may be attached to top or bottom, regardless.)
        // Trap door must be open (really?).
        if (data1 == 0) {
            return false;
        }
        // Need the facing direction.
        final BlockFace face1 = getFacing(flags1, data1);
        if (face1 == null) {
            return false;
        }
        // Basic flags and facing for ladder.
        final Material belowId = access.getType(x, y - 1, z);
        // Really confine to ladder here.
        if (belowId != Material.LADDER) {
            return false;
        }
        final long flags2 = BlockFlags.getBlockFlags(belowId);
        // (Type has already been checked.)
        //if ((flags2 & BlockFlags.F_CLIMBABLE) == 0) {
        //    return false;
        //}
        final int data2 = access.getData(x, y - 1, z);
        final BlockFace face2 = getFacing(flags2, data2);
        // Compare faces.
        if (face1 != face2) {
            return false;
        }
        return true;
    }

    /**
     * Just check if a position is not inside of a block that has a bounding
     * box.<br>
     * This is an inaccurate check, it also returns false for doors etc.
     *
     * @param blockType
     *            the block type
     * @return true, if is passable
     */
    public static final boolean isPassable(final Material blockType) {
        final long flags = BlockFlags.getBlockFlags(blockType);
        // NOTE: What with non-solid blocks that are not passable ?
        if ((flags & (BlockFlags.F_LIQUID | BlockFlags.F_IGN_PASSABLE)) != 0) {
            return true;
        }
        else {
            return (flags & BlockFlags.F_GROUND) == 0;
        }
    }

    /**
     * Test if a position can be passed through (collidesBlock + passable test,
     * no fences yet).<br>
     * NOTE: This is experimental.
     *
     * @param access
     *            the access
     * @param x
     *            the x
     * @param y
     *            the y
     * @param z
     *            the z
     * @param node
     *            The IBlockCacheNode instance for the given block coordinates.
     *            Not null.
     * @param nodeAbove
     *            The IBlockCacheNode instance above the given block
     *            coordinates. May be null.
     * @return true, if is passable
     */
    public static final boolean isPassable(final BlockCache access, final double x, final double y, final double z, 
                                           final IBlockCacheNode node, final IBlockCacheNode nodeAbove) {
        final Material id = node.getType();
        // Simple exclusion check first.
        if (isPassable(id)) {
            return true;
        }
        // Check if the position is inside of a bounding box.
        // NOTE: Consider to pass these as arguments too.
        final int bx = Location.locToBlock(x);
        final int by = Location.locToBlock(y);
        final int bz = Location.locToBlock(z);
        if (node.hasNonNullBounds() == AlmostBoolean.NO
            || !collidesBlock(access, x, y, z, x, y, z, bx, by, bz, node, nodeAbove, BlockFlags.getBlockFlags(id))) {
            return true;
        }

        final double fx = x - bx;
        final double fy = y - by;
        final double fz = z - bz;
        // NOTE: Check f_itchy if/once exists.
        // Check workarounds (blocks with bigger collision box but passable on some spots).
        if (!isPassableWorkaround(access, bx, by, bz, fx, fy, fz, node, 0, 0, 0, 0)) {
            // Not passable.
            return false;
        }
        return true;
    }

    /**
     * Checking the block below to account for fences and such. This must be
     * called extra to isPassable(...).
     *
     * @param access
     *            the access
     * @param x
     *            the x
     * @param y
     *            the y
     * @param z
     *            the z
     * @return true, if is passable h150
     */
    public static final boolean isPassableH150(final BlockCache access, final double x, final double y, final double z) {
        // Check for fences.
        final int by = Location.locToBlock(y) - 1;
        final double fy = y - by;
        if (fy >= 1.5) {
            return true;
        }
        final int bx = Location.locToBlock(x);
        final int bz = Location.locToBlock(z);
        final IBlockCacheNode nodeBelow = access.getOrCreateBlockCacheNode(x, y, z, false);
        final Material belowId = nodeBelow.getType();
        final long belowFlags = BlockFlags.getBlockFlags(belowId); 
        if ((belowFlags & BlockFlags.F_HEIGHT150) == 0 || isPassable(belowId)) {
            return true;
        }
        final double[] belowBounds = nodeBelow.getBounds(access, bx, by, bz);
        if (belowBounds == null) {
            return true;
        }
        if (!collidesBlock(access, x, y, z, x, y, z, bx, by, bz, nodeBelow, null, belowFlags)) {
            return true;
        }
        final double fx = x - bx;
        final double fz = z - bz;
        return isPassableWorkaround(access, bx, by, bz, fx, fy, fz, nodeBelow, 0, 0, 0, 0);
    }

    /**
     * Check if passable, including blocks with height 1.5.
     *
     * @param access
     *            the access
     * @param x
     *            the x
     * @param y
     *            the y
     * @param z
     *            the z
     * @param id
     *            the id
     * @return true, if is passable exact
     */
    public static final boolean isPassableExact(final BlockCache access, final double x, final double y, final double z) {
        return isPassable(access, x, y, z, access.getOrCreateBlockCacheNode(x, y, z, false), null) && isPassableH150(access, x, y, z);
    }

    /**
     * Check if passable, including blocks with height 1.5.
     *
     * @param access
     *            the access
     * @param loc
     *            the loc
     * @return true, if is passable exact
     */
    public static final boolean isPassableExact(final BlockCache access, final Location loc) {
        return isPassableExact(access, loc.getX(), loc.getY(), loc.getZ());
    }


    /**
     * Requires the hit-box of the block is hit (...): this checks for special
     * blocks properties such as glass panes and similar.<br>
     * Ray-tracing version for passable-workarounds.
     *
     * @param access
     *            the access
     * @param bx
     *            Block-coordinates.
     * @param by
     *            the by
     * @param bz
     *            the bz
     * @param fx
     *            Offset from block-coordinates in [0..1].
     * @param fy
     *            the fy
     * @param fz
     *            the fz
     * @param node
     *            The IBlockCacheNode instance for the given block coordinates.
     * @param dX
     *            Total ray distance for coordinated (see dT).
     * @param dY
     *            the d y
     * @param dZ
     *            the d z
     * @param dT
     *            Time to cover from given position in [0..1], relating to dX,
     *            dY, dZ.
     * @param minX 
     *            Bound to test
     * @param minY 
     *            Bound to test
     * @param minZ 
     *            Bound to test
     * @param maxX 
     *            Bound to test
     * @param maxY 
     *            Bound to test
     * @param maxZ 
     *            Bound to test
     * @return true, if is passable workaround
     */
    public static final boolean isPassableWorkaround(final BlockCache access, final int bx, final int by, final int bz, 
                                                     final double fx, final double fy, final double fz, 
                                                     final IBlockCacheNode node, final double dX, 
                                                     final double dY, final double dZ,
                                                     final double minX, final double minY, final double minZ,
                                                     final double maxX, final double maxY, final double maxZ,
                                                     final double dT) {
        // Note: Since this is only called if the bounding box collides, out-of-bounds checks should not be necessary.
        // NOTE: Add a flag if a workaround exists (!), might store the type of workaround extra (generic!), or extra flags.
        final Material id = node.getType();
        final long flags = BlockFlags.getBlockFlags(id);
        if ((flags & BlockFlags.F_PASSABLE_X4) != 0 && (access.getData(bx, by, bz) & 0x4) != 0) {
            // (Allow checking further entries.)
            return true; 
        }
        else if ((flags & BlockFlags.F_THICK_FENCE) != 0) {
            if (!collidesFence(fx, fz, dX, dZ, dT, 0.125)) {
                return true;
            }
        }
        else if ((flags & BlockFlags.F_THIN_FENCE) != 0) {
            if (!collidesFence(fx, fz, dX, dZ, dT, 0.0625)) {
                return true;
            }
            // NOTE: 0.974 is depend on Y_ON_GROUND_DEFAULT
            if (Math.min(fy, fy + dY * dT) < 0.974
                && !collidesBlock(access, minX, minY, minZ, maxX, maxY, maxZ, 
                                  bx, by, bz, node, null, flags | BlockFlags.F_FAKEBOUNDS)) {
                return true;
            }
            return false;
        }
        else if (id == Material.CAULDRON || id == Material.HOPPER) {
            if (Math.min(fy, fy + dY * dT) >= getGroundMinHeight(access, bx, by, bz, node, flags)) {
                // Check for moving through walls or floor.
                // NOTE: Maybe this is too exact...
                return isInsideCenter(fx, fz, dX, dZ, dT, 0.125);
            }
        }
        else if ((flags & BlockFlags.F_GROUND_HEIGHT) != 0
                && getGroundMinHeight(access, bx, by, bz, node, flags) <= Math.min(fy, fy + dY * dT)) {
            return true;
        } 
        else if (id.toString().equals("CHORUS_PLANT") && !collidesFence(fx, fz, dX, dZ, dT, 0.3)) {
             return true;
        }
        //else if (id.toString().equals("BAMBOO")) return true;
        // Nothing found.
        return false;
    }

    /**
     * Convenient method.
     *
     * @param access
     *            the access
     * @param bx
     *            Block-coordinates.
     * @param by
     *            the by
     * @param bz
     *            the bz
     * @param fx
     *            Offset from block-coordinates in [0..1].
     * @param fy
     *            the fy
     * @param fz
     *            the fz
     * @param node
     *            The IBlockCacheNode instance for the given block coordinates.
     * @param dX
     *            Total ray distance for coordinated (see dT).
     * @param dY
     *            the d y
     * @param dZ
     *            the d z
     * @param dT
     *            Time to cover from given position in [0..1], relating to dX,
     *            dY, dZ.
     * @return true, if is passable workaround
     */
    public static final boolean isPassableWorkaround(final BlockCache access, final int bx, final int by, 
                                                    final int bz, final double fx, final double fy, final double fz, 
                                                    final IBlockCacheNode node, final double dX, final double dY, 
                                                    final double dZ, final double dT) {
        return isPassableWorkaround(access, bx, by, bz, fx, fy, fz, node, dX, dY, dZ,
                                    fx + bx, fy + by, fz + bz, fx + bx, fy + by, fz + bz, dT);
    }

    /**
     * XZ-collision check for (bounds / pseudo-ray) with fence type blocks
     * (fences, glass panes), margin configurable.
     *
     * @param fx
     *            the fx
     * @param fz
     *            the fz
     * @param dX
     *            the d x
     * @param dZ
     *            the d z
     * @param dT
     *            the d t
     * @param d
     *            Distance to the fence middle to keep (see code of
     *            isPassableworkaround for reference).
     * @return true, if successful
     */
    public static boolean collidesFence(final double fx, final double fz, final double dX, final double dZ, final double dT, final double d) {
        final double dFx = 0.5 - fx;
        final double dFz = 0.5 - fz;
        if (Math.abs(dFx) > d && Math.abs(dFz) > d) {
            // Check moving between quadrants.
            final double dFx2 = 0.5 - (fx + dX * dT);
            final double dFz2 = 0.5 - (fz + dZ * dT);
            if (Math.abs(dFx2) > d && Math.abs(dFz2) > d) {
                if (dFx * dFx2 > 0.0 && dFz * dFz2 > 0.0) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Collision for x-z ray / bounds. Use this to check if a box is really
     * outside.
     *
     * @param fx
     *            the fx
     * @param fz
     *            the fz
     * @param dX
     *            the d x
     * @param dZ
     *            the d z
     * @param dT
     *            the d t
     * @param inset
     *            the inset
     * @return False if no collision with the center bounds.
     */
    public static final boolean collidesCenter(final double fx, final double fz, 
                                               final double dX, final double dZ, 
                                               final double dT, final double inset) {
        final double low = inset;
        final double high = 1.0 - inset;
        final double xEnd = fx + dX * dT;
        if (xEnd < low && fx < low) {
            return false;
        }
        else if (xEnd >= high && fx >= high) {
            return false;
        }
        final double zEnd = fz + dZ * dT;
        if (zEnd < low && fz < low) {
            return false;
        }
        else if (zEnd >= high && fz >= high) {
            return false;
        }
        return true;
    }

    /**
     * Collision for x-z ray / bounds. Use this to check if a box is really
     * inside.
     *
     * @param fx
     *            the fx
     * @param fz
     *            the fz
     * @param dX
     *            the d x
     * @param dZ
     *            the d z
     * @param dT
     *            the d t
     * @param inset
     *            the inset
     * @return True if the box is really inside of the center bounds.
     */
    public static final boolean isInsideCenter(final double fx, final double fz, 
                                               final double dX, final double dZ, 
                                               final double dT, final double inset) {
        final double low = inset;
        final double high = 1.0 - inset;
        final double xEnd = fx + dX * dT;
        if (xEnd < low || fx < low) {
            return false;
        }
        else if (xEnd >= high || fx >= high) {
            return false;
        }
        final double zEnd = fz + dZ * dT;
        if (zEnd < low || fz < low) {
            return false;
        }
        else if (zEnd >= high || fz >= high) {
            return false;
        }
        return true;
    }

    /**
     * Reference block height for on-ground judgment: player must be at this or
     * greater height to stand on this block.<br>
     * <br>
     * NOTE: Check naming convention, might change to something with max ...
     * volatile! <br>
     * This might return 0 or somewhat arbitrary values for some blocks that
     * don't have full bounds (!), might return 0 for blocks with the
     * BlockFlags.F_GROUND_HEIGHT flag, unless they are treated individually here.
     *
     * @param access
     *            the access
     * @param x
     *            the x
     * @param y
     *            the y
     * @param z
     *            the z
     * @param node
     *            The node at the given block coordinates.
     * @param flags
     *            Flags for this block.
     * @return the ground min height
     */
    public static double getGroundMinHeight(final BlockCache access, final int x, final int y, final int z, 
                                            final IBlockCacheNode node, final long flags) {
        final Material id = node.getType();
        final double[] bounds = node.getBounds(access, x, y, z);
        final int data = node.getData(access, x, y, z);

        if ((flags & BlockFlags.F_HEIGHT_8_INC) != 0) {
            final int val = (data & 0xF) % 8;
            return 0.125 * (double) val;
        }
        // Height 100 is ignored (!).
        else if (((flags & BlockFlags.F_HEIGHT150) != 0)) {
            return 1.5;
        }
        else if ((flags & BlockFlags.F_THICK_FENCE) != 0) {
           return Math.min(1.0, bounds[4]);
        }
        else if (bounds == null) {
            return 0.0;
        }
        else if ((flags & BlockFlags.F_GROUND_HEIGHT) != 0) {
            // Subsequent min height flags via lookup.
            final long flag = flags & MIN_HEIGHT_MASK;
            if (flag != 0) {
                final Double fixed = MIN_HEIGHT_LOOKUP.get(flag);
                if (fixed != null) {
                    return fixed.doubleValue();
                }
            }
            // Default height is used.
            if (id == BridgeMaterial.FARMLAND) {
                return bounds[4];
            }
            // Assume open gates/trapdoors/things to only allow standing on to, if at all.
            if ((flags & BlockFlags.F_PASSABLE_X4) != 0 && (data & 0x04) != 0) {
                return bounds[4];
            }
            // All blocks that are not treated individually are ground all through.
            return 0;
        }
        else {
            // Nothing found.
            // NOTE: Consider using Math.min(1.0, bounds[4]) for compatibility rather?
            double minHeight = bounds[4];
            for (int i = 2; i <= (int)bounds.length / 6; i++) {
                minHeight = Math.min(minHeight, bounds[i*6-2]);
            }
            return minHeight;
        }
    }

    /**
     * Convenience method for debugging purposes.
     *
     * @param loc
     *            the loc
     * @return true, if is passable
     */
    public static final boolean isPassable(final PlayerLocation loc) {
        return isPassable(loc.getBlockCache(), loc.getX(), loc.getY(), loc.getZ(), loc.getOrCreateBlockCacheNode(), null);
    }

    /**
     * Convenience method.
     *
     * @param loc
     *            the loc
     * @return true, if is passable
     */
    public static final boolean isPassable(final Location loc) {
        return isPassable(loc.getWorld(), loc.getX(), loc.getY(), loc.getZ());
    }

    /**
     * Convenience method.
     *
     * @param world
     *            the world
     * @param x
     *            the x
     * @param y
     *            the y
     * @param z
     *            the z
     * @return true, if is passable
     */
    public static final boolean isPassable(final World world, final double x, final double y, final double z) {
        final BlockCache blockCache = wrapBlockCache.getBlockCache();
        blockCache.setAccess(world);
        boolean res = isPassable(blockCache, x, y, z, blockCache.getOrCreateBlockCacheNode(x, y, z, false), null);
        blockCache.cleanup();
        return res;
    }

    /**
     * Normal ray tracing.
     *
     * @param from
     *            the from
     * @param to
     *            the to
     * @return true, if is passable
     */
    public static final boolean isPassable(final Location from, final Location to) {
        return isPassable(rtRay, from, to);
    }

    /**
     * Axis-wise checking, like the client does.
     *
     * @param from
     *            the from
     * @param to
     *            the to
     * @return true, if is passable axis wise
     */
    public static final boolean isPassableAxisWise(final Location from, final Location to) {
        return isPassable(rtAxis, from, to);
    }

    /**
     * Checks if is passable.
     *
     * @param rt
     *            the rt
     * @param from
     *            the from
     * @param to
     *            the to
     * @return true, if is passable
     */
    private static boolean isPassable(final ICollidePassable rt, final Location from, final Location to) {
        final BlockCache blockCache = wrapBlockCache.getBlockCache();
        blockCache.setAccess(from.getWorld());
        rt.setMaxSteps(60); // NOTE: Configurable ?
        rt.setBlockCache(blockCache);
        rt.set(from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ());
        rt.loop();
        final boolean collides = rt.collides();
        blockCache.cleanup();
        rt.cleanup();
        return !collides;
    }

    /**
     * Convenience method to allow using an already fetched or prepared
     * IBlockAccess.
     *
     * @param access
     *            the access
     * @param loc
     *            the loc
     * @return true, if is passable
     */
    public static final boolean isPassable(final BlockCache  access, final Location loc) {
        return isPassable(access, loc.getX(), loc.getY(), loc.getZ(), access.getOrCreateBlockCacheNode(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), false), null);
    }

    /**
     * API access to read extra properties from files.
     *
     * @param config
     *            the config
     * @param pathPrefix
     *            the path prefix
     */
    public static void applyConfig(final RawConfigFile config, final String pathPrefix) {
        loadBreakingTimeOverrides(config, pathPrefix);
        loadInstantBreakList(config, pathPrefix);
        loadOverrideFlags(config, pathPrefix);
        minWorldY = config.getInt(pathPrefix + ConfPaths.SUB_BLOCKCACHE_WORLD_MINY);
    }

    private static void loadBreakingTimeOverrides(final RawConfigFile config, final String pathPrefix) {
        ConfigurationSection section = config.getConfigurationSection(pathPrefix + ConfPaths.SUB_BREAKINGTIME);
        if (section == null) {
            return;
        }
        for (final String input : section.getKeys(false)) {
            try {
                BlockProperties.setBreakingTimeOverride(new BlockBreakKey().fromString(input.trim()),
                        section.getLong(input));
            }
            catch (Exception e) {
                StaticLog.logWarning("Bad breaking time override (" + pathPrefix + ConfPaths.SUB_BREAKINGTIME + "): " + input);
                StaticLog.logWarning(e);
            }
        }
    }

    private static void loadInstantBreakList(final RawConfigFile config, final String pathPrefix) {
        for (final String input : config.getStringList(pathPrefix + ConfPaths.SUB_ALLOWINSTANTBREAK)) {
            final Material id = RawConfigFile.parseMaterial(input);
            if (id == null) {
                StaticLog.logWarning("Bad block id (" + pathPrefix + ConfPaths.SUB_ALLOWINSTANTBREAK + "): " + input);
            } else {
                setBlockProps(id, instantType);
            }
        }
    }

    private static void loadOverrideFlags(final RawConfigFile config, final String pathPrefix) {
        ConfigurationSection section = config.getConfigurationSection(pathPrefix + ConfPaths.SUB_OVERRIDEFLAGS);
        if (section == null) {
            return;
        }
        final Map<String, Object> entries = section.getValues(false);
        boolean hasErrors = false;
        for (final Entry<String, Object> entry : entries.entrySet()) {
            final String key = entry.getKey();
            final Material id = RawConfigFile.parseMaterial(key);
            if (id == null) {
                StaticLog.logWarning("Bad block id (" + pathPrefix + ConfPaths.SUB_OVERRIDEFLAGS + "): " + key);
                continue;
            }
            final Object obj = entry.getValue();
            if (!(obj instanceof String)) {
                StaticLog.logWarning("Bad flags at " + pathPrefix + ConfPaths.SUB_OVERRIDEFLAGS + " for key: " + key);
                hasErrors = true;
                continue;
            }
            final Collection<String> split = StringUtil.split((String) obj, ' ', ',', '/', '|', '+', ';', '\t');
            long flags = 0;
            boolean error = false;
            for (String input : split) {
                input = input.trim();
                if (input.isEmpty()) {
                    continue;
                } else if (input.equalsIgnoreCase("default")) {
                    flags |= BlockFlags.getBlockFlags(id);
                    continue;
                }
                try {
                    flags |= BlockFlags.parseFlag(input);
                }
                catch(InputMismatchException e) {
                    StaticLog.logWarning("Bad flag at " + pathPrefix + ConfPaths.SUB_OVERRIDEFLAGS + " for key " + key + " (skip setting flags for this block): " + input);
                    error = true;
                    hasErrors = true;
                    break;
                }
            }
            if (error) {
                continue;
            }
            BlockFlags.setBlockFlags(id, flags);
        }
        if (hasErrors) {
            StaticLog.logInfo("Overriding block-flags was not entirely successful, all available flags: \n" + StringUtil.join(BlockFlags.flagNameMap.values(), "|"));
        }
    }

    /**
     * Test if the bounding box overlaps with a block of given flags (does not
     * check the blocks bounding box).
     *
     * @param access
     *            the access
     * @param minX
     *            the min x
     * @param minY
     *            the min y
     * @param minZ
     *            the min z
     * @param maxX
     *            the max x
     * @param maxY
     *            the max y
     * @param maxZ
     *            the max z
     * @param flags
     *            Block flags (@see
     *            fr.neatmonster.nocheatplus.utilities.BlockProperties).
     * @return If any block has the flags.
     */
    public static final boolean hasAnyFlags(final BlockCache access, final double minX, 
                                            final double minY, final double minZ, final double maxX, 
                                            final double maxY, final double maxZ, final long flags) {
        return hasAnyFlags(access, Location.locToBlock(minX), Location.locToBlock(minY), Location.locToBlock(minZ), Location.locToBlock(maxX), Location.locToBlock(maxY), Location.locToBlock(maxZ), flags);
    }


    /**
     * Test if the bounding box overlaps with a block of given flags (does not
     * check the blocks bounding box).
     *
     * @param access
     *            the access
     * @param minX
     *            the min x
     * @param minY
     *            the min y
     * @param minZ
     *            the min z
     * @param maxX
     *            the max x
     * @param maxY
     *            the max y
     * @param maxZ
     *            the max z
     * @param flags
     *            Block flags (@see
     *            fr.neatmonster.nocheatplus.utilities.BlockFlags).
     * @return If any block has the flags.
     */
    public static final boolean hasAnyFlags(final BlockCache access, final int minX, final int minY, final int minZ, 
                                            final int maxX, final int maxY, final int maxZ, 
                                            final long flags) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    if ((BlockFlags.getBlockFlags(access.getType(x, y, z)) & flags) != 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Test if the box collide with any block that matches the flags somehow.
     * Convenience method.
     *
     * @param access
     *            the access
     * @param bounds
     *            'Classic' bounds as returned for blocks (minX, minY, minZ,
     *            maxX, maxY, maxZ).
     * @param flags
     *            The flags to match.
     * @return true, if successful
     */
    public static final boolean collides(final BlockCache access, double[] bounds,  final long flags) {
        return collides(access, bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5], flags);
    }

    /**
     * Test if the box collide with any block that matches the flags somehow.
     *
     * @param access
     *            the access
     * @param minX
     *            the min x
     * @param minY
     *            the min y
     * @param minZ
     *            the min z
     * @param maxX
     *            the max x
     * @param maxY
     *            the max y
     * @param maxZ
     *            the max z
     * @param flags
     *            The flags to match.
     * @return true, if successful
     */
    public static final boolean collides(final BlockCache access, final double minX, final double minY, final double minZ, 
                                         final double maxX, final double maxY, final double maxZ, final long flags) {
        final int iMinX = Location.locToBlock(minX);
        final int iMaxX = Location.locToBlock(maxX);
        // At least find fences etc. if searched for.
        // NOTE: BlockFlags.F_HEIGHT150 could also be ground etc., more consequent might be to always use or flag it.
        final int iMinY = Location.locToBlock(minY - ((flags & BlockFlags.F_HEIGHT150) != 0 ? 0.5625 : 0));
        final int iMaxY = Math.min(Location.locToBlock(maxY), access.getMaxBlockY());
        final int iMinZ = Location.locToBlock(minZ);
        final int iMaxZ = Location.locToBlock(maxZ);
        for (int x = iMinX; x <= iMaxX; x++) {
            for (int z = iMinZ; z <= iMaxZ; z++) {
                IBlockCacheNode nodeAbove = null;
                for (int y = iMaxY; y >= iMinY; y--) {
                    final IBlockCacheNode node = access.getOrCreateBlockCacheNode(x, y, z, false);
                    final Material id = node.getType();
                    final long cFlags = BlockFlags.getBlockFlags(id);
                    if ((cFlags & flags) != 0) {
                        // Might collide.
                        if (node.hasNonNullBounds().decideOptimistically() 
                            && collidesBlock(access, minX, minY, minZ, maxX, maxY, maxZ, x, y, z, node, nodeAbove, cFlags)) {
                            return true;
                        }
                    }
                    nodeAbove = node;
                }
            }
        }
        return false;
    }

    /**
     * Convenience method for Material instead of block id.
     *
     * @param access
     *            the access
     * @param minX
     *            the min x
     * @param minY
     *            the min y
     * @param minZ
     *            the min z
     * @param maxX
     *            the max x
     * @param maxY
     *            the max y
     * @param maxZ
     *            the max z
     * @param mat
     *            the mat
     * @return true, if successful
     */
    public static final boolean collidesId(final BlockCache access, 
                                           final double minX, final double minY, final double minZ, 
                                           final double maxX, final double maxY, final double maxZ, 
                                           final Material mat) {
        final int iMinX = Location.locToBlock(minX);
        final int iMaxX = Location.locToBlock(maxX);
        final int iMinY = Location.locToBlock(minY - ((BlockFlags.getBlockFlags(mat) & BlockFlags.F_HEIGHT150) != 0 ? 0.5625 : 0));
        final int iMaxY = Location.locToBlock(maxY);
        final int iMinZ = Location.locToBlock(minZ);
        final int iMaxZ = Location.locToBlock(maxZ);
        for (int x = iMinX; x <= iMaxX; x++) {
            for (int z = iMinZ; z <= iMaxZ; z++) {
                for (int y = iMinY; y <= iMaxY; y++) {
                    if (mat == access.getType(x, y, z)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Check if the given bounding box collides with water logged blocks.
     *
     * @param world
     *            the world
     * @param access
     *            the access
     * @param minX
     *            the min x
     * @param minY
     *            the min y
     * @param minZ
     *            the min z
     * @param maxX
     *            the max x
     * @param maxY
     *            the max y
     * @param maxZ
     *            the max z
     * @return true, if successful
     */
    public static final boolean isWaterlogged(final World world, final BlockCache access, 
                                              final double minX, final double minY, final double minZ, 
                                              final double maxX, final double maxY, final double maxZ) {
        if (!Bridge1_13.hasIsSwimming()) return false;
        final int iMinX = Location.locToBlock(minX);
        final int iMaxX = Location.locToBlock(maxX);
        final int iMinY = Location.locToBlock(minY);
        final int iMaxY = Math.min(Location.locToBlock(maxY), access.getMaxBlockY());
        final int iMinZ = Location.locToBlock(minZ);
        final int iMaxZ = Location.locToBlock(maxZ);

        for (int x = iMinX; x <= iMaxX; x++) {
            for (int z = iMinZ; z <= iMaxZ; z++) {
                for (int y = iMaxY; y >= iMinY; y--) {
                    BlockData bd = world.getBlockAt(x,y,z).getBlockData();
                    if (bd instanceof Waterlogged && ((Waterlogged)bd).isWaterlogged()) {
                         // Clearly outside of bounds. (liquid)
                        if (minX > 1.0 + x || maxX < 0.0 + x
                         || minY > LIQUID_HEIGHT_LOWERED + y || maxY < 0.0 + y
                         || minZ > 1.0 + z || maxZ < 0.0 + z) {
                            continue;
                        }
                        // Hitting the max-edges (if allowed).
                        if (Math.abs(minX - (1.0 + x)) < EPSILON || Math.abs(minY - (LIQUID_HEIGHT_LOWERED + y)) < EPSILON || Math.abs(minZ - (1.0 + z)) < EPSILON) {
                            continue;
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }


    /**
     * Check if the given bounding box collides with a bubble stream that can drag the player.
     *
     * @param world
     *            the world
     * @param access
     *            the access
     * @param minX
     *            the min x
     * @param minY
     *            the min y
     * @param minZ
     *            the min z
     * @param maxX
     *            the max x
     * @param maxY
     *            the max y
     * @param maxZ
     *            the max z
     * @return true, if successful
     */
    public static final boolean isDraggableBubbleStream(final World world, final BlockCache access, 
                                                        final double minX, final double minY, final double minZ, 
                                                        final double maxX, final double maxY, final double maxZ) {
        if (!Bridge1_13.hasIsSwimming()) return false;
        final int iMinX = Location.locToBlock(minX);
        final int iMaxX = Location.locToBlock(maxX);
        final int iMinY = Location.locToBlock(minY);
        final int iMaxY = Math.min(Location.locToBlock(maxY), access.getMaxBlockY());
        final int iMinZ = Location.locToBlock(minZ);
        final int iMaxZ = Location.locToBlock(maxZ);

        for (int x = iMinX; x <= iMaxX; x++) {
            for (int z = iMinZ; z <= iMaxZ; z++) {
                 for (int y = iMaxY; y >= iMinY; y--) {
                    BlockData data = world.getBlockAt(x,y,z).getBlockData();
                    if (data instanceof BubbleColumn) {
                        if (((BubbleColumn)data).isDrag()) return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Check if the given bounding box collides with a block of the given id,
     * taking into account the actual bounds of the block.
     *
     * @param access
     *            the access
     * @param minX
     *            the min x
     * @param minY
     *            the min y
     * @param minZ
     *            the min z
     * @param maxX
     *            the max x
     * @param maxY
     *            the max y
     * @param maxZ
     *            the max z
     * @param id
     *            the id
     * @return true, if successful
     */
    public static final boolean collidesBlock(final BlockCache access, 
                                              final double minX, final double minY, final double minZ, 
                                              final double maxX, final double maxY, final double maxZ, 
                                              final Material id) {
        final int iMinX = Location.locToBlock(minX);
        final int iMaxX = Location.locToBlock(maxX);
        final int iMinY = Location.locToBlock(minY - ((BlockFlags.getBlockFlags(id) & BlockFlags.F_HEIGHT150) != 0 ? 0.5625 : 0));
        final int iMaxY = Math.min(Location.locToBlock(maxY), access.getMaxBlockY());
        final int iMinZ = Location.locToBlock(minZ);
        final int iMaxZ = Location.locToBlock(maxZ);
        final long flags = BlockFlags.getBlockFlags(id);
        for (int x = iMinX; x <= iMaxX; x++) {
            for (int z = iMinZ; z <= iMaxZ; z++) {
                IBlockCacheNode nodeAbove = null;
                for (int y = iMaxY; y >= iMinY; y--) {
                    final IBlockCacheNode node = access.getOrCreateBlockCacheNode(x, y, z, false);
                    if (id == node.getType()) {
                        if (node.hasNonNullBounds().decideOptimistically()) {
                            if (collidesBlock(access, minX, minY, minZ, maxX, maxY, maxZ, x, y, z, node, nodeAbove, flags)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    //    /**
    //     * Check if the bounds collide with the block that has the given type id at
    //     * the given position. <br>
    //     * Delegates: This method signature is not used internally anymore.
    //     *
    //     * @param access
    //     *            the access
    //     * @param minX
    //     *            the min x
    //     * @param minY
    //     *            the min y
    //     * @param minZ
    //     *            the min z
    //     * @param maxX
    //     *            the max x
    //     * @param maxY
    //     *            the max y
    //     * @param maxZ
    //     *            the max z
    //     * @param x
    //     *            the x
    //     * @param y
    //     *            the y
    //     * @param z
    //     *            the z
    //     * @param id
    //     *            the id
    //     * @return true, if successful
    //     */
    //    public static final boolean collidesBlock(final BlockCache access, final double minX, double minY, final double minZ, final double maxX, final double maxY, final double maxZ, final int x, final int y, final int z, final int id) {
    //        // NOTE: use internal block data unless delegation wanted?
    //        final double[] bounds = access.getBounds(x,y,z);
    //        if (bounds == null) {
    //            return false; // NOTE: policy ?
    //        }
    //        final long flags = blockFlags[id];
    //        return collidesBlock(access, minX, minY, minZ, maxX, maxY, maxZ, x, y, z, id, bounds, flags);
    //    }

    /**
     * Check if the bounds collide with the block for the given type id at the
     * given position. This does not check workarounds for ground_height nor
     * passable.
     *
     * @param access
     *            the access <- we all love the access!
     * @param minX
     *            the min x
     * @param minY
     *            the min y
     * @param minZ
     *            the min z
     * @param maxX
     *            the max x
     * @param maxY
     *            the max y
     * @param maxZ
     *            the max z
     * @param x
     *            the x
     * @param y
     *            the y
     * @param z
     *            the z
     * @param node
     *            The node at the given block coordinates (not null, bounds need
     *            not be fetched).
     * @param nodeAbove
     *            The node above the given block coordinates (may be null). Pass
     *            for efficiency, if it should already have been fetched for
     *            some reason.
     * @param flags
     *            Block flags for the block at x, y, z. Mix in BlockFlags.F_COLLIDE_EDGES
     *            to disallow the "high edges" of blocks.
     * @return true, if successful
     */
    public static boolean collidesBlock(
            final BlockCache access,
            final double minX, final double minY, final double minZ,
            final double maxX, final double maxY, final double maxZ,
            final int x, final int y, final int z,
            final IBlockCacheNode node, final IBlockCacheNode nodeAbove,
            final long flags) {

        // 1. Primary bounding box (most blocks only have one box).
        final double[] rawBounds = node.getBounds(access, x, y, z);
        if (rawBounds == null) {
            return false;
        }

        final BoundingBox primary = createBoundingBoxFromFlags(rawBounds, flags, node, nodeAbove, access, x, y, z);

        final boolean allowEdge = (flags & BlockFlags.F_COLLIDE_EDGES) == 0;
        if (!primary.intersects(minX, minY, minZ, maxX, maxY, maxZ, x, y, z, allowEdge)) {
            // 2. Early-out: only fall back to sub-bounds if the main box misses.
            return checkSubBoxIntersections(rawBounds, minX, minY, minZ, maxX, maxY, maxZ, x, y, z, allowEdge);
        }
        return true;
    }

    /** Immutable local bounding box helper */
    private static final class BoundingBox {
        final double minX, minY, minZ, maxX, maxY, maxZ;
        BoundingBox(double minX, double minY, double minZ,
                    double maxX, double maxY, double maxZ) {
            validateBounds(minX, minY, minZ, maxX, maxY, maxZ);
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        private static void validateBounds(double minX, double minY, double minZ,
                                           double maxX, double maxY, double maxZ) {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("Invalid bounds: [" + minX + ", "
                        + minY + ", " + minZ + "] > [" + maxX + ", " + maxY
                        + ", " + maxZ + "]");
            }
        }

        /**
         * Check intersection against another axis aligned bounding box.
         *
         * @param minX world space minimum x of the other box
         * @param minY world space minimum y of the other box
         * @param minZ world space minimum z of the other box
         * @param maxX world space maximum x of the other box
         * @param maxY world space maximum y of the other box
         * @param maxZ world space maximum z of the other box
         * @param x world offset of this box on the x-axis
         * @param y world offset of this box on the y-axis
         * @param z world offset of this box on the z-axis
         * @param allowEdge whether touching the outer edge should count as
         *                  non-colliding
         * @return {@code true} if the boxes intersect
         */
        public boolean intersects(double minX, double minY, double minZ,
                                  double maxX, double maxY, double maxZ,
                                  int x, int y, int z, boolean allowEdge) {
            if (minX > this.maxX + x || maxX < this.minX + x ||
                minY > this.maxY + y || maxY < this.minY + y ||
                minZ > this.maxZ + z || maxZ < this.minZ + z) {
                return false;
            }
            if (doubleEquals(minX, this.maxX + x) && (this.maxX < 1.0 || allowEdge) ||
                doubleEquals(minY, this.maxY + y) && (this.maxY < 1.0 || allowEdge) ||
                doubleEquals(minZ, this.maxZ + z) && (this.maxZ < 1.0 || allowEdge)) {
                return false;
            }
            return true;
        }
    }

    /**
     * Builds the primary bounding box, including flag-controlled fake-bounds
     * tweaks.
     */
    private static BoundingBox createBoundingBoxFromFlags(
            double[] b, long flags, IBlockCacheNode node, IBlockCacheNode nodeAbove,
            BlockCache access, int x, int y, int z) {

        double bminX, bminY, bminZ, bmaxX, bmaxY, bmaxZ;

        // XZ plane (flags can force full-block extents).
        if ((flags & BlockFlags.F_XZ100) != 0) {
            bminX = bminZ = 0.0;
            bmaxX = bmaxZ = 1.0;
        } else {
            bminX = b[0];
            bminZ = b[2];
            bmaxX = b[3];
            bmaxZ = b[5];
        }

        // Y extents – many liquid/height variants.
        if ((flags & BlockFlags.F_HEIGHT_8_INC) != 0) {
            bminY = 0;
            final int data = (node.getData(access, x, y, z) & 0xF) % 8;
            bmaxY = 0.125 * data;
        } else if ((flags & BlockFlags.F_HEIGHT150) != 0) {
            bminY = 0;
            bmaxY = 1.5;
        } else if ((flags & BlockFlags.F_HEIGHT100) != 0) {
            bminY = 0;
            bmaxY = 1.0;
        } else if ((flags & BlockFlags.F_HEIGHT_8SIM_DEC) != 0) {
            bminY = 0;
            bmaxY = getLiquidHeightFromFlags(flags, node, nodeAbove, access, x, y, z, b);
        } else if ((flags & BlockFlags.F_HEIGHT8_1) != 0) {
            bminY = 0;
            bmaxY = 0.125;
        } else {
            bminY = b[1];
            bmaxY = b[4];
        }

        if (bmaxY > 1.0) {
            bmaxY = 1.0;
        }

        // Fake bounds for thin-glass and similar oddities.
        if ((flags & BlockFlags.F_FAKEBOUNDS) != 0) {
            double dz = bmaxZ - bminZ;
            double dx = bmaxX - bminX;
            bminX = adjustBoundFaceIfFake(bminX, bmaxX, dz, dx, true);
            bmaxX = adjustBoundFaceIfFake(bmaxX, bminX, dz, dx, false);
            bminZ = adjustBoundFaceIfFake(bminZ, bmaxZ, dx, dz, true);
            bmaxZ = adjustBoundFaceIfFake(bmaxZ, bminZ, dx, dz, false);
        }
        return new BoundingBox(bminX, bminY, bminZ, bmaxX, bmaxY, bmaxZ);
    }

    /** Liquid-height helper extracted from original branch-nest */
    private static double getLiquidHeightFromFlags(long flags, IBlockCacheNode node, IBlockCacheNode nodeAbove,
                                              BlockCache access, int x, int y, int z, double[] bounds) {
        // Lava
        if ((flags & BlockFlags.F_LAVA) != 0) {
            if (nodeAbove != null && (BlockFlags.getBlockFlags(nodeAbove.getType()) & BlockFlags.F_LAVA) != 0) {
                return 1.0;
            }
            int data = node.getData(access, x, y, z);
            return (data >= 8) ? LIQUID_HEIGHT_LOWERED : 1.0 - data / 9.0;
        }
        // Water
        if ((flags & BlockFlags.F_WATER) != 0) {
            if (nodeAbove != null && (BlockFlags.getBlockFlags(nodeAbove.getType()) & BlockFlags.F_WATER) != 0) {
                return 1.0;
            }
            int data = node.getData(access, x, y, z);
            if ((data & 8) == 8) {
                return Math.max(LIQUID_HEIGHT_LOWERED, bounds[4]);
            }
            return 1.0 - data / 9.0;
        }
        // Default lowered-liquid height.
        return LIQUID_HEIGHT_LOWERED;
    }

    /** Simpler fake-bounds face adjustment */
    private static double adjustBoundFaceIfFake(double faceA, double faceB, double da, double db, boolean isMin) {
        if (da == 0.125 && db != 1.0) {
            return isMin ? 0.0 : 0.5;
        } else if (db == 0.125 && da != 1.0) {
            return isMin ? 0.0 : 0.5;
        } else if (db == da && db != 1.0) {
            if (!isMin && faceA == 0.5625) return 0.5;
            if (isMin && faceA == 0.4375) return 0.5;
        }
        return faceA; // unchanged
    }


    /**
     * Iterate over additional 6-tuple sub-boxes when present.
     * Some blocks expose multiple bounding boxes packed in a flat array.
     * Each box occupies six consecutive entries.
     */
    private static boolean checkSubBoxIntersections(double[] rawBounds,
                                                  double minX, double minY, double minZ,
                                                  double maxX, double maxY, double maxZ,
                                                  int x, int y, int z, boolean allowEdge) {
        if (rawBounds.length <= 6 || rawBounds.length % 6 != 0) {
            return false;
        }
        // Start at index 1 since index 0 represents the primary box.
        for (int i = 1; i < rawBounds.length / 6; i++) {
            double capMaxY = rawBounds[i * 6 + 4];
            if (capMaxY > 1.0) {
                capMaxY = 1.0;
            }
            BoundingBox sub = new BoundingBox(
                    rawBounds[i*6],     rawBounds[i*6+1], rawBounds[i*6+2],
                    rawBounds[i*6+3],   capMaxY, rawBounds[i*6+5]);
            if (sub.intersects(minX, minY, minZ, maxX, maxY, maxZ, x, y, z, allowEdge)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Like isOnGround, just with minimum and maximum coordinates in arbitrary
     * order.
     *
     * @param access
     *            the access
     * @param x1
     *            the x1
     * @param y1
     *            the y1
     * @param z1
     *            the z1
     * @param x2
     *            the x2
     * @param y2
     *            the y2
     * @param z2
     *            the z2
     * @param xzMargin
     *            Subtracted from minima and added to maxima.
     * @param yBelow
     *            Subtracted from the minimum of y.
     * @param yAbove
     *            Added to the maximum of y.
     * @return true, if is on ground shuffled
     */
    public static final boolean isOnGroundShuffled(final BlockCache access, 
                                                   final double x1, final double y1, final double z1, 
                                                   final double x2, final double y2, final double z2, 
                                                   final double xzMargin, final double yBelow, final double yAbove) {
        return isOnGroundShuffled(access, x1, y1, z1, x2, y2, z2, xzMargin, yBelow, yAbove, 0L);
    }

    /**
     * Similar to collides(... , BlockFlags.F_GROUND), but also checks the block above
     * (against spider).<br>
     * NOTE: This does not return true if stuck, to check for that use
     * collidesBlock for the players location.
     *
     * @param access
     *            the access
     * @param minX
     *            the min x
     * @param minY
     *            the min y
     * @param minZ
     *            the min z
     * @param maxX
     *            the max x
     * @param maxY
     *            the max y
     * @param maxZ
     *            the max z
     * @return true, if is on ground
     */
    public static final boolean isOnGround(final BlockCache access, final double minX, double minY, final double minZ, 
                                           final double maxX, final double maxY, final double maxZ) {
        return isOnGround(access, minX, minY, minZ, maxX, maxY, maxZ, 0L);
    }

    /**
     * Like isOnGround, just with minimum and maximum coordinates in arbitrary
     * order.
     *
     * @param access
     *            the access
     * @param x1
     *            the x1
     * @param y1
     *            the y1
     * @param z1
     *            the z1
     * @param x2
     *            the x2
     * @param y2
     *            the y2
     * @param z2
     *            the z2
     * @param xzMargin
     *            Subtracted from minima and added to maxima.
     * @param yBelow
     *            Subtracted from the minimum of y.
     * @param yAbove
     *            Added to the maximum of y.
     * @param ignoreFlags
     *            the ignore flags
     * @return true, if is on ground shuffled
     */
    public static final boolean isOnGroundShuffled(final BlockCache access, 
                                                   final double x1, double y1, final double z1, 
                                                   final double x2, final double y2, final double z2, 
                                                   final double xzMargin, final double yBelow, final double yAbove, 
                                                   final long ignoreFlags) {
        return isOnGround(access, Math.min(x1, x2) - xzMargin, Math.min(y1, y2) - yBelow, Math.min(z1, z2) - xzMargin, Math.max(x1, x2) + xzMargin, Math.max(y1, y2) + yAbove, Math.max(z1, z2) + xzMargin, ignoreFlags);
    }

    /**
     * Similar to collides(... , BlockFlags.F_GROUND), but also checks the block above
     * (against spider).<br>
     * NOTE: This does not return true if stuck, to check for that use
     * collidesBlock for the players location.
     *
     * @param access
     *            the access
     * @param minX
     *            Bounding box coordinates...
     * @param minY
     *            the min y
     * @param minZ
     *            the min z
     * @param maxX
     *            the max x
     * @param maxY
     *            Meant to be the foot-level.
     * @param maxZ
     *            the max z
     * @param ignoreFlags
     *            Blocks with these flags are not counted as ground.
     * @return true, if is on ground
     */
    public static final boolean isOnGround(final BlockCache access,
                                           final double minX, final double minY, final double minZ,
                                           final double maxX, final double maxY, final double maxZ,
                                           final long ignoreFlags) {
        final int maxBlockY = access.getMaxBlockY();
        final int iMinX = Location.locToBlock(minX);
        final int iMaxX = Location.locToBlock(maxX);
        final int iMinY = Location.locToBlock(minY - 0.5626);
        if (iMinY > maxBlockY) {
            return false;
        }
        final int iMaxY = Math.min(Location.locToBlock(maxY), maxBlockY);
        final int iMinZ = Location.locToBlock(minZ);
        final int iMaxZ = Location.locToBlock(maxZ);

        for (int x = iMinX; x <= iMaxX; x++) {
            for (int z = iMinZ; z <= iMaxZ; z++) {
                if (checkColumnGround(access, minX, minY, minZ, maxX, maxY, maxZ,
                                     ignoreFlags, x, z, iMinY, iMaxY)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check a single vertical column of blocks for ground.
     *
     * @param access
     * @param minX
     * @param minY
     * @param minZ
     * @param maxX
     * @param maxY
     *            Meant to be the foot-level.
     * @param maxZ
     * @param ignoreFlags
     * @param x
     *            the block x coordinate
     * @param z
     *            the block z coordinate
     * @param iMinY
     *            lowest block y to check
     * @param iMaxY
     *            highest block y to check
     * @return {@code true} if ground was found in this column
     */
    private static boolean checkColumnGround(final BlockCache access,
                                             final double minX, final double minY, final double minZ,
                                             final double maxX, final double maxY, final double maxZ,
                                             final long ignoreFlags,
                                             final int x, final int z,
                                             final int iMinY, final int iMaxY) {
        IBlockCacheNode prevNodeAbove = null;
        for (int y = iMaxY; y >= iMinY; y--) {
            final IBlockCacheNode node = access.getOrCreateBlockCacheNode(x, y, z, false);
            final AlmostBoolean result = evaluateGroundNode(access, minX, minY, minZ, maxX, maxY, maxZ,
                                                            ignoreFlags, x, y, z, node, prevNodeAbove);
            if (result == AlmostBoolean.YES) {
                return true;
            }
            else if (result == AlmostBoolean.MAYBE) {
                prevNodeAbove = node;
                continue;
            }
            else {
                break;
            }
        }
        return false;
    }

    /**
     * Check for ground at a certain block position, assuming checking order is
     * top down within an x-z loop.
     * 
     * @param access
     * @param minX
     * @param minY
     * @param minZ
     * @param maxX
     * @param maxY
     * @param maxZ
     * @param ignoreFlags
     * @param x
     * @param y
     * @param z
     * @param iMaxY
     *            Math.min(iteration max block y, world max block y)
     * @param node
     * @param nodeAbove
     *            May be null.
     * @return YES if certainly on ground, MAYBE if not on ground with the
     *         possibility of being on ground underneath, NO if not on ground
     *         without the possibility to be on ground with checking lower
     *         y-coordinates.
     */
    public static final AlmostBoolean evaluateGroundNode(final BlockCache access,
                                                 final double minX, final double minY, final double minZ,
                                                 final double maxX, final double maxY, final double maxZ,
                                                 final long ignoreFlags, final int x, final int y, final int z,
                                                 final IBlockCacheNode node, IBlockCacheNode nodeAbove) {
        final Material id = node.getType();
        final long flags = BlockFlags.getBlockFlags(id);

        if (isGroundIgnored(flags, ignoreFlags)) {
            return AlmostBoolean.MAYBE;
        }

        final double[] bounds = node.getBounds(access, x, y, z);
        if (isBlockWithoutBounds(bounds)) {
            return AlmostBoolean.YES;
        }

        if (!isCollidingWithNode(access, minX, minY, minZ, maxX, maxY, maxZ,
                                 x, y, z, node, nodeAbove, flags)) {
            return AlmostBoolean.MAYBE;
        }

        final double groundMinHeight = getGroundMinHeight(access, x, y, z, node, flags);

        if (isPassableButNotGroundHighEnough(access, x, y, z, minX, minY, minZ, maxX, maxY, maxZ,
                                             node, flags, groundMinHeight)) {
            return AlmostBoolean.MAYBE;
        }

        if (isFootInsideBlock(groundMinHeight, maxY - y)) {
            return isFullBounds(bounds) ? AlmostBoolean.NO : AlmostBoolean.MAYBE;
        }

        if (isTopOrSufficient(maxY - y, y, access)) {
            return AlmostBoolean.YES;
        }

        return evaluateNodeAbove(access, minX, minY, minZ, maxX, maxY, maxZ, ignoreFlags,
                                 x, y, z, flags, id, bounds, nodeAbove);
    }

    private static boolean isGroundIgnored(final long flags, final long ignoreFlags) {
        return (flags & BlockFlags.F_GROUND) == 0 || (flags & ignoreFlags) != 0;
    }

    private static boolean isBlockWithoutBounds(final double[] bounds) {
        return bounds == null;
    }

    private static boolean isCollidingWithNode(final BlockCache access,
                                               final double minX, final double minY, final double minZ,
                                               final double maxX, final double maxY, final double maxZ,
                                               final int x, final int y, final int z,
                                               final IBlockCacheNode node, final IBlockCacheNode nodeAbove,
                                               final long flags) {
        return collidesBlock(access, minX, minY, minZ, maxX, maxY, maxZ,
                             x, y, z, node, nodeAbove, flags);
    }

    private static boolean isPassableButNotGroundHighEnough(
            final BlockCache access, final int x, final int y, final int z,
            final double minX, final double minY, final double minZ,
            final double maxX, final double maxY, final double maxZ,
            final IBlockCacheNode node, final long flags, final double groundMinHeight) {
        return isPassableWorkaround(access, x, y, z, minX - x, minY - y, minZ - z, node,
                                    maxX - minX, maxY - minY, maxZ - minZ,
                                    minX, minY, minZ, maxX, maxY, maxZ, 1.0)
                && ((flags & BlockFlags.F_GROUND_HEIGHT) == 0
                        || groundMinHeight > maxY - y);
    }

    private static boolean isFootInsideBlock(final double groundMinHeight, final double maxYMinusY) {
        return groundMinHeight > maxYMinusY;
    }

    private static boolean isTopOrSufficient(final double maxYMinusY, final int y, final BlockCache access) {
        return maxYMinusY < 1.0 || y >= access.getMaxBlockY();
    }

    private static AlmostBoolean evaluateNodeAbove(
            final BlockCache access,
            final double minX, final double minY, final double minZ,
            final double maxX, final double maxY, final double maxZ,
            final long ignoreFlags, final int x, final int y, final int z,
            final long flags, final Material id, final double[] bounds,
            IBlockCacheNode nodeAbove) {

        if (nodeAbove == null) {
            nodeAbove = access.getOrCreateBlockCacheNode(x, y + 1, z, false);
        }
        final Material aboveId = nodeAbove.getType();
        final long aboveFlags = BlockFlags.getBlockFlags(aboveId);

        if ((aboveFlags & BlockFlags.F_IGN_PASSABLE) != 0
                || (aboveFlags & BlockFlags.F_GROUND) == 0
                || (aboveFlags & BlockFlags.F_LIQUID) != 0
                || (aboveFlags & ignoreFlags) != 0) {
            return AlmostBoolean.YES;
        }

        final boolean variable = ((flags | aboveFlags) & BlockFlags.F_VARIABLE) != 0;
        if (!variable && id == aboveId) {
            return isFullBounds(bounds) ? AlmostBoolean.NO : AlmostBoolean.MAYBE;
        }

        final double[] aboveBounds = nodeAbove.getBounds(access, x, y + 1, z);
        if (aboveBounds == null
                || !collidesBlock(access, minX, minY, minZ, maxX, Math.max(maxY, y + 1.49), maxZ,
                                   x, y + 1, z, nodeAbove, null, aboveFlags)
                || isPassableWorkaround(access, x, y + 1, z, minX - x, minY - (y + 1), minZ - z,
                                       nodeAbove, maxX - minX, maxY - minY, maxZ - minZ,
                                       minX, minY, minZ, maxX, maxY, maxZ, 1.0)) {
            return AlmostBoolean.YES;
        }

        if (isFullBounds(aboveBounds)) {
            return AlmostBoolean.NO;
        }

        if (variable && isSameShape(bounds, aboveBounds)) {
            return AlmostBoolean.MAYBE;
        }

        return variable ? AlmostBoolean.YES : AlmostBoolean.MAYBE;
    }

    /**
     * All dimensions 0 ... 1, no null checks.
     *
     * @param bounds
     *            Block bounds: minX, minY, minZ, maxX, maxY, maxZ
     * @return true, if is full bounds
     */
    public static final boolean isFullBounds(final double[] bounds) {
        for (int i = 0; i < 3; i++) {
            if (bounds[i] != 0.0 || bounds[i + 3] != 1.0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if the bounds are the same. With null checks.
     *
     * @param bounds1
     *            the bounds1
     * @param bounds2
     *            the bounds2
     * @return True, if the shapes have the exact same bounds, same if both are
     *         null. In case of one parameter being null, false is returned,
     *         even if the other is a full block.
     */
    public static final boolean isSameShape(final double[] bounds1, final double[] bounds2) {
        // NOTE: further exclude simple full shape blocks, or confine to itchy block types
        // NOTE: make flags for it.
        if (bounds1 == null || bounds2 == null) {
            return bounds1 == bounds2;
        }
        // Allow as ground for differing shapes.
        for (int i = 0; i <  6; i++) {
            if (bounds1[i] != bounds2[i]) {
                // Simplistic.
                return false;
            }
        }
        return true;
    }

    /**
     * Check if the move given by from and to is leading downstream. Currently
     * only the x-z move is regarded, no envelope/consistency checks are
     * performed here, such as checking if the block is liquid at all, nor if
     * the move makes sense. performed here.
     *
     * @param from
     *            the from
     * @param to
     *            the to
     * @return true, if is down stream
     */
    public static final boolean isDownStream(final PlayerLocation from, final PlayerLocation to) {
        return isDownStream(from.getBlockCache(), from.getBlockX(), from.getBlockY(), from.getBlockZ(), 
                            from.getData(), to.getX() - from.getX(), to.getZ() - from.getZ());
    }

    /**
     * Check if a move determined by xDistance and zDistance is leading down
     * stream.
     *
     * @param access
     *            the access
     * @param x
     *            the x
     * @param y
     *            the y
     * @param z
     *            the z
     * @param data
     *            the data
     * @param dX
     *            the d x
     * @param dZ
     *            the d z
     * @return true, if is down stream
     */
    public static final boolean isDownStream(final BlockCache access, final int x, final int y, final int z, final int data, final double dX, final double dZ) {
        // x > 0 -> south, z > 0 -> west
        if ((data & 0x8) != 0) {
            // Falling water, no horizontal flow.
            return false;
        }
        if (dX != 0) {
            final int stepX = dX > 0 ? 1 : -1;
            if (data < 7 && BlockProperties.isLiquid(access.getType(x + stepX, y, z)) && access.getData(x + stepX, y, z) > data) {
                return true;
            }
            if (data > 0 && BlockProperties.isLiquid(access.getType(x - stepX, y, z)) && access.getData(x - stepX, y, z) < data) {
                // reverse direction
                return true;
            }
        }
        if (dZ != 0) {
            final int stepZ = dZ > 0 ? 1 : -1;
            if (data < 7 && BlockProperties.isLiquid(access.getType(x, y, z + stepZ)) && access.getData(x, y, z + stepZ) > data) {
                return true;
            }
            if (data > 0 && BlockProperties.isLiquid(access.getType(x, y, z - stepZ)) && access.getData(x, y, z - stepZ) < data) {
                // reverse direction
                return true;
            }
        }
        return false;
    }

    /**
     *
     * @param from
     *            the from
     * @param to
     *            the to
     * @return true, if is in waterfall
     */
    public static final boolean isWaterfall(final PlayerLocation from, final PlayerLocation to) {
        return isWaterfall(from.getBlockCache(), from.getBlockX(), from.getBlockY(), from.getBlockZ(), 
                            from.getData(), to.getY() - from.getY());
    }

    /**
     * Check if a player is moving inside a waterfall, determined by the yDistance
     *
     * @param access
     *            the access
     * @param x
     *            the x
     * @param y
     *            the y
     * @param z
     *            the z
     * @param data
     *            the data
     * @param dY
     *            the d y
     * @return true, if is moving in a waterfall
     */
    public static final boolean isWaterfall(final BlockCache access, final int x, final int y, final int z, final int data, final double dY) {
        if ((data & 0x8) == 0) {
            if ((dY > 0)) {
                if (data < 7 && BlockProperties.isLiquid(access.getType(x, y + 1, z)) && access.getData(x, y + 1, z) > data) {
                    return true;
                }
                else if (data > 0 && BlockProperties.isLiquid(access.getType(x, y - 1, z)) && access.getData(x, y - 1, z) < data) {
                    // reverse direction.
                    return true;
                }
            } 
            else if (dY < 0) {
                if (data < 7 && BlockProperties.isLiquid(access.getType(x, y - 1, z)) && access.getData(x, y + 1, z) > data) {
                    return true;
                }
                else if (data > 0  && BlockProperties.isLiquid(access.getType(x, y + 1, z)) && access.getData(x, y + 1, z) < data) {
                    // reverse direction.
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Collect all flags of blocks touched by the bounds, this does not check
     * versus the blocks bounding box.
     *
     * @param access
     *            the access
     * @param minX
     *            the min x
     * @param minY
     *            the min y
     * @param minZ
     *            the min z
     * @param maxX
     *            the max x
     * @param maxY
     *            the max y
     * @param maxZ
     *            the max z
     * @return the long
     */
    public static final long collectFlagsSimple(final BlockCache access, 
                                                final double minX, final double minY, final double minZ, 
                                                final double maxX, final double maxY, final double maxZ) {
        final int iMinX = Location.locToBlock(minX);
        final int iMaxX = Location.locToBlock(maxX);
        final int iMinY = Location.locToBlock(minY);
        final int iMaxY = Location.locToBlock(maxY);
        final int iMinZ = Location.locToBlock(minZ);
        final int iMaxZ = Location.locToBlock(maxZ);
        //      NCPAPIProvider.getNoCheatPlusAPI().getLogManager().debug(Streams.TRACE_FILE, "*** collect flags check size: " + ((iMaxX - iMinX + 1) * (iMaxY - iMinY + 1) * (iMaxZ - iMinZ + 1)));
        long flags = 0;
        for (int x = iMinX; x <= iMaxX; x++) {
            for (int z = iMinZ; z <= iMaxZ; z++) {
                for (int y = iMinY; y <= iMaxY; y++) {
                    flags |= BlockFlags.getBlockFlags(access.getType(x, y, z));
                }
            }
        }
        return flags;
    }

    /**
     * Penalty factor for block break duration if under water.
     *
     * @return the break penalty in water
     */
    public static float getBreakPenaltyInWater() {
        return breakPenaltyInWater;
    }

    /**
     * Penalty factor for block break duration if under water.
     *
     * @param breakPenaltyInWater
     *            the new break penalty in water
     */
    public static void setBreakPenaltyInWater(float breakPenaltyInWater) {
        BlockProperties.breakPenaltyInWater = breakPenaltyInWater;
    }

    /**
     * Penalty factor for block break duration if not on ground.
     *
     * @return the break penalty off ground
     */
    public static float getBreakPenaltyOffGround() {
        return breakPenaltyOffGround;
    }

    /**
     * Penalty factor for block break duration if not on ground.
     *
     * @param breakPenaltyOffGround
     *            the new break penalty off ground
     */
    public static void setBreakPenaltyOffGround(float breakPenaltyOffGround) {
        BlockProperties.breakPenaltyOffGround = breakPenaltyOffGround;
    }

    /**
     * Cleanup. Call init() to re-initialize.
     */
    public static void cleanup() {
        // (Null checks are error cases, to be intercepted elsewhere.)
        if (pLoc != null) {
            pLoc.cleanup();
            pLoc = null;
        }
        if (wrapBlockCache != null) {
            wrapBlockCache.cleanup();
            wrapBlockCache = null;
        }
        // NOTE: might empty mappings...
    }

    /**
     * Checks if is passable ray.
     *
     * @param access
     *            the access
     * @param blockX
     *            Block location.
     * @param blockY
     *            the block y
     * @param blockZ
     *            the block z
     * @param oX
     *            Origin / offset from block location.
     * @param oY
     *            the o y
     * @param oZ
     *            the o z
     * @param dX
     *            Direction (multiplied by dT to get end point of move).
     * @param dY
     *            the d y
     * @param dZ
     *            the d z
     * @param dT
     *            the d t
     * @return true, if is passable ray
     */
    public static final boolean isPassableRay(final BlockCache access, 
                                              final int blockX, final int blockY, final int blockZ, 
                                              final double oX, final double oY, final double oZ, 
                                              final double dX, final double dY, final double dZ, final double dT) {
        // NOTE: Method signature with node, nodeAbove.
        final IBlockCacheNode node = access.getOrCreateBlockCacheNode(blockX, blockY, blockZ, false);
        if (BlockProperties.isPassable(node.getType())) {
            return true;
        }
        double[] bounds = access.getBounds(blockX, blockY, blockZ);
        if (bounds == null) {
            return true;
        }

        // Simplified check: Only collision of bounds of the full move is checked.
        final double minX, maxX;
        if (dX < 0.0) {
            minX = dX * dT + oX + blockX;
            maxX = oX + blockX;
        }
        else {
            maxX = dX * dT + oX + blockX;
            minX = oX + blockX;
        }
        final double minY, maxY;
        if (dY < 0.0) {
            minY = dY * dT + oY + blockY;
            maxY = oY + blockY;
        }
        else {
            maxY = dY * dT + oY + blockY;
            minY = oY + blockY;
        }
        final double minZ, maxZ;
        if (dZ < 0.0) {
            minZ = dZ * dT + oZ + blockZ;
            maxZ = oZ + blockZ;
        }
        else {
            maxZ = dZ * dT + oZ + blockZ;
            minZ = oZ + blockZ;
        }
        if (!collidesBlock(access, minX, minY, minZ, maxX, maxY, maxZ, blockX, blockY, blockZ, node, null, BlockFlags.getBlockFlags(node.getType()) | BlockFlags.F_COLLIDE_EDGES)) {
            // NOTE: Might check for fence too, here.
            return true;
        }

        // NOTE: Actual ray-collision checking?

        // Check for workarounds.
        // NOTE: check f_itchy once exists.
        if (BlockProperties.isPassableWorkaround(access, blockX, blockY, blockZ, oX, oY, oZ, node, dX, dY, dZ,
                                                 minX, minY, minZ, maxX, maxY, maxZ, dT)) {
            return true;
        }
        // Does collide (most likely).
        // (Could allow start-end if passable + check first collision time or some estimate.)
        return false;
    }

    /**
     * Check passability with an arbitrary bounding box vs. a block.
     *
     * @param access
     *            the access
     * @param blockX
     *            the block x
     * @param blockY
     *            the block y
     * @param blockZ
     *            the block z
     * @param minX
     *            the min x
     * @param minY
     *            the min y
     * @param minZ
     *            the min z
     * @param maxX
     *            the max x
     * @param maxY
     *            the max y
     * @param maxZ
     *            the max z
     * @return true, if is passable box
     */
    public static final boolean isPassableBox(final BlockCache access, 
                                              final int blockX, final int blockY, final int blockZ,
                                              final double minX, final double minY, final double minZ,
                                              final double maxX, final double maxY, final double maxZ) {
        // NOTE: This mostly is copy and paste from isPassableRay.
        final IBlockCacheNode node = access.getOrCreateBlockCacheNode(blockX, blockY, blockZ, false);
        final Material id = node.getType();
        if (BlockProperties.isPassable(id)) {
            return true;
        }
        double[] bounds = access.getBounds(blockX, blockY, blockZ);
        if (bounds == null) {
            return true;
        }
        // (Coordinates are already passed in an ordered form.)
        if (!collidesBlock(access, minX, minY, minZ, maxX, maxY, maxZ, blockX, blockY, blockZ, node, null, BlockFlags.getBlockFlags(id) | BlockFlags.F_COLLIDE_EDGES)) {
            return true;
        }

        // Check for workarounds.
        // NOTE: Adapted to use the version initially intended for ray-tracing. Should have an explicit thing for the box, and let the current ray-tracing variant use that, until THEY implement something real.
        // NOTE: check f_itchy once exists.
        if (BlockProperties.isPassableWorkaround(access, blockX, blockY, blockZ, minX - blockX, minY - blockY, minZ - blockZ, node, maxX - minX, maxY - minY, maxZ - minZ, 
                                                 minX, minY, minZ, maxX, maxY, maxZ, 1.0)) {
            return true;
        }
        // Does collide (most likely).
        return false;
    }

    /**
     * Check if the bounding box collides with a block (passable + accounting
     * for workarounds).
     *
     * @param access
     *            the access
     * @param minX
     *            the min x
     * @param minY
     *            the min y
     * @param minZ
     *            the min z
     * @param maxX
     *            the max x
     * @param maxY
     *            the max y
     * @param maxZ
     *            the max z
     * @return true, if is passable box
     */
    public static final boolean isPassableBox(final BlockCache access, 
                                              final double minX, final double minY, final double minZ,
                                              final double maxX, final double maxY, final double maxZ) {
        final int iMinX = Location.locToBlock(minX);
        final int iMaxX = Location.locToBlock(maxX);
        final int iMinY = Location.locToBlock(minY);
        final int iMaxY = Location.locToBlock(maxY);
        final int iMinZ = Location.locToBlock(minZ);
        final int iMaxZ = Location.locToBlock(maxZ);
        for (int x = iMinX; x <= iMaxX; x++) {
            for (int z = iMinZ; z <= iMaxZ; z++) {
                for (int y = iMinY; y <= iMaxY; y++) {
                    if (!isPassableBox(access, x, y, z, minX, minY, minZ, maxX, maxY, maxZ)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Add the block coordinates that are colliding via a isPassableBox check
     * for the given bounds to the given container.
     *
     * @param access
     *            the access
     * @param minX
     *            the min x
     * @param minY
     *            the min y
     * @param minZ
     *            the min z
     * @param maxX
     *            the max x
     * @param maxY
     *            the max y
     * @param maxZ
     *            the max z
     * @param results
     *            the results
     * @return The number of added blocks.
     */
    public static final int collectInitiallyCollidingBlocks(final BlockCache access, 
                                                            final double minX, final double minY, final double minZ,
                                                            final double maxX, final double maxY, final double maxZ,
                                                            final BlockPositionContainer results) {
        int added = 0;
        final int iMinX = Location.locToBlock(minX);
        final int iMaxX = Location.locToBlock(maxX);
        final int iMinY = Location.locToBlock(minY);
        final int iMaxY = Location.locToBlock(maxY);
        final int iMinZ = Location.locToBlock(minZ);
        final int iMaxZ = Location.locToBlock(maxZ);
        for (int x = iMinX; x <= iMaxX; x++) {
            for (int z = iMinZ; z <= iMaxZ; z++) {
                for (int y = iMinY; y <= iMaxY; y++) {
                    if (!isPassableBox(access, x, y, z, minX, minY, minZ, maxX, maxY, maxZ)) {
                        results.addBlockPosition(x, y, z);
                        added ++;
                    }
                }
            }
        }
        final int iMinY2 = Location.locToBlock(minY - 0.5); // Include height150 blocks.
        if (iMinY != iMinY2) {
            for (int x = iMinX; x <= iMaxX; x++) {
                for (int z = iMinZ; z <= iMaxZ; z++) {
                    final IBlockCacheNode node = access.getOrCreateBlockCacheNode(x, iMinY2, z, false);
                    if ((BlockFlags.getBlockFlags(node.getType()) & BlockFlags.F_HEIGHT150) != 0) {
                        if (!isPassableBox(access, x, iMinY2, z, minX, minY, minZ, maxX, maxY, maxZ)) {
                            results.addBlockPosition(x, iMinY2, z);
                            added ++;
                        }
                    }
                }
            }
        }
        return added;
    }

    /**
     * Test for special case activation: trap door is climbable above ladder
     * with distinct facing.
     *
     * @return true, if is special case trap door above ladder
     */
    public static boolean isSpecialCaseTrapDoorAboveLadder() {
        return specialCaseTrapDoorAboveLadder;
    }

    /**
     * Set special case activation: trap door is climbable above ladder with
     * distinct facing.
     *
     * @param specialCaseTrapDoorAboveLadder
     *            the new special case trap door above ladder
     */
    public static void setSpecialCaseTrapDoorAboveLadder(boolean specialCaseTrapDoorAboveLadder) {
        BlockProperties.specialCaseTrapDoorAboveLadder = specialCaseTrapDoorAboveLadder;
    }

    public static int getMinWorldY() {
        return minWorldY;
    }
    private static void registerSlabs(MCAccess access, WorldConfigProvider<?> config) {
        final long stairFlags = BlockFlags.F_STAIRS | BlockFlags.F_GROUND | BlockFlags.F_SOLID;
        for (final Material mat : MaterialUtil.ALL_STAIRS) {
            BlockFlags.setFlag(mat, stairFlags);
        }
        final long stepFlags = BlockFlags.F_GROUND | BlockFlags.F_XZ100;
        for (final Material mat : new Material[]{BridgeMaterial.STONE_SLAB}) {
            BlockFlags.setFlag(mat, stepFlags);
        }
        for (final Material mat : MaterialUtil.SLABS) {
            BlockFlags.setFlag(mat, stepFlags);
        }
    }

    private static void registerFluidBlocks(MCAccess access) {
        for (final Material mat : MaterialUtil.WATER) {
            BlockFlags.setFlag(mat, BlockFlags.F_LIQUID | BlockFlags.F_HEIGHT_8SIM_DEC | BlockFlags.F_WATER | BlockFlags.F_FALLDIST_ZERO);
        }
        for (final Material mat : MaterialUtil.LAVA) {
            BlockFlags.setFlag(mat, BlockFlags.F_LIQUID | BlockFlags.F_LAVA | BlockFlags.F_FALLDIST_HALF | BlockFlags.F_HEIGHT_8SIM_DEC);
        }
    }

    private static void registerRedstoneComponents() {
        for (final Material mat : MaterialUtil.RAILS) {
            BlockFlags.setFlag(mat, BlockFlags.F_RAILS);
        }
        for (Material material : ALL_MATERIALS) {
            if (material.isBlock()) {
                final String name = material.name().toLowerCase();
                if (name.endsWith("_door") || name.endsWith("_trapdoor") || name.endsWith("fence_gate")) {
                    BlockFlags.setFlag(material, BlockFlags.F_VARIABLE_REDSTONE);
                    if (!name.contains("iron")) {
                        BlockFlags.setFlag(material, BlockFlags.F_VARIABLE_USE);
                    }
                }
                if (name.equals("iron_door_block")) {
                    BlockFlags.setFlag(material, BlockFlags.F_VARIABLE_REDSTONE);
                }
            }
        }
        for (final Material mat : new Material[]{Material.LADDER}) {
            BlockFlags.setFlag(mat, BlockFlags.F_FACING_LOW3D2_NSWE);
        }
        for (final Material mat : MaterialUtil.WOODEN_TRAP_DOORS) {
            BlockFlags.setFlag(mat, BlockFlags.F_ATTACHED_LOW2_SNEW);
        }
        final long paneFlags = BlockFlags.F_THIN_FENCE | BlockFlags.F_VARIABLE;
        for (final Material mat : new Material[]{BridgeMaterial.IRON_BARS}) {
            BlockFlags.setFlag(mat, paneFlags);
        }
        for (final Material mat : MaterialUtil.GLASS_PANES) {
            BlockFlags.setFlag(mat, paneFlags);
        }
    }


    /* =====================================================================
     *  Extracted helpers from the former registerMiscSpecialBlocks()
     * ===================================================================*/

    private static void registerClimbables() {
        for (final Material mat : new Material[]{Material.VINE, Material.LADDER}) {
            BlockFlags.setFlag(mat, BlockFlags.F_CLIMBABLE);
        }
    }

    private static void registerGroundOverrides() {
        final Material[] mats = {
            Material.COCOA, Material.SNOW, Material.LADDER, Material.BREWING_STAND,
            BridgeMaterial.get("DIODE_BLOCK_OFF"), BridgeMaterial.get("DIODE_BLOCK_ON"),
            BridgeMaterial.getBlock("comparator"), BridgeMaterial.getBlock("daylight_detector"),
            BridgeMaterial.LILY_PAD, BridgeMaterial.PISTON_HEAD,
            BridgeMaterial.STONE_SLAB, BridgeMaterial.REPEATER
        };
        for (final Material mat : mats) {
            if (mat != null) BlockFlags.setFlag(mat, BlockFlags.F_GROUND);
        }
    }

    private static void registerPistonOverrides() {
        // Moving piston: indestructible + special bounds
        setBlockProps(BridgeMaterial.MOVING_PISTON, indestructibleType);
        BlockFlags.setFlag(BridgeMaterial.MOVING_PISTON,
            BlockFlags.F_IGN_PASSABLE | BlockFlags.F_GROUND | BlockFlags.F_GROUND_HEIGHT | BlockFlags.FULL_BOUNDS);

        // Farmland reports 0.9375 height server-side but should be treated full
        BlockFlags.setFlag(BridgeMaterial.FARMLAND, BlockFlags.F_HEIGHT100);

        // Signs are erroneously marked solid by the server – correct that
        BlockFlags.maskFlag(BridgeMaterial.SIGN, ~(BlockFlags.F_GROUND | BlockFlags.F_SOLID));
    }

    private static void registerIgnorePassables() {
        final Material[] mats = {
            BridgeMaterial.STONE_PRESSURE_PLATE,
            BridgeMaterial.WOODEN_PRESSURE_PLATE,
            BridgeMaterial.SIGN,
            BridgeMaterial.get("DIODE_BLOCK_ON"),
            BridgeMaterial.get("DIODE_BLOCK_OFF")
        };
        for (final Material mat : mats) {
            if (mat != null) BlockFlags.setFlag(mat, BlockFlags.F_IGN_PASSABLE);
        }
    }

    private static void registerFenceHeights() {
        final long flags150 = BlockFlags.F_HEIGHT150 | BlockFlags.F_VARIABLE | BlockFlags.F_THICK_FENCE;

        // 1.5-high thick fences & walls
        for (final Material mat : new Material[]{BridgeMaterial.NETHER_BRICK_FENCE, BridgeMaterial.COBBLESTONE_WALL}) {
            BlockFlags.setFlag(mat, flags150);
        }
        for (final Material mat : MaterialUtil.WOODEN_FENCES) {
            BlockFlags.setFlag(mat, flags150);
        }

        // Fence gates require additional passable flag
        for (final Material mat : MaterialUtil.WOODEN_FENCE_GATES) {
            BlockFlags.setFlag(mat, flags150 | BlockFlags.F_PASSABLE_X4);
        }

        // Trap doors vary by state
        for (final Material mat : MaterialUtil.WOODEN_TRAP_DOORS) {
            BlockFlags.setFlag(mat, BlockFlags.F_VARIABLE);
        }
    }

    private static void registerThinFences() {
        final long paneFlags = BlockFlags.F_THIN_FENCE | BlockFlags.F_VARIABLE;
        BlockFlags.setFlag(BridgeMaterial.IRON_BARS, paneFlags);
        for (final Material mat : MaterialUtil.GLASS_PANES) {
            BlockFlags.setFlag(mat, paneFlags);
        }
    }

    private static void registerInstantBreakables() {
        for (final Material mat : new Material[]{
            Material.TNT,
            BridgeMaterial.get("DIODE_BLOCK_ON"),
            BridgeMaterial.get("DIODE_BLOCK_OFF"),
            BridgeMaterial.get("repeater"),
            BridgeMaterial.get("sea_pickle"),
            BridgeMaterial.LILY_PAD,
            BridgeMaterial.COMMAND_BLOCK }) {
            if (mat != null) setBlock(mat, instantType);
        }
        for (final Material mat : MaterialUtil.INSTANT_PLANTS) {
            setBlock(mat, instantType);
        }
    }

    private static void registerPassableInstantBreakables() {
        for (final Material mat : new Material[] {
            Material.REDSTONE_WIRE,
            BridgeMaterial.get("REDSTONE_TORCH_ON"),
            BridgeMaterial.get("REDSTONE_TORCH_OFF"),
            BridgeMaterial.get("redstone_torch"),
            BridgeMaterial.get("redstone_wall_torch"),
            Material.TRIPWIRE,
            Material.TRIPWIRE_HOOK,
            Material.TORCH,
            Material.FIRE }) {
            if (mat != null) {
                setBlock(mat, instantType);
                BlockFlags.addFlags(mat, BlockFlags.F_IGN_PASSABLE);
            }
        }
    }

    private static void registerLeafBlocks() {
        for (final Material mat : MaterialUtil.LEAVES) {
            setBlock(mat, leafType);
            BlockFlags.setFlag(mat, BlockFlags.F_LEAVES);
        }
    }

    private static void registerBedBlocks() {
        for (Material mat : MaterialUtil.BEDS) {
            setBlock(mat, leafType);
            BlockFlags.setFlag(mat, BlockFlags.F_GROUND | BlockFlags.F_SOLID | BlockFlags.F_BED);
        }
    }

    private static void registerHugeMushroomBlocks() {
        for (Material mat : new Material[]{ Material.VINE, Material.COCOA }) {
            setBlock(mat, hugeMushroomType);
        }
        for (Material mat : MaterialUtil.MUSHROOM_BLOCKS) {
            setBlock(mat, hugeMushroomType);
        }
    }

    private static void registerPressurePlates() {
        for (Material mat : MaterialUtil.WOODEN_PRESSURE_PLATES) {
            setBlockProps(mat, new BlockProps(woodAxe, 0.5f));
        }
        setBlock(BridgeMaterial.STONE_PRESSURE_PLATE, new BlockProps(woodPickaxe, 0.5f, true));
    }

    private static void registerSandAndGravelBlocks() {
        setBlock(Material.SAND, sandType);
        setBlock(Material.SOUL_SAND, sandType);
        setBlock(Material.DIRT, sandType);
        for (Material mat : MaterialUtil.CONCRETE_POWDER_BLOCKS) {
            BlockInit.setAs(mat, Material.DIRT);
        }
        for (Material mat : new Material[] {
            BridgeMaterial.MYCELIUM,
            BridgeMaterial.FARMLAND,
            BridgeMaterial.GRASS_BLOCK,
            Material.GRAVEL,
            Material.CLAY }) {
            setBlock(mat, gravelType);
        }
    }

    private static void registerLeverAndPistonBlocks() {
        for (Material mat : new Material[]{
            Material.LEVER,
            BridgeMaterial.PISTON,
            BridgeMaterial.PISTON_HEAD,
            BridgeMaterial.STICKY_PISTON }) {
            setBlock(mat, leverType);
        }
        setBlock(BridgeMaterial.CAKE, leverType);
    }

    private static void registerMiscBlockSets() {
        for (Material mat : MaterialUtil.RAILS) {
            setBlock(mat, new BlockProps(woodPickaxe, 0.7f));
        }
        for (Material mat : MaterialUtil.INFESTED_BLOCKS) {
            setBlock(mat, new BlockProps(noTool, 0.75f));
        }
        for (Material mat : MaterialUtil.WOOD_BLOCKS) {
            setBlock(mat, new BlockProps(noTool, 0.8f));
        }
        setBlock(Material.SANDSTONE, sandStoneType);
        setBlock(Material.SANDSTONE_STAIRS, sandStoneType);
        for (Material mat : new Material[] {
            Material.STONE,
            BridgeMaterial.STONE_BRICKS,
            BridgeMaterial.STONE_BRICK_STAIRS }) {
            setBlock(mat, stoneTypeI);
        }
        BlockProps pumpkinType = new BlockProps(woodAxe, 1f);
        setBlock(BridgeMaterial.SIGN, pumpkinType);
        setBlock(Material.PUMPKIN, pumpkinType);
        setBlock(Material.JACK_O_LANTERN, pumpkinType);
        registerWoodTypes();
        registerBrickTypes();
        setBlock(BridgeMaterial.CRAFTING_TABLE, chestType);
        setBlock(Material.CHEST, chestType);
        if (BridgeMaterial.has("locked_chest")) {
            BlockFlags.setFlagsAs("LOCKED_CHEST", Material.CHEST);
            setBlockProps("LOCKED_CHEST", BlockProperties.chestType);
        }
    }

    private static void registerOreAndMineralBlocks() {
        for (Material mat : new Material[] {
            BridgeMaterial.END_STONE,
            Material.COAL_ORE }) {
            setBlock(mat, coalType);
        }
        BlockProps ironType = new BlockProps(stonePickaxe, 3f, true);
        for (Material mat : new Material[] {
            Material.LAPIS_ORE,
            Material.LAPIS_BLOCK,
            Material.IRON_ORE }) {
            setBlock(mat, ironType);
        }
        BlockProps diamondType = new BlockProps(ironPickaxe, 3f, true);
        for (Material mat : new Material[] {
            Material.REDSTONE_ORE,
            Material.EMERALD_ORE,
            Material.GOLD_ORE,
            Material.DIAMOND_ORE,
            BridgeMaterial.get("glowing_redstone_ore") }) {
            if (mat != null) setBlock(mat, diamondType);
        }
        setBlock(Material.GOLD_BLOCK, goldBlockType);
    }

    private static void registerToolSensitiveBlocks() {
        setBlock(Material.FURNACE, dispenserType);
        if (BridgeMaterial.has("burning_furnace")) {
            setBlock(BridgeMaterial.get("burning_furnace"), dispenserType);
        }
        setBlock(Material.DISPENSER, dispenserType);
        for (Material mat : new Material[] {
            Material.EMERALD_BLOCK,
            BridgeMaterial.SPAWNER,
            BridgeMaterial.IRON_DOOR,
            BridgeMaterial.IRON_BARS,
            BridgeMaterial.ENCHANTING_TABLE }) {
            setBlock(mat, ironDoorType);
        }
        setBlock(Material.IRON_BLOCK, ironBlockType);
        setBreakingTimeOverridesByEfficiency(
            new BlockBreakKey().blockType(Material.IRON_BLOCK).toolType(ToolType.PICKAXE).materialBase(MaterialBase.WOOD),
            ironBlockType.breakingTimes[1], 6200L, 3500L, 2050L, 1350L, 900L, 500L);
        setBlock(Material.DIAMOND_BLOCK, diamondBlockType);
        for (Material mat : MaterialUtil.WOODEN_BUTTONS) {
            setBlock(mat, new BlockProps(woodAxe, 0.5f));
        }
        BlockProps props = new BlockProps(noTool, 1f);
        for (Material mat : MaterialUtil.HEADS_GROUND) {
            setBlock(mat, props);
            BlockFlags.setFlag(mat, BlockFlags.F_SOLID | BlockFlags.F_GROUND);
        }
        setBlock(Material.ANVIL, new BlockProps(woodPickaxe, 5f, true));
        for (final Material mat : MaterialUtil.FLOWER_POTS) {
            BlockFlags.addFlags(mat, BlockFlags.F_SOLID | BlockFlags.F_GROUND);
            setBlockProps(mat, instantType);
        }
    }

    private static void registerIndestructibleBlocks() {
        for (Material mat : new Material[]{
            Material.AIR,
            Material.BEDROCK,
            BridgeMaterial.END_PORTAL,
            BridgeMaterial.END_PORTAL_FRAME,
            BridgeMaterial.NETHER_PORTAL,
            BridgeMaterial.get("void_air") }) {
            if (mat != null) setBlock(mat, indestructibleType);
        }
        List<Set<Material>> indestructible = new LinkedList<Set<Material>>(Arrays.asList(MaterialUtil.LAVA, MaterialUtil.WATER));
        for (Set<Material> set : indestructible) {
            for (Material mat : set) {
                setBlock(mat, indestructibleType);
            }
        }
        BlockFlags.setBlockFlags(Material.BEDROCK, BlockFlags.FULLY_SOLID_BOUNDS);
    }

    private static void registerSpecialBlocks() {
        BlockProps props;
        props = new BlockProps(BlockProperties.woodPickaxe, 1.25f, true);
        for (final Material mat : MaterialUtil.TERRACOTTA_BLOCKS) {
            if (mat != null) {
                BlockProperties.setBlockProps(mat, props);
                BlockFlags.setFlagsAs(mat, Material.STONE);
            }
        }
        props = new BlockProps(BlockProperties.woodPickaxe, 1.4f, true);
        for (final Material mat : MaterialUtil.GLAZED_TERRACOTTA_BLOCKS) {
            if (mat != null) {
                BlockProperties.setBlockProps(mat, props);
                BlockFlags.setFlagsAs(mat, BridgeMaterial.TERRACOTTA);
            }
        }
        BlockProps carpetProps = new BlockProps(BlockProperties.noTool, 0.1f);
        long carpetFlags = BlockFlags.F_GROUND | BlockFlags.F_CARPET;
        for (final Material mat : MaterialUtil.CARPETS) {
            BlockProperties.setBlockProps(mat, carpetProps);
            BlockFlags.setBlockFlags(mat, carpetFlags);
        }
        props = new BlockProps(BlockProperties.woodAxe, 1f);
        for (Material mat : MaterialUtil.BANNERS) {
            BlockFlags.setBlockFlags(mat, 0L);
            setBlockProps(mat, props);
        }
        for (Material mat : MaterialUtil.WALL_BANNERS) {
            setBlockProps(mat, props);
        }
        registerShulkerBoxes();
        registerConcreteBlocks();
        props = new BlockProps(tools.get(Material.SHEARS), 0.8f);
        for (Material mat : MaterialUtil.WOOL_BLOCKS) {
            BlockFlags.setFlagsAs(mat, Material.STONE);
            setBlockProps(mat, props);
        }
    }

    private static void registerBoundsAndPassables() {
        for (Material mat : MaterialUtil.FULLY_SOLID_BLOCKS) {
            BlockFlags.addFlags(mat, BlockFlags.FULL_BOUNDS | BlockFlags.F_SOLID);
        }
        for (Material mat : MaterialUtil.FULLY_PASSABLE_BLOCKS) {
            BlockFlags.addFlags(mat, BlockFlags.F_IGN_PASSABLE);
            BlockFlags.removeFlags(mat, BlockFlags.F_SOLID | BlockFlags.F_GROUND);
        }
    }

    private static void registerConcreteBlocks() {
        BlockProps props = new BlockProps(BlockProperties.woodPickaxe, 1.8f, true);
        for (Material mat : MaterialUtil.CONCRETE_BLOCKS) {
            setBlockProps(mat, props);
            BlockFlags.setFlagsAs(mat, Material.COBBLESTONE);
        }
    }

    private static void registerGlassBlocks() {
        for (Material mat : new Material[] {
            BridgeMaterial.get("REDSTONE_LAMP_ON"),
            BridgeMaterial.get("REDSTONE_LAMP_OFF"),
            BridgeMaterial.get("REDSTONE_LAMP"),
            Material.GLOWSTONE,}) {
            if (mat != null) setBlock(mat, glassType);
        }
        for (final Material mat : MaterialUtil.GLASS_BLOCKS) {
            setBlock(mat, glassType);
        }
        for (final Material mat : MaterialUtil.GLASS_PANES) {
            setBlock(mat, glassType);
        }
    }

    private static void registerWoodTypes() {
        final List<Set<Material>> woodTypes = Arrays.asList(
            MaterialUtil.WOODEN_FENCE_GATES,
            MaterialUtil.WOODEN_FENCES,
            MaterialUtil.WOODEN_STAIRS,
            MaterialUtil.WOODEN_SLABS,
            MaterialUtil.LOGS,
            MaterialUtil.WOOD_BLOCKS,
            MaterialUtil.PLANKS);
        for (final Set<Material> set : woodTypes) {
            for (final Material mat : set) {
                setBlock(mat, woodType);
            }
        }
        for (Material mat : new Material[] {
            Material.JUKEBOX,
            BridgeMaterial.get("wood_double_step"),}) {
            if (mat != null) setBlock(mat, woodType);
        }
        for (Material mat : MaterialUtil.STRIPPED_LOGS) {
            BlockInit.setAs(mat, BridgeMaterial.OAK_LOG);
        }
        for (Material mat : MaterialUtil.STRIPPED_WOOD_BLOCKS) {
            BlockInit.setAs(mat, BridgeMaterial.OAK_WOOD);
        }
        for (Material mat : MaterialUtil.WOODEN_DOORS) {
            setBlock(mat, woodDoorType);
        }
        for (Material mat : MaterialUtil.WOODEN_TRAP_DOORS) {
            setBlock(mat, woodDoorType);
        }
    }

    private static void registerBrickTypes() {
        for (Material mat : new Material[] {
            // Vanilla blocks
            Material.CAULDRON,
            Material.COBBLESTONE_STAIRS,
            Material.COBBLESTONE,
            Material.NETHER_BRICK_STAIRS,
            Material.MOSSY_COBBLESTONE,
            Material.BRICK_STAIRS,
            
            // Bridge/alias materials
            BridgeMaterial.NETHER_BRICK_FENCE,
            BridgeMaterial.BRICK_SLAB,
            BridgeMaterial.STONE_SLAB,
            BridgeMaterial.NETHER_BRICKS,
            BridgeMaterial.BRICKS,
            BridgeMaterial.get("double_step"),}) {
            if (mat != null) setBlock(mat, brickType);
        }
        BlockFlags.setBlockFlags(Material.CAULDRON, BlockFlags.SOLID_GROUND | BlockFlags.F_GROUND_HEIGHT | BlockFlags.F_MIN_HEIGHT16_5); // LEGACY
        setBlock(BridgeMaterial.COBBLESTONE_WALL, brickType);
    }

    private static void registerShulkerBoxes() {
        for (Material mat : MaterialUtil.SHULKER_BOXES) {
            BlockProperties.setBlockProps(mat, new BlockProps(BlockProperties.woodPickaxe, 2f));
            BlockFlags.setBlockFlags(mat, BlockFlags.F_SOLID | BlockFlags.F_GROUND);
        }
    }

    private static void registerSpecialStaticFlags() {
        // Flexible ground height for farmland (server uses bounding-box height)
        BlockFlags.setFlag(BridgeMaterial.FARMLAND,
                BlockFlags.F_GROUND_HEIGHT | BlockFlags.F_GROUND);

        // End-portal frames are fully solid ground
        BlockFlags.setFlag(BridgeMaterial.END_PORTAL_FRAME, BlockFlags.SOLID_GROUND);

        // Cobweb slows & is pass-through but solid for ray trace
        BlockFlags.setFlag(BridgeMaterial.COBWEB,
            BlockFlags.F_COBWEB | BlockFlags.FULL_BOUNDS | BlockFlags.F_IGN_PASSABLE);

        // Soulsand slow walk
        BlockFlags.setFlag(Material.SOUL_SAND, BlockFlags.F_SOULSAND | BlockFlags.SOLID_GROUND);

        // Ice slipperiness
        BlockFlags.setFlag(Material.ICE, BlockFlags.F_ICE);

        // Cake (ground but not solid)
        BlockFlags.setFlag(BridgeMaterial.CAKE, BlockFlags.F_GROUND);

        // Cobblestone wall: keep legacy height flag for 1.5-high bounding box
        BlockFlags.setFlag(BridgeMaterial.COBBLESTONE_WALL, BlockFlags.F_HEIGHT150);
    }

    private static boolean doubleEquals(double a, double b) {
        return Math.abs(a - b) < 1.0e-6;
    }

}
