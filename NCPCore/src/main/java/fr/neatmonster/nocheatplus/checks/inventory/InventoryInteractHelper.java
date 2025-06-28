package fr.neatmonster.nocheatplus.checks.inventory;

import org.bukkit.Material;

final class InventoryInteractHelper {

    private InventoryInteractHelper() {}

    static void applyFoodInteract(final InventoryData data, final Material type, final long now) {
        final long previous = data.instantEatInteract;
        final long diff = now - previous;

        // Determine if the interaction occurred within the fast-eat threshold.
        final boolean qualifies = previous <= 0 || diff < 800; // within 800 ms

        data.instantEatInteract = (qualifies && previous > 0)
                ? Math.min(now, previous)
                : now;

        // Assign food only if the interaction qualifies.
        data.instantEatFood = qualifies ? type : null;
    }
}
