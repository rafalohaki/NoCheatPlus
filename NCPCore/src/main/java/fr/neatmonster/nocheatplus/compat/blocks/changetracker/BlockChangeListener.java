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
package fr.neatmonster.nocheatplus.compat.blocks.changetracker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected.Half;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Door;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;
import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.compat.BridgeMaterial;
import fr.neatmonster.nocheatplus.compat.versions.ServerVersion;
import fr.neatmonster.nocheatplus.components.NoCheatPlusAPI;
import fr.neatmonster.nocheatplus.components.registry.order.RegistrationOrder.RegisterMethodWithOrder;
import fr.neatmonster.nocheatplus.event.mini.MiniListener;
import fr.neatmonster.nocheatplus.logging.Streams;
import fr.neatmonster.nocheatplus.utilities.ReflectionUtil;
import fr.neatmonster.nocheatplus.utilities.map.BlockFlags;

public class BlockChangeListener implements Listener {

    public final boolean is1_13 = ServerVersion.compareMinecraftVersion("1.13") >= 0;
    public final boolean is1_9 = ServerVersion.compareMinecraftVersion("1.9") >= 0;

    /** These blocks certainly can't be pushed nor pulled. */
    public static long F_MOVABLE_IGNORE = BlockFlags.F_LIQUID;
    /** These blocks might be pushed or pulled. */
    public static long F_MOVABLE = BlockFlags.F_GROUND | BlockFlags.F_SOLID;

    private final BlockChangeTracker tracker;
    private final boolean retractHasBlocks;
    private boolean enabled = true;
    
    /** Plugin instance for scheduling */
    private final Plugin plugin;

    /** Default tag for listeners. */
    private final String defaultTag = "system.nocheatplus.blockchangetracker";

    /** Modern tool types - replacement for deprecated ToolType */
    public enum ModernToolType {
        HOE, SPADE, AXE, PICKAXE, SWORD, SHEARS
    }

    /** Properties by dirt block type - updated to use modern enum */
    protected final Map<Material, ModernToolType> dirtblocks = init();

    /**
     * NOTE: Using MiniListenerWithOrder (and @Override before @EventHandler)
     * would make the registry attempt to register with Bukkit for 'Object'.
     */
    private final MiniListener<?>[] miniListeners = new MiniListener<?>[] {
        new MiniListener<BlockRedstoneEvent>() {
            @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
            @RegisterMethodWithOrder(tag = defaultTag)
            @Override
            public void onEvent(BlockRedstoneEvent event) {
                if (enabled) {
                    onBlockRedstone(event);
                }
            }
        },
        new MiniListener<EntityChangeBlockEvent>() {
            @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
            @RegisterMethodWithOrder(tag = defaultTag)
            @Override
            public void onEvent(EntityChangeBlockEvent event) {
                if (enabled) {
                    onEntityChangeBlock(event);
                }
            }
        },
        new MiniListener<BlockPistonExtendEvent>() {
            @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
            @RegisterMethodWithOrder(tag = defaultTag)
            @Override
            public void onEvent(BlockPistonExtendEvent event) {
                if (enabled) {
                    onPistonExtend(event);
                }
            }
        },
        new MiniListener<BlockPistonRetractEvent>() {
            @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
            @RegisterMethodWithOrder(tag = defaultTag)
            @Override
            public void onEvent(BlockPistonRetractEvent event) {
                if (enabled) {
                    onPistonRetract(event);
                }
            }
        },
        new MiniListener<PlayerInteractEvent>() {
            // Include cancelled events, due to the use-block part.
            @EventHandler(ignoreCancelled = false, priority = EventPriority.MONITOR)
            @RegisterMethodWithOrder(tag = defaultTag)
            @Override
            public void onEvent(PlayerInteractEvent event) {
                if (enabled) {
                    onPlayerInteract(event);
                }
            }
        },
        new MiniListener<BlockFormEvent>() {
            @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
            @RegisterMethodWithOrder(tag = defaultTag)
            @Override
            public void onEvent(BlockFormEvent event) {
                if (enabled) {
                    onBlockForm(event);
                }
            }
        }
    };

    /**
     * NAPRAWIONY KONSTRUKTOR - przyjmuje instancję pluginu
     */
    public BlockChangeListener(final BlockChangeTracker tracker, final Plugin plugin) {
        this.tracker = tracker;
        this.plugin = plugin;
        if (ReflectionUtil.getMethodNoArgs(BlockPistonRetractEvent.class, "getBlocks") == null) {
            retractHasBlocks = false;
            NCPAPIProvider.getNoCheatPlusAPI().getLogManager().info(Streams.STATUS, "Assume legacy piston behavior.");
        }
        else {
            retractHasBlocks = true;
        }
    }
    
    /**
     * ALTERNATYWNY KONSTRUKTOR - próbuje uzyskać plugin z Bukkit
     */
    public BlockChangeListener(final BlockChangeTracker tracker) {
        this.tracker = tracker;
        // Próba uzyskania pluginu NoCheatPlus z Bukkit PluginManager
        Plugin foundPlugin = Bukkit.getPluginManager().getPlugin("NoCheatPlus");
        if (foundPlugin == null) {
            foundPlugin = Bukkit.getPluginManager().getPlugin("NoCheat+");
        }
        this.plugin = foundPlugin;
        
        if (ReflectionUtil.getMethodNoArgs(BlockPistonRetractEvent.class, "getBlocks") == null) {
            retractHasBlocks = false;
            NCPAPIProvider.getNoCheatPlusAPI().getLogManager().info(Streams.STATUS, "Assume legacy piston behavior.");
        }
        else {
            retractHasBlocks = true;
        }
    }

    /**
     * Initialize modern tool mappings - replacement for deprecated ToolType usage
     */
    private Map<Material, ModernToolType> init() {
        Map<Material, ModernToolType> blocks = new HashMap<Material, ModernToolType>();
        blocks.put(BridgeMaterial.GRASS_BLOCK, ModernToolType.HOE);
        blocks.put(Material.DIRT, ModernToolType.HOE);
        if (is1_13) {
            blocks.put(Material.COARSE_DIRT, ModernToolType.SPADE);
            blocks.put(Material.PODZOL, ModernToolType.SPADE);
        }
        if (ServerVersion.compareMinecraftVersion("1.17") >= 0) {
            blocks.put(Material.ROOTED_DIRT, ModernToolType.SPADE);
        }
        return blocks;
    }

    /**
     * Register actual listener(s).
     */
    public void register() {
        final NoCheatPlusAPI api = NCPAPIProvider.getNoCheatPlusAPI();
        for (final MiniListener<?> listener : miniListeners) {
            api.addComponent(listener);
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Modern approach to get block direction using BlockData API
     */
    private BlockFace getDirection(final Block pistonBlock) {
        final BlockData data = pistonBlock.getBlockData();
        if (data instanceof org.bukkit.block.data.Directional) {
            org.bukkit.block.data.Directional directional = (org.bukkit.block.data.Directional) data;
            return directional.getFacing();
        }
        return null;
    }

    /**
     * Get the direction, in which blocks are or would be moved (towards the piston).
     * Enhanced for modern piston mechanics and multi-directional support.
     * 
     * @param pistonBlock
     * @param eventDirection
     * @return
     */
    private BlockFace getRetractDirection(final Block pistonBlock, final BlockFace eventDirection) {
        final BlockFace pistonDirection = getDirection(pistonBlock);
        if (pistonDirection == null) {
            return eventDirection;
        }
        else {
            return eventDirection.getOppositeFace();
        }
    }

    /**
     * NAPRAWIONA METODA - Asynchroniczne przetwarzanie pistonów EXTEND
     * Rozwiązuje deadlock z Watchdog Thread
     */
    private void onPistonExtend(final BlockPistonExtendEvent event) {
        if (plugin == null) {
            // Fallback - po prostu nie rób nic jeśli nie mamy pluginu (bezpieczniej niż crash)
            NCPAPIProvider.getNoCheatPlusAPI().getLogManager().warning(Streams.STATUS,
                "BlockChangeListener: Plugin instance not available, skipping piston extend tracking");
            return;
        }
        
        // Wykonaj asynchronicznie aby uniknąć deadlock
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                final BlockFace direction = event.getDirection();
                final Block pistonBlock = event.getBlock();
                final List<Block> blocks = event.getBlocks();
                
                // Sprawdzenia null-safety
                if (pistonBlock != null && pistonBlock.getWorld() != null && direction != null) {
                    final Block targetBlock = pistonBlock.getRelative(direction);
                    
                    // Powrót do głównego wątku dla bezpiecznego wywołania API
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        try {
                            if (targetBlock != null && targetBlock.getWorld() != null) {
                                tracker.addPistonBlocks(targetBlock, direction, blocks);
                            }
                        } catch (Exception e) {
                            NCPAPIProvider.getNoCheatPlusAPI().getLogManager().warning(Streams.STATUS,
                                "Error in sync piston extend handler: " + e.getMessage());
                        }
                    });
                }
            } catch (Exception e) {
                NCPAPIProvider.getNoCheatPlusAPI().getLogManager().warning(Streams.STATUS,
                    "Error in async piston extend handler: " + e.getMessage());
            }
        });
    }

    /**
     * NAPRAWIONA METODA - Asynchroniczne przetwarzanie pistonów RETRACT
     * Rozwiązuje deadlock z Watchdog Thread
     */
    private void onPistonRetract(final BlockPistonRetractEvent event) {
        if (plugin == null) {
            // Fallback - po prostu nie rób nic jeśli nie mamy pluginu
            NCPAPIProvider.getNoCheatPlusAPI().getLogManager().warning(Streams.STATUS,
                "BlockChangeListener: Plugin instance not available, skipping piston retract tracking");
            return;
        }
        
        // Wykonaj asynchronicznie aby uniknąć deadlock
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                final List<Block> blocks;
                if (retractHasBlocks) {
                    blocks = event.getBlocks();
                }
                else {
                    @SuppressWarnings("deprecation")
                    final Location retLoc = event.getRetractLocation();
                    if (retLoc == null) {
                        blocks = null;
                    }
                    else {
                        // Cache block reference to avoid repeated calls
                        final Block retBlock = retLoc.getBlock();
                        if (retBlock == null || retLoc.getWorld() == null) {
                            blocks = null;
                        } else {
                            // Use cached material type instead of repeated getType() calls
                            final Material blockType = retBlock.getType();
                            final long flags = BlockFlags.getBlockFlags(blockType);
                            if ((flags & F_MOVABLE_IGNORE) == 0L && (flags & F_MOVABLE) != 0L) {
                                blocks = new ArrayList<Block>(1);
                                blocks.add(retBlock);
                            }
                            else {
                                blocks = null;
                            }
                        }
                    }
                }

                // Cache piston block reference
                final Block pistonBlock = event.getBlock();
                if (pistonBlock != null && pistonBlock.getWorld() != null) {
                    final BlockFace direction = getRetractDirection(pistonBlock, event.getDirection());
                    final Block targetBlock = pistonBlock.getRelative(direction.getOppositeFace());
                    
                    // Powrót do głównego wątku dla bezpiecznego wywołania API
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        try {
                            if (targetBlock != null && targetBlock.getWorld() != null) {
                                tracker.addPistonBlocks(targetBlock, direction, blocks);
                            }
                        } catch (Exception e) {
                            NCPAPIProvider.getNoCheatPlusAPI().getLogManager().warning(Streams.STATUS,
                                "Error in sync piston retract handler: " + e.getMessage());
                        }
                    });
                }
            } catch (Exception e) {
                NCPAPIProvider.getNoCheatPlusAPI().getLogManager().warning(Streams.STATUS,
                    "Error in async piston retract handler: " + e.getMessage());
            }
        });
    }

    private void onBlockRedstone(final BlockRedstoneEvent event) {
        final int oldCurrent = event.getOldCurrent();
        final int newCurrent = event.getNewCurrent();
        if (oldCurrent == newCurrent || oldCurrent > 0 && newCurrent > 0) {
            return;
        }
        
        final Block block = event.getBlock();
        if (block == null 
            || (BlockFlags.getBlockFlags(block.getType()) & BlockFlags.F_VARIABLE_REDSTONE) == 0) {
            return;
        }
        addRedstoneBlock(block);
    }

    private void addRedstoneBlock(final Block block) {
        addBlockWithAttachedPotential(block, BlockFlags.F_VARIABLE_REDSTONE);
    }

    private void onEntityChangeBlock(final EntityChangeBlockEvent event) {
        final Block block = event.getBlock();
        if (block != null) {
            tracker.addBlocks(block); // E.g. falling blocks like sand.
        }
    }

    private void onPlayerInteract(final PlayerInteractEvent event) {
        // Check preconditions.
        final org.bukkit.event.block.Action action = event.getAction();
        if (action == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            onRightClickBlock(event);
        }
        else if (!isEventCancelled(event)) { // Modern cancellation check
            if (action == org.bukkit.event.block.Action.PHYSICAL) {
                onInteractPhysical(event);
            }
        }
    }

    /**
     * Modern replacement for deprecated isCancelled() method
     */
    private boolean isEventCancelled(final PlayerInteractEvent event) {
        return event.useInteractedBlock() == Result.DENY;
    }

    private void onInteractPhysical(final PlayerInteractEvent event) {
        final Block block = event.getClickedBlock();
        if (block != null) {
            final Material type = block.getType();
            if (type == BridgeMaterial.FARMLAND) {
                tracker.addBlocks(block);
            }
        }
    }

    private void onRightClickBlock(final PlayerInteractEvent event) {
        final Result result = event.useInteractedBlock();
        if ((result == Result.ALLOW 
            || result == Result.DEFAULT && !isEventCancelled(event))) {
            final Block block = event.getClickedBlock();
            if (block != null) {
                final Material type = block.getType();
                // Modern dirt tool handling
                final ModernToolType blocktool = dirtblocks.get(type);
                if (blocktool != null) {
                    final ModernToolType itemTool = getModernToolType(event.getItem());
                    if (itemTool == ModernToolType.SPADE || blocktool == itemTool) {
                        tracker.addBlocks(block);
                    }
                }

                if ((BlockFlags.getBlockFlags(type) & BlockFlags.F_VARIABLE_USE) != 0L) {
                    addBlockWithAttachedPotential(block, BlockFlags.F_VARIABLE_USE);
                }
            }
        }
    }

    /**
     * Modern replacement for deprecated BlockProperties.getToolProps()
     */
    private ModernToolType getModernToolType(final org.bukkit.inventory.ItemStack item) {
        if (item == null) return null;
        
        final Material material = item.getType();
        final String name = material.name();
        
        if (name.contains("HOE")) return ModernToolType.HOE;
        if (name.contains("SHOVEL") || name.contains("SPADE")) return ModernToolType.SPADE;
        if (name.contains("AXE")) return ModernToolType.AXE;
        if (name.contains("PICKAXE")) return ModernToolType.PICKAXE;
        if (name.contains("SWORD")) return ModernToolType.SWORD;
        if (name.contains("SHEARS")) return ModernToolType.SHEARS;
        
        return null;
    }

    private void onBlockForm(final BlockFormEvent event) {
        final Block block = event.getBlock();
        if (block != null) {
            tracker.addBlocks(block);
        }
    }

    /**
     * Add a past state for this block, extending for the other block in case of
     * doors. This is for the case of interaction or redstone level change.
     * Updated to use modern BlockData API instead of deprecated MaterialData
     * 
     * @param block
     * @param relevantFlags
     */
    private void addBlockWithAttachedPotential(final Block block, final long relevantFlags) {
        final BlockData data = block.getBlockData();
        if (data instanceof Door) {
            Door door = (Door) data;
            final Block otherBlock = block.getRelative(door.getHalf() == Half.TOP ? BlockFace.DOWN : BlockFace.UP);
            
            // Enhanced door detection for modern door mechanics
            if (otherBlock != null // Top of the map / special case.
                && (BlockFlags.getBlockFlags(otherBlock.getType()) 
                    & relevantFlags) != 0) {
                tracker.addBlocks(block, otherBlock);
                return;
            }
        }
        
        // Only add the block in question itself.
        tracker.addBlocks(block);
    }
}
