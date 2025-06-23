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

import fr.neatmonster.nocheatplus.compat.blocks.BlockPropertiesSetup;
import fr.neatmonster.nocheatplus.compat.blocks.init.BlockInit;
import fr.neatmonster.nocheatplus.config.ConfPaths;
import fr.neatmonster.nocheatplus.config.ConfigFile;
import fr.neatmonster.nocheatplus.config.ConfigManager;
import fr.neatmonster.nocheatplus.config.WorldConfigProvider;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import fr.neatmonster.nocheatplus.utilities.map.BlockFlags;


/**
 * Blocks for Minecraft 1.21.5.
 * 
 * @author Claude for NCP (Based author addition, Claude 3.7 -zim)
 */
public class BlocksMC1_21_5 implements BlockPropertiesSetup {
    
    public BlocksMC1_21_5() {
        BlockInit.assertMaterialExists("CACTUS_FLOWER");
    }
    
    @Override
    public void setupBlockProperties(WorldConfigProvider<?> worldConfigProvider) {
        // Minecraft 1.21.5 additions
        BlockInit.setInstantPassable("CACTUS_FLOWER");
        BlockInit.setInstantPassable("LEAF_LITTER");
        BlockInit.setInstantPassable("SHORT_DRY_GRASS");
        BlockInit.setInstantPassable("TALL_DRY_GRASS");
        BlockInit.setInstantPassable("WILDFLOWERS");

        // Test blocks
        BlockFlags.setBlockFlags("TEST_BLOCK", BlockFlags.SOLID_GROUND);
        BlockFlags.setBlockFlags("TEST_INSTANCE_BLOCK", BlockFlags.SOLID_GROUND);

        ConfigFile config = ConfigManager.getConfigFile();
        if (config.getBoolean(ConfPaths.BLOCKBREAK_DEBUG, config.getBoolean(ConfPaths.CHECKS_DEBUG, false)))
        StaticLog.logInfo("Added block-info for Minecraft 1.21.5 blocks.");
    }
}
