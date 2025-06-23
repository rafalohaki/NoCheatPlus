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
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;

/**
 * Blocks for Minecraft 1.21.4.
 * 
 * @author Claude for NCP (Based author addition, Claude 3.7 -zim)
 */
public class BlocksMC1_21_4 implements BlockPropertiesSetup {
    
    public BlocksMC1_21_4() {
        BlockInit.assertMaterialExists("PALE_OAK_LOG");
    }
    
    @SuppressWarnings("deprecation")
    @Override
    public void setupBlockProperties(WorldConfigProvider<?> worldConfigProvider) {
        // Pale Oak variants (similar to normal oak)
        // According to the wiki, pale oak has the same hardness and breaking properties as regular oak
        BlockInit.setAs("PALE_OAK_LOG", "OAK_LOG");
        BlockInit.setAs("PALE_OAK_WOOD", "OAK_WOOD");
        BlockInit.setAs("PALE_OAK_PLANKS", "OAK_PLANKS");
        BlockInit.setAs("PALE_OAK_SAPLING", "OAK_SAPLING");
        
        // Special flag handling for leaves - match oak leaves exactly
        BlockInit.setPropsAs("PALE_OAK_LEAVES", "OAK_LEAVES");
        BlockFlags.setFlagsAs("PALE_OAK_LEAVES", "OAK_LEAVES");
        
        // Slabs and stairs
        BlockInit.setAs("PALE_OAK_SLAB", "OAK_SLAB");
        BlockInit.setAs("PALE_OAK_STAIRS", "OAK_STAIRS");
        
        // Set props for buttons and pressure plates
        BlockInit.setAs("PALE_OAK_BUTTON", "OAK_BUTTON");
        BlockInit.setAs("PALE_OAK_PRESSURE_PLATE", "OAK_PRESSURE_PLATE");
        
        // Door, trapdoor, fence and fence gate
        BlockInit.setAs("PALE_OAK_DOOR", "OAK_DOOR");
        BlockInit.setAs("PALE_OAK_TRAPDOOR", "OAK_TRAPDOOR");
        BlockInit.setAs("PALE_OAK_FENCE", "OAK_FENCE");
        BlockInit.setAs("PALE_OAK_FENCE_GATE", "OAK_FENCE_GATE");
        
        // Signs handling
        BlockInit.setAs("PALE_OAK_SIGN", "OAK_SIGN");
        BlockInit.setAs("PALE_OAK_HANGING_SIGN", "OAK_HANGING_SIGN");
        BlockInit.setAs("PALE_OAK_WALL_SIGN", "OAK_WALL_SIGN");
        BlockInit.setAs("PALE_OAK_WALL_HANGING_SIGN", "OAK_WALL_HANGING_SIGN");
                
        // Moss and Plants - set instantPassable for non-solid blocks
        BlockInit.setInstantPassable("OPEN_EYEBLOSSOM");
        BlockInit.setInstantPassable("CLOSED_EYEBLOSSOM");
        BlockInit.setInstantPassable("PALE_HANGING_MOSS");
        
        // Moss blocks - similar to regular moss
        BlockInit.setAs("PALE_MOSS_BLOCK", "MOSS_BLOCK");
        BlockInit.setAs("PALE_MOSS_CARPET", "MOSS_CARPET");
        
        // Resin-based blocks
        // Resin clump has 0 hardness and is instantly breakable
        BlockProperties.setBlockProps("RESIN_CLUMP", BlockProperties.instantType);
        BlockFlags.setBlockFlags("RESIN_CLUMP", BlockFlags.F_IGN_PASSABLE);
        
        // Block of resin is similar to honey block (sticky properties)
        BlockProperties.setBlockProps("RESIN_BLOCK", new BlockProperties.BlockProps(BlockProperties.noTool, 0.5f));
        BlockFlags.setBlockFlags("RESIN_BLOCK", BlockFlags.SOLID_GROUND | BlockFlags.F_STICKY);
        
        // Resin brick variants (hardness similar to other brick types)
        BlockProperties.setBlockProps("RESIN_BRICKS", 
            new BlockProperties.BlockProps(BlockProperties.woodPickaxe, 2.0f, true));
        BlockFlags.setBlockFlags("RESIN_BRICKS", BlockFlags.FULLY_SOLID_BOUNDS);
        
        BlockProperties.setBlockProps("RESIN_BRICK_STAIRS", 
            new BlockProperties.BlockProps(BlockProperties.woodPickaxe, 2.0f, true));
        BlockFlags.setBlockFlags("RESIN_BRICK_STAIRS", BlockFlags.F_STAIRS | BlockFlags.SOLID_GROUND);
        
        BlockProperties.setBlockProps("RESIN_BRICK_SLAB", 
            new BlockProperties.BlockProps(BlockProperties.woodPickaxe, 2.0f, true));
        BlockFlags.setBlockFlags("RESIN_BRICK_SLAB", BlockFlags.SOLID_GROUND | BlockFlags.F_XZ100);
        
        BlockProperties.setBlockProps("RESIN_BRICK_WALL", 
            new BlockProperties.BlockProps(BlockProperties.woodPickaxe, 2.0f, true));
        BlockFlags.setBlockFlags("RESIN_BRICK_WALL", BlockFlags.SOLID_GROUND | BlockFlags.F_VARIABLE);
        
        BlockProperties.setBlockProps("CHISELED_RESIN_BRICKS", 
            new BlockProperties.BlockProps(BlockProperties.woodPickaxe, 2.0f, true));
        BlockFlags.setBlockFlags("CHISELED_RESIN_BRICKS", BlockFlags.FULLY_SOLID_BOUNDS);
        
        // Special case - Creaking Heart
        // A solid block that forms the center of pale oaks
        BlockProperties.setBlockProps("CREAKING_HEART", new BlockProperties.BlockProps(BlockProperties.woodAxe, 2.0f));
        BlockFlags.setBlockFlags("CREAKING_HEART", BlockFlags.SOLID_GROUND);
        
        ConfigFile config = ConfigManager.getConfigFile();
        if (config.getBoolean(ConfPaths.BLOCKBREAK_DEBUG, config.getBoolean(ConfPaths.CHECKS_DEBUG, false)))
        StaticLog.logInfo("Added block-info for Minecraft 1.21.4 blocks.");
    }
}
