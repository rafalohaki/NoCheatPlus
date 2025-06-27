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

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.compat.AlmostBoolean;
import fr.neatmonster.nocheatplus.compat.BridgeHealth;
import fr.neatmonster.nocheatplus.compat.BridgeMaterial;
import fr.neatmonster.nocheatplus.compat.blocks.init.BlockInit;
import fr.neatmonster.nocheatplus.compat.bukkit.model.*;
import fr.neatmonster.nocheatplus.compat.cbreflect.reflect.ReflectBase;
import fr.neatmonster.nocheatplus.compat.cbreflect.reflect.ReflectDamageSource;
import fr.neatmonster.nocheatplus.compat.cbreflect.reflect.ReflectDamageSources;
import fr.neatmonster.nocheatplus.compat.cbreflect.reflect.ReflectLivingEntity;
import fr.neatmonster.nocheatplus.config.WorldConfigProvider;
import fr.neatmonster.nocheatplus.utilities.ReflectionUtil;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache;
import fr.neatmonster.nocheatplus.utilities.map.BlockFlags;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;
import fr.neatmonster.nocheatplus.utilities.map.MaterialUtil;

public class MCAccessBukkitModern extends MCAccessBukkit {

    protected ReflectBase reflectBase = null;
    protected ReflectDamageSource reflectDamageSource = null;
    protected ReflectDamageSources reflectDamageSources = null;
    protected ReflectLivingEntity reflectLivingEntity = null;
    protected final Map<Material, BukkitShapeModel> shapeModels = new HashMap<Material, BukkitShapeModel>();

    // Blocks that can be fetched automatically from from the Bukkit API
    private static final BukkitShapeModel MODEL_AUTO_FETCH = new BukkitFetchableBounds();
    private static final BukkitShapeModel MODEL_AUTO_FETCH_LEGACY = new BukkitFetchableBound();

    // Blocks that are formed from multiple bounding boxes
    private static final BukkitShapeModel MODEL_BREWING_STAND = BukkitStatic.ofBounds(
        // Bottom rod
        0.0625, 0.0, 0.0625, 0.9375, 0.125, 0.9375,
        // Rod
        0.4375, 0.125, 0.4375, 0.5625, 0.875, 0.5625
    );
    private static final BukkitShapeModel MODEL_CANDLE_CAKE = BukkitStatic.ofBounds(
        // Cake
        0.0625, 0.0, 0.0625, 0.9375, 0.5, 0.9375,
        // Candle
        0.4375, 0.5, 0.4375, 0.5625, 0.875, 0.5625
    );
    private static final BukkitShapeModel MODEL_LECTERN = BukkitStatic.ofBounds(
        // Post
        0.0, 0.0, 0.0, 1.0, 0.125, 1.0,
        // Lectern
        0.25, 0.125, 0.25, 0.75, 0.875, 0.75
    );
    private static final BukkitShapeModel MODEL_HOPPER = new BukkitHopper();
    private static final BukkitShapeModel MODEL_CAULDRON = new BukkitCauldron(0.1875, 0.125, 0.8125, 0.0625);
    private static final BukkitShapeModel MODEL_COMPOSTER = new BukkitCauldron(0.0, 0.125, 1.0, 0.125);
    private static final BukkitShapeModel MODEL_PISTON_HEAD = new BukkitPistonHead();
    private static final BukkitShapeModel MODEL_BELL = new BukkitBell();
    private static final BukkitShapeModel MODEL_ANVIL = new BukkitAnvil();
    private static final BukkitShapeModel MODEL_GRIND_STONE = new BukkitGrindStone();

    // Blocks that change shape based on interaction or redstone.
    private static final BukkitShapeModel MODEL_DOOR = new BukkitDoor();
    private static final BukkitShapeModel MODEL_TRAP_DOOR = new BukkitTrapDoor();
    private static final BukkitShapeModel MODEL_GATE = new BukkitGate(0.375, 1.5);
    private static final BukkitShapeModel MODEL_SHULKER_BOX = new BukkitShulkerBox();
    private static final BukkitShapeModel MODEL_CHORUS_PLANT = new BukkitChorusPlant();
    private static final BukkitShapeModel MODEL_DRIP_LEAF = new BukkitDripLeaf();

    // Blocks with different heights based on whatever.
    private static final BukkitShapeModel MODEL_END_PORTAL_FRAME = new BukkitEndPortalFrame();
    private static final BukkitShapeModel MODEL_SEA_PICKLE = new BukkitSeaPickle();
    private static final BukkitShapeModel MODEL_COCOA = new BukkitCocoa();
    private static final BukkitShapeModel MODEL_TURTLE_EGG = new BukkitTurtleEgg();

    // Blocks that have a different shape, based on how they have been placed.
    private static final BukkitShapeModel MODEL_CAKE = new BukkitCake();
    private static final BukkitShapeModel MODEL_SLAB = new BukkitSlab();
    private static final BukkitShapeModel MODEL_STAIRS = new BukkitStairs();
    private static final BukkitShapeModel MODEL_SNOW = new BukkitSnow();
    private static final BukkitShapeModel MODEL_PISTON = new BukkitPiston();
    private static final BukkitShapeModel MODEL_LEVELLED = new BukkitLevelled();
    private static final BukkitShapeModel MODEL_LADDER = new BukkitLadder();
    private static final BukkitShapeModel MODEL_RAIL = new BukkitRail();
    private static final BukkitShapeModel MODEL_END_ROD = new BukkitDirectionalCentered(0.375, 1.0, false);

    // Blocks that have a different shape with neighbor blocks (bukkit takes care though).
    private static final BukkitShapeModel MODEL_THIN_FENCE = new BukkitFence(0.4375, 1.0);
    private static final BukkitShapeModel MODEL_THICK_FENCE = new BukkitFence(0.375, 1.5);
    private static final BukkitShapeModel MODEL_THICK_FENCE2 = new BukkitWall(0.25, 1.5, 0.3125); // .75 .25 0 max: .25 .75 .5
    private static final BukkitShapeModel MODEL_WALL_HEAD = new BukkitWallHead();

    // Static blocks (various height and inset values).
    private static final BukkitShapeModel MODEL_CAMPFIRE = BukkitStatic.ofInsetAndHeight(0.0, 0.4375);
    private static final BukkitShapeModel MODEL_BAMBOO = new BukkitBamboo();
    private static final BukkitShapeModel MODEL_WATER_PLANTS = new BukkitWaterPlant();
    private static final BukkitShapeModel MODEL_LILY_PAD = BukkitStatic.ofInsetAndHeight(0.0625, 0.09375);
    private static final BukkitShapeModel MODEL_FLOWER_POT = BukkitStatic.ofInsetAndHeight(0.3125, 0.375);
    private static final BukkitShapeModel MODEL_LANTERN = new BukkitLantern();
    private static final BukkitShapeModel MODEL_CONDUIT = BukkitStatic.ofBounds(0.3125, 0.3125, 0.3125, 0.6875, 0.6875, 0.6875);
    private static final BukkitShapeModel MODEL_GROUND_HEAD = BukkitStatic.ofInsetAndHeight(0.25, 0.5);
    private static final BukkitShapeModel MODEL_SINGLE_CHEST = BukkitStatic.ofInsetAndHeight(0.0625, 0.875);
    private static final BukkitShapeModel MODEL_HONEY_BLOCK = BukkitStatic.ofInsetAndHeight(0.0625, 0.9375);
    /** Bounds for cactus blocks: inset by 1/16 and full height. */
    private static final BukkitShapeModel MODEL_CACTUS = BukkitStatic.ofInsetAndHeight(0.0625, 1.0);
    private static final BukkitShapeModel MODEL_SCULK_SHRIEKER = BukkitStatic.ofInsetAndHeight(0.0, 0.5);

    // Static blocks with full height sorted by inset.
    private static final BukkitShapeModel MODEL_INSET16_1_HEIGHT100 = BukkitStatic.ofInsetAndHeight(0.0625, 1.0);

    // Static blocks with full xz-bounds sorted by height.
    private static final BukkitShapeModel MODEL_XZ100_HEIGHT16_1 = BukkitStatic.ofHeight(0.0625);
    private static final BukkitShapeModel MODEL_XZ100_HEIGHT8_1 = BukkitStatic.ofHeight(0.125);
    private static final BukkitShapeModel MODEL_XZ100_HEIGHT8_3 = BukkitStatic.ofHeight(0.375);
    private static final BukkitShapeModel MODEL_XZ100_HEIGHT16_9 = BukkitStatic.ofHeight(0.5625);
    private static final BukkitShapeModel MODEL_XZ100_HEIGHT4_3 = BukkitStatic.ofHeight(0.75);
    private static final BukkitShapeModel MODEL_XZ100_HEIGHT16_15 = BukkitStatic.ofHeight(0.9375);
    private static final BukkitShapeModel MODEL_XZ100_HEIGHT8_7 = BukkitStatic.ofHeight(0.875);

    public MCAccessBukkitModern() {
        super();
        // Generic setup via Bukkit interfaces; fetch methods when available.
        BlockInit.assertMaterialExists("OAK_LOG");
        BlockInit.assertMaterialExists("CAVE_AIR");
        try {
            this.reflectBase = new ReflectBase();
            this.reflectDamageSource = new ReflectDamageSource(this.reflectBase);
            this.reflectLivingEntity = new ReflectLivingEntity(this.reflectBase, null, this.reflectDamageSource);
            // Can be null
            this.reflectDamageSources = new ReflectDamageSources(this.reflectBase, this.reflectDamageSource);
        }
        catch (ClassNotFoundException ex) {
            // Optional classes may not be present on older versions.
        }
        catch (RuntimeException ex) {
            // Optional classes may not be present on older versions.
        }
    }

    @Override
    public String getMCVersion() {
        return "1.13-1.21|?";
    }

    @Override
    public BlockCache getBlockCache() {
        return new BlockCacheBukkitModern(shapeModels);
    }

    public void addModel(Material mat, BukkitShapeModel model) {
        processedBlocks.add(mat);
        shapeModels.put(mat, model);
    }

    @Override
    public void setupBlockProperties(final WorldConfigProvider<?> worldConfigProvider) {
        /*
         * Incomplete block bounds still rely on workarounds (no movement impact):
         * All fences, glass pane, iron bar, chorus plant, hopper
         *
         * Legacy blocks also rely on workarounds (no movement impact):
         * All fences, glass pane, iron bar, chorus plant, hopper, cauldron
         *
         * Scaffolding affects fall damage.
         */

        applyTemporaryFlagFixes();
        registerBaseBlocks();
        registerAutoFetchedBlocks();
        registerModelGroups();
        registerMiscBlocks();
        registerFlagBasedModels();

        super.setupBlockProperties(worldConfigProvider);
    }

    private void addModels(Collection<Material> materials, BukkitShapeModel model) {
        for (Material mat : materials) {
            addModel(mat, model);
        }
    }

    private void registerCandles() {
        addModels(MaterialUtil.ALL_CANDLES, MODEL_AUTO_FETCH);
    }

    private void registerAmethyst() {
        addModels(MaterialUtil.AMETHYST, MODEL_AUTO_FETCH);
    }

    private void registerWalls() {
        for (Material mat : MaterialUtil.ALL_WALLS) {
            BlockFlags.setBlockFlags(mat, BlockFlags.SOLID_GROUND | BlockFlags.F_VARIABLE);
            addModel(mat, MODEL_THICK_FENCE2);
        }
    }

    private void applyTemporaryFlagFixes() {
        final long blockFix = BlockFlags.SOLID_GROUND;
        BlockFlags.setBlockFlags(Material.COCOA, blockFix);
        BlockFlags.setBlockFlags(Material.TURTLE_EGG, blockFix);
        BlockFlags.setBlockFlags(Material.CHORUS_PLANT, blockFix);
        BlockFlags.setBlockFlags(Material.CREEPER_WALL_HEAD, blockFix);
        BlockFlags.setBlockFlags(Material.ZOMBIE_WALL_HEAD, blockFix);
        BlockFlags.setBlockFlags(Material.PLAYER_WALL_HEAD, blockFix);
        BlockFlags.setBlockFlags(Material.DRAGON_WALL_HEAD, blockFix);
        BlockFlags.setBlockFlags(Material.WITHER_SKELETON_WALL_SKULL, blockFix);
        BlockFlags.setBlockFlags(Material.SKELETON_WALL_SKULL, blockFix);
    }

    private void registerBaseBlocks() {
        for (final Material mat : new Material[] {
            BridgeMaterial.COBWEB,
            BridgeMaterial.MOVING_PISTON,
            Material.SNOW,
            Material.BEACON,
            Material.VINE,
            Material.CHORUS_FLOWER}) {
            processedBlocks.add(mat);
        }

        for (final Material mat : BridgeMaterial.getAllBlocks(
            "light", "glow_lichen", "big_dripleaf_stem",
            "scaffolding", "powder_snow")) {
            processedBlocks.add(mat);
        }

        registerCandles();
        registerAmethyst();
    }

    private void registerAutoFetchedBlocks() {
        for (Material mat : BridgeMaterial.getAllBlocks(
            "azalea", "flowering_azalea",
            "sculk_sensor", "pointed_dripstone", "frogspawn",
            "sniffer_egg", "decorated_pot", "pitcher_crop", "calibrated_sculk_sensor")) {
            addModel(mat, MODEL_AUTO_FETCH);
        }

        for (Material mat : MaterialUtil.WALL_HANGING_SIGNS) {
            addModel(mat, MODEL_AUTO_FETCH);
        }

        for (Material mat : BridgeMaterial.getAllBlocks(
            "stonecutter", "chain")) {
            addModel(mat, MODEL_AUTO_FETCH_LEGACY);
        }
    }

    private void registerModelGroups() {
        registerCampfireAndCauldron();
        registerAnvils();
        registerStaticBlockModels();
        registerMiscModelCollections();
        registerHeadAndDoorModels();
        registerLiquidAndRailModels();
        registerWalls();
    }

    private void registerCampfireAndCauldron() {
        addModels(BridgeMaterial.getAllBlocks("campfire", "soul_campfire"), MODEL_CAMPFIRE);
        for (Material mat : MaterialUtil.CAULDRON) {
            BlockFlags.setBlockFlags(mat, BlockFlags.SOLID_GROUND);
        }
        addModels(MaterialUtil.CAULDRON, MODEL_CAULDRON);
    }

    private void registerAnvils() {
        addModels(Arrays.asList(
                Material.ANVIL,
                Material.CHIPPED_ANVIL,
                Material.DAMAGED_ANVIL), MODEL_ANVIL);
    }

    private void registerStaticBlockModels() {
        addModel(BridgeMaterial.LILY_PAD, MODEL_LILY_PAD);
        addModel(BridgeMaterial.END_PORTAL_FRAME, MODEL_END_PORTAL_FRAME);
        addModel(BridgeMaterial.CAKE, MODEL_CAKE);
        addModels(MaterialUtil.RODS, MODEL_END_ROD);
        addModel(Material.HOPPER, MODEL_HOPPER);
        addModel(Material.LADDER, MODEL_LADDER);
        addModel(Material.BREWING_STAND, MODEL_BREWING_STAND);
        addModel(Material.DRAGON_EGG, MODEL_INSET16_1_HEIGHT100);
        addModel(Material.CACTUS, MODEL_CACTUS);
    }

    private void registerMiscModelCollections() {
        addModels(Arrays.asList(BridgeMaterial.REPEATER, Material.COMPARATOR), MODEL_XZ100_HEIGHT8_1);
        addModels(Collections.singleton(Material.DAYLIGHT_DETECTOR), MODEL_XZ100_HEIGHT8_3);
        addModels(Collections.singleton(BridgeMaterial.ENCHANTING_TABLE), MODEL_XZ100_HEIGHT4_3);
        addModels(MaterialUtil.ALL_CANDLE_CAKE, MODEL_CANDLE_CAKE);
        addModels(Collections.singleton(Material.SOUL_SAND), MODEL_XZ100_HEIGHT8_7);
        addModels(Arrays.asList(BridgeMaterial.GRASS_PATH, BridgeMaterial.FARMLAND), MODEL_XZ100_HEIGHT16_15);
        addModels(MaterialUtil.addBlocks(MaterialUtil.GLASS_PANES, BridgeMaterial.IRON_BARS), MODEL_THIN_FENCE);
        addModels(MaterialUtil.SLABS, MODEL_SLAB);
        addModels(MaterialUtil.SHULKER_BOXES, MODEL_SHULKER_BOX);
        addModels(BridgeMaterial.getAllBlocks("chest", "trapped_chest", "ender_chest"), MODEL_SINGLE_CHEST);
        addModels(MaterialUtil.BEDS, MODEL_XZ100_HEIGHT16_9);
        addModels(MaterialUtil.FLOWER_POTS, MODEL_FLOWER_POT);
        addModels(Collections.singleton(Material.TURTLE_EGG), MODEL_TURTLE_EGG);
        addModels(Collections.singleton(Material.CONDUIT), MODEL_CONDUIT);
        addModels(Collections.singleton(Material.COCOA), MODEL_COCOA);
        addModels(Collections.singleton(Material.SEA_PICKLE), MODEL_SEA_PICKLE);
        addModels(MaterialUtil.CARPETS, MODEL_XZ100_HEIGHT16_1);
    }

    private void registerHeadAndDoorModels() {
        addModels(MaterialUtil.HEADS_GROUND, MODEL_GROUND_HEAD);
        addModels(MaterialUtil.HEADS_WALL, MODEL_WALL_HEAD);
        addModels(MaterialUtil.ALL_DOORS, MODEL_DOOR);
        addModels(MaterialUtil.ALL_TRAP_DOORS, MODEL_TRAP_DOOR);
        addModels(Collections.singleton(Material.CHORUS_PLANT), MODEL_CHORUS_PLANT);
        addModels(BridgeMaterial.getAllBlocks("lantern", "soul_lantern"), MODEL_LANTERN);
        addModels(BridgeMaterial.getAllBlocks("piston", "sticky_piston", "piston_base", "piston_sticky_base"), MODEL_PISTON);
        addModel(BridgeMaterial.PISTON_HEAD, MODEL_PISTON_HEAD);
        addModel(Material.SNOW, MODEL_SNOW);
    }

    private void registerLiquidAndRailModels() {
        addModels(MaterialUtil.WATER, MODEL_LEVELLED);
        addModels(MaterialUtil.LAVA, MODEL_LEVELLED);
        addModels(MaterialUtil.WATER_PLANTS, MODEL_WATER_PLANTS);
        addModels(MaterialUtil.RAILS, MODEL_RAIL);
    }

    private void registerMiscBlocks() {
        Material mt = BridgeMaterial.getBlock("lectern");
        if (mt != null) addModel(mt, MODEL_LECTERN);

        mt = BridgeMaterial.getBlock("bamboo");
        if (mt != null) addModel(mt, MODEL_BAMBOO);

        mt = BridgeMaterial.getBlock("bell");
        if (mt != null) addModel(mt, MODEL_BELL);

        mt = BridgeMaterial.getBlock("composter");
        if (mt != null) addModel(mt, MODEL_COMPOSTER);

        mt = BridgeMaterial.getBlock("honey_block");
        if (mt != null) addModel(mt, MODEL_HONEY_BLOCK);

        mt = BridgeMaterial.getBlock("big_dripleaf");
        if (mt != null) addModel(mt, MODEL_DRIP_LEAF);

        mt = BridgeMaterial.getBlock("grindstone");
        if (mt != null) addModel(mt, MODEL_GRIND_STONE);

        mt = BridgeMaterial.getBlock("sculk_shrieker");
        if (mt != null) addModel(mt, MODEL_SCULK_SHRIEKER);

        mt = BridgeMaterial.getBlock("mud");
        if (mt != null) addModel(mt, MODEL_XZ100_HEIGHT8_7);

        mt = BridgeMaterial.getBlock("heavy_core");
        if (mt != null) addModel(mt, MODEL_GROUND_HEAD);
    }

    private void registerFlagBasedModels() {
        for (final Material mat : Material.values()) {
            final long flags = BlockFlags.getBlockFlags(mat);
            if (BlockFlags.hasAnyFlag(flags, BlockFlags.F_STAIRS)) {
                addModel(mat, MODEL_STAIRS);
            }
            if (BlockFlags.hasAnyFlag(flags, BlockFlags.F_THICK_FENCE)) {
                if (BlockFlags.hasAnyFlag(flags, BlockFlags.F_PASSABLE_X4)) {
                    addModel(mat, MODEL_GATE);
                } else {
                    addModel(mat, MODEL_THICK_FENCE);
                }
            }
        }
    }

    private Object getHandle(Player player) {
        // Assumes the Player instance is a CraftPlayer.
        if (this.reflectLivingEntity == null || this.reflectLivingEntity.obcGetHandle == null) {
            return null;
        }
        Object handle = ReflectionUtil.invokeMethodNoArgs(this.reflectLivingEntity.obcGetHandle, player);
        return handle;
    }

    private boolean canDealFallDamage() {
        return this.reflectLivingEntity != null && this.reflectLivingEntity.nmsDamageEntity != null 
               && (this.reflectDamageSource.nmsFALL != null || this.reflectDamageSources != null);
    }

    @Override
    public AlmostBoolean dealFallDamageFiresAnEvent() {
        return canDealFallDamage() ? AlmostBoolean.YES : AlmostBoolean.NO;
    }

    @Override
    public void dealFallDamage(Player player, double damage) {
        if (canDealFallDamage()) {
            Object handle = getHandle(player);
            if (handle != null) {
                // Direct accessing DamageSource
                Object damageSource = this.reflectDamageSource.nmsFALL;
                if (damageSource == null) {
                    // Fail, second attempt
                    damageSource = this.reflectDamageSources.getDamageSource(handle);
                    if (damageSource == null) {
                        // Fail, deal generic damage
                        BridgeHealth.damage(player, damage);
                        // Disable class to prevent continuous damage
                        reflectDamageSources = null;
                        return;
                    }
                }
                ReflectionUtil.invokeMethod(this.reflectLivingEntity.nmsDamageEntity, handle, damageSource, (float) damage);
            }
        } 
        else BridgeHealth.damage(player, damage);
    }

    @Override
    public boolean resetActiveItem(Player player) {
        if (this.reflectLivingEntity != null && this.reflectLivingEntity.nmsclearActiveItem != null) {
            Object handle = getHandle(player);
            if (handle != null) {
                ReflectionUtil.invokeMethodNoArgs(this.reflectLivingEntity.nmsclearActiveItem, handle);
                return true;
            }
        }
        return false;
    }
}
