package fr.neatmonster.nocheatplus.checks.inventory;

import org.bukkit.Material;

final class InventoryInteractHelper {

    private InventoryInteractHelper() {}

    static void applyFoodInteract(final InventoryData data, final Material type, final long now) {
        final long previous = data.instantEatInteract;
        final long diff = now - previous;

        if (previous <= 0) {
            data.instantEatInteract = now;
            data.instantEatFood = type;
        } else if (diff < 800) {
            data.instantEatInteract = Math.min(now, previous);
            data.instantEatFood = type;
        } else {
            data.instantEatInteract = now;
            data.instantEatFood = null;
        }
    }
}
