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
 * Blocks for Minecraft 1.21.8 (Pale Garden update).
 * 
 * @author Assistant for NoCheatPlus
 */
public class BlocksMC1_21_8 implements BlockPropertiesSetup {
    
    public BlocksMC1_21_8() {
        // Verify some key blocks exist
        BlockInit.assertMaterialExists("PALE_OAK_LOG");
        BlockInit.assertMaterialExists("CREAKING_HEART");
        BlockInit.assertMaterialExists("RESIN_CLUMP");
    }
    
    @Override
    public void setupBlockProperties(WorldConfigProvider<?> worldConfigProvider) {
        // Minecraft 1.21.8 Pale Garden update blocks
        
        // Pale Garden plants - passable (instantly breakable)
        BlockProperties.setBlockProps("CACTUS_FLOWER", BlockProperties.instantType);
        BlockFlags.setBlockFlags("CACTUS_FLOWER", BlockFlags.F_IGN_PASSABLE);
        
        BlockProperties.setBlockProps("CLOSED_EYEBLOSSOM", BlockProperties.instantType);
        BlockFlags.setBlockFlags("CLOSED_EYEBLOSSOM", BlockFlags.F_IGN_PASSABLE);
        
        BlockProperties.setBlockProps("OPEN_EYEBLOSSOM", BlockProperties.instantType);
        BlockFlags.setBlockFlags("OPEN_EYEBLOSSOM", BlockFlags.F_IGN_PASSABLE);
        
        BlockProperties.setBlockProps("LEAF_LITTER", BlockProperties.instantType);
        BlockFlags.setBlockFlags("LEAF_LITTER", BlockFlags.F_IGN_PASSABLE);
        
        BlockProperties.setBlockProps("PALE_HANGING_MOSS", BlockProperties.instantType);
        BlockFlags.setBlockFlags("PALE_HANGING_MOSS", BlockFlags.F_IGN_PASSABLE);
        
        BlockProperties.setBlockProps("SHORT_DRY_GRASS", BlockProperties.instantType);
        BlockFlags.setBlockFlags("SHORT_DRY_GRASS", BlockFlags.F_IGN_PASSABLE);
        
        BlockProperties.setBlockProps("TALL_DRY_GRASS", BlockProperties.instantType);
        BlockFlags.setBlockFlags("TALL_DRY_GRASS", BlockFlags.F_IGN_PASSABLE);
        
        BlockProperties.setBlockProps("WILDFLOWERS", BlockProperties.instantType);
        BlockFlags.setBlockFlags("WILDFLOWERS", BlockFlags.F_IGN_PASSABLE);
        
        // Resin blocks - if not already defined in 1.21.4
        try {
            BlockProperties.setBlockProps("CHISELED_RESIN_BRICKS", 
                new BlockProperties.BlockProps(BlockProperties.woodPickaxe, 2.0f, true));
            BlockFlags.setBlockFlags("CHISELED_RESIN_BRICKS", BlockFlags.FULLY_SOLID_BOUNDS);
        } catch (Exception e) {
            // Already defined in BlocksMC1_21_4, skip
        }
        
        try {
            BlockProperties.setBlockProps("CREAKING_HEART", 
                new BlockProperties.BlockProps(BlockProperties.woodAxe, 2.0f, true));
            BlockFlags.setBlockFlags("CREAKING_HEART", BlockFlags.SOLID_GROUND);
        } catch (Exception e) {
            // Already defined in BlocksMC1_21_4, skip
        }
        
        BlockProperties.setBlockProps("RESIN_CLUMP", BlockProperties.instantType);
        BlockFlags.setBlockFlags("RESIN_CLUMP", BlockFlags.F_IGN_PASSABLE);
        
        // Resin brick variants - if not already defined
        try {
            BlockProperties.setBlockProps("RESIN_BRICK_SLAB", 
                new BlockProperties.BlockProps(BlockProperties.woodPickaxe, 2.0f, true));
            BlockFlags.setBlockFlags("RESIN_BRICK_SLAB", BlockFlags.SOLID_GROUND | BlockFlags.F_XZ100);
            
            BlockProperties.setBlockProps("RESIN_BRICK_STAIRS", 
                new BlockProperties.BlockProps(BlockProperties.woodPickaxe, 2.0f, true));
            BlockFlags.setBlockFlags("RESIN_BRICK_STAIRS", BlockFlags.F_STAIRS | BlockFlags.SOLID_GROUND);
            
            BlockProperties.setBlockProps("RESIN_BRICK_WALL", 
                new BlockProperties.BlockProps(BlockProperties.woodPickaxe, 2.0f, true));
            BlockFlags.setBlockFlags("RESIN_BRICK_WALL", BlockFlags.SOLID_GROUND | BlockFlags.F_VARIABLE);
            
            BlockProperties.setBlockProps("RESIN_BRICKS", 
                new BlockProperties.BlockProps(BlockProperties.woodPickaxe, 2.0f, true));
            BlockFlags.setBlockFlags("RESIN_BRICKS", BlockFlags.FULLY_SOLID_BOUNDS);
            
            BlockProperties.setBlockProps("RESIN_BLOCK", 
                new BlockProperties.BlockProps(BlockProperties.woodPickaxe, 2.0f, true));
            BlockFlags.setBlockFlags("RESIN_BLOCK", BlockFlags.SOLID_GROUND);
        } catch (Exception e) {
            // Already defined in BlocksMC1_21_4, skip
        }
        
        // Pale Oak wood types
        BlockProperties.setBlockProps("PALE_OAK_BUTTON", 
            new BlockProperties.BlockProps(BlockProperties.noTool, 0.5f, true));
        BlockFlags.setBlockFlags("PALE_OAK_BUTTON", BlockFlags.F_IGN_PASSABLE);
        
        BlockProperties.setBlockProps("PALE_OAK_DOOR", 
            new BlockProperties.BlockProps(BlockProperties.woodAxe, 3.0f, true));
        BlockFlags.setBlockFlags("PALE_OAK_DOOR", BlockFlags.SOLID_GROUND | BlockFlags.F_VARIABLE);
        
        BlockProperties.setBlockProps("PALE_OAK_FENCE", 
            new BlockProperties.BlockProps(BlockProperties.woodAxe, 2.0f, true));
        BlockFlags.setBlockFlags("PALE_OAK_FENCE", BlockFlags.SOLID_GROUND | BlockFlags.F_IGN_PASSABLE);
        
        BlockProperties.setBlockProps("PALE_OAK_FENCE_GATE", 
            new BlockProperties.BlockProps(BlockProperties.woodAxe, 2.0f, true));
        BlockFlags.setBlockFlags("PALE_OAK_FENCE_GATE", BlockFlags.SOLID_GROUND | BlockFlags.F_IGN_PASSABLE | BlockFlags.F_VARIABLE);
        
        BlockProperties.setBlockProps("PALE_OAK_HANGING_SIGN", 
            new BlockProperties.BlockProps(BlockProperties.woodAxe, 1.0f, true));
        BlockFlags.setBlockFlags("PALE_OAK_HANGING_SIGN", BlockFlags.SOLID_GROUND | BlockFlags.F_IGN_PASSABLE);
        
        BlockProperties.setBlockProps("PALE_OAK_LEAVES", 
            new BlockProperties.BlockProps(BlockProperties.noTool, 0.2f, true));
        BlockFlags.setBlockFlags("PALE_OAK_LEAVES", BlockFlags.SOLID_GROUND | BlockFlags.F_IGN_PASSABLE | BlockFlags.F_LEAVES);
        
        BlockProperties.setBlockProps("PALE_OAK_LOG", 
            new BlockProperties.BlockProps(BlockProperties.woodAxe, 2.0f, true));
        BlockFlags.setBlockFlags("PALE_OAK_LOG", BlockFlags.SOLID_GROUND);
        
        BlockProperties.setBlockProps("PALE_OAK_PRESSURE_PLATE", 
            new BlockProperties.BlockProps(BlockProperties.woodAxe, 0.5f, true));
        BlockFlags.setBlockFlags("PALE_OAK_PRESSURE_PLATE", BlockFlags.F_IGN_PASSABLE);
        
        BlockProperties.setBlockProps("PALE_OAK_SIGN", 
            new BlockProperties.BlockProps(BlockProperties.woodAxe, 1.0f, true));
        BlockFlags.setBlockFlags("PALE_OAK_SIGN", BlockFlags.SOLID_GROUND | BlockFlags.F_IGN_PASSABLE);
        
        BlockProperties.setBlockProps("PALE_OAK_SLAB", 
            new BlockProperties.BlockProps(BlockProperties.woodAxe, 2.0f, true));
        BlockFlags.setBlockFlags("PALE_OAK_SLAB", BlockFlags.SOLID_GROUND | BlockFlags.F_XZ100);
        
        BlockProperties.setBlockProps("PALE_OAK_STAIRS", 
            new BlockProperties.BlockProps(BlockProperties.woodAxe, 2.0f, true));
        BlockFlags.setBlockFlags("PALE_OAK_STAIRS", BlockFlags.F_STAIRS | BlockFlags.SOLID_GROUND);
        
        BlockProperties.setBlockProps("PALE_OAK_TRAPDOOR", 
            new BlockProperties.BlockProps(BlockProperties.woodAxe, 3.0f, true));
        BlockFlags.setBlockFlags("PALE_OAK_TRAPDOOR", BlockFlags.SOLID_GROUND | BlockFlags.F_VARIABLE);
        
        BlockProperties.setBlockProps("PALE_OAK_WALL_HANGING_SIGN", 
            new BlockProperties.BlockProps(BlockProperties.woodAxe, 1.0f, true));
        BlockFlags.setBlockFlags("PALE_OAK_WALL_HANGING_SIGN", BlockFlags.SOLID_GROUND | BlockFlags.F_IGN_PASSABLE);
        
        BlockProperties.setBlockProps("PALE_OAK_WALL_SIGN", 
            new BlockProperties.BlockProps(BlockProperties.woodAxe, 1.0f, true));
        BlockFlags.setBlockFlags("PALE_OAK_WALL_SIGN", BlockFlags.SOLID_GROUND | BlockFlags.F_IGN_PASSABLE);
        
        BlockProperties.setBlockProps("PALE_OAK_WOOD", 
            new BlockProperties.BlockProps(BlockProperties.woodAxe, 2.0f, true));
        BlockFlags.setBlockFlags("PALE_OAK_WOOD", BlockFlags.SOLID_GROUND);
        
        // Moss block
        BlockProperties.setBlockProps("PALE_MOSS_BLOCK", 
            new BlockProperties.BlockProps(BlockProperties.noTool, 0.1f, true));
        BlockFlags.setBlockFlags("PALE_MOSS_BLOCK", BlockFlags.SOLID_GROUND);
        
        // Test blocks (if present)
        try {
            BlockProperties.setBlockProps("TEST_BLOCK", 
                new BlockProperties.BlockProps(BlockProperties.noTool, 1.0f, true));
            BlockFlags.setBlockFlags("TEST_BLOCK", BlockFlags.SOLID_GROUND);
        } catch (Exception e) {
            // Test block may not exist in production
        }
        
        try {
            BlockProperties.setBlockProps("TEST_INSTANCE_BLOCK", 
                new BlockProperties.BlockProps(BlockProperties.noTool, 1.0f, true));
            BlockFlags.setBlockFlags("TEST_INSTANCE_BLOCK", BlockFlags.SOLID_GROUND);
        } catch (Exception e) {
            // Test block may not exist in production
        }
        
        ConfigFile config = ConfigManager.getConfigFile();
        if (config.getBoolean(ConfPaths.BLOCKBREAK_DEBUG, config.getBoolean(ConfPaths.CHECKS_DEBUG, false)))
            StaticLog.logInfo("Added block-info for Minecraft 1.21.8 Pale Garden blocks.");
    }
}