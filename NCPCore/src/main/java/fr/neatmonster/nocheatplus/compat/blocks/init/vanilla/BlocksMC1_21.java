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
package fr.neatmonster.nocheatplus.compat.blocks.init.vanilla;

import fr.neatmonster.nocheatplus.compat.BridgeMaterial;
import fr.neatmonster.nocheatplus.compat.blocks.BlockPropertiesSetup;
import fr.neatmonster.nocheatplus.compat.blocks.init.BlockInit;
import fr.neatmonster.nocheatplus.compat.versions.ServerVersion;
import fr.neatmonster.nocheatplus.config.ConfPaths;
import fr.neatmonster.nocheatplus.config.ConfigFile;
import fr.neatmonster.nocheatplus.config.ConfigManager;
import fr.neatmonster.nocheatplus.config.WorldConfigProvider;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import org.bukkit.Material;

public class BlocksMC1_21 implements BlockPropertiesSetup {

    @Override
    public void setupBlockProperties(WorldConfigProvider<?> worldConfigProvider) {
        // Custom or modded blocks the plugin should recognize.
        for (String name : new String[]{
                "CACTUS_FLOWER",
                "LEAF_LITTER",
                "SHORT_DRY_GRASS",
                "TALL_DRY_GRASS",
                "WILDFLOWERS"}) {
            if (BridgeMaterial.has(name)) {
                BlockInit.setInstantPassable(name);
            }
        }

        if (BridgeMaterial.has("TEST_BLOCK")) {
            BlockInit.setAs("TEST_BLOCK", Material.STONE);
        }
        if (BridgeMaterial.has("TEST_INSTANCE_BLOCK")) {
            BlockInit.setAs("TEST_INSTANCE_BLOCK", Material.STONE);
        }

        if (ServerVersion.compareMinecraftVersion("1.21") >= 0) {
            BlockInit.setAsIfExists("RESIN_BLOCK", Material.ANDESITE);
            BlockInit.setAsIfExists("RESIN_BRICKS", Material.ANDESITE);
            BlockInit.setAsIfExists("RESIN_BRICK_SLAB", Material.ANDESITE_SLAB);
            BlockInit.setAsIfExists("RESIN_BRICK_STAIRS", Material.ANDESITE_STAIRS);
            BlockInit.setAsIfExists("RESIN_BRICK_WALL", Material.ANDESITE_WALL);
            BlockInit.setAsIfExists("CHISELED_RESIN_BRICKS", Material.ANDESITE);

            BlockInit.setAsIfExists("CREAKING_HEART", Material.ANDESITE);

            for (String name : new String[]{
                    "RESIN_CLUMP",
                    "CLOSED_EYEBLOSSOM",
                    "OPEN_EYEBLOSSOM",
                    "PALE_HANGING_MOSS"}) {
                if (BridgeMaterial.has(name)) {
                    BlockInit.setInstantPassable(name);
                }
            }

            BlockInit.setAsIfExists("PALE_OAK_LOG", Material.OAK_LOG);
            BlockInit.setAsIfExists("PALE_OAK_WOOD", Material.OAK_WOOD);
            BlockInit.setAsIfExists("PALE_OAK_PLANKS", Material.OAK_PLANKS);
            BlockInit.setAsIfExists("PALE_OAK_SLAB", Material.OAK_SLAB);
            BlockInit.setAsIfExists("PALE_OAK_STAIRS", Material.OAK_STAIRS);
            BlockInit.setAsIfExists("PALE_OAK_FENCE", Material.OAK_FENCE);
            BlockInit.setAsIfExists("PALE_OAK_FENCE_GATE", Material.OAK_FENCE_GATE);
            BlockInit.setAsIfExists("PALE_OAK_DOOR", Material.OAK_DOOR);
            BlockInit.setAsIfExists("PALE_OAK_TRAPDOOR", Material.OAK_TRAPDOOR);
            BlockInit.setAsIfExists("PALE_OAK_HANGING_SIGN", Material.OAK_HANGING_SIGN);
            BlockInit.setAsIfExists("PALE_MOSS_BLOCK", Material.MOSS_BLOCK);
            BlockInit.setAsIfExists("PALE_OAK_LEAVES", Material.OAK_LEAVES);
            BlockInit.setAsIfExists("PALE_OAK_SIGN", Material.OAK_SIGN);
            BlockInit.setAsIfExists("PALE_OAK_WALL_SIGN", Material.OAK_WALL_SIGN);
            BlockInit.setAsIfExists("PALE_OAK_WALL_HANGING_SIGN", Material.OAK_WALL_HANGING_SIGN);

            if (BridgeMaterial.has("PALE_OAK_PRESSURE_PLATE")) {
                BlockInit.setInstantPassable("PALE_OAK_PRESSURE_PLATE");
            }
            if (BridgeMaterial.has("PALE_OAK_BUTTON")) {
                BlockInit.setInstantPassable("PALE_OAK_BUTTON");
            }
        }

        ConfigFile config = ConfigManager.getConfigFile();
        if (config.getBoolean(ConfPaths.BLOCKBREAK_DEBUG, config.getBoolean(ConfPaths.CHECKS_DEBUG, false))) {
            StaticLog.logInfo("Added block-info for Minecraft 1.21 blocks");
        }
    }
}
