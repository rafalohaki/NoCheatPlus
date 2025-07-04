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
package fr.neatmonster.nocheatplus.checks.moving.velocity;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Velocity accounting for one axis (positive + negative direction) with
 * maintaining some kind of friction effects and maximum distance gained.
 * 
 * @author dev1mc
 *
 */
public class FrictionAxisVelocity {

    /** Velocity with a smaller absolute amount is removed. */
    private static final double minValue = 0.001;

    private static final double defaultFrictionFactor = 0.93;

    private final List<AccountEntry> queued = new LinkedList<AccountEntry>();
    private final List<AccountEntry> active = new LinkedList<AccountEntry>();

    public void add(AccountEntry vel) {
        // Merging behavior is not yet implemented.
        if (Math.abs(vel.value) != 0.0) {
            queued.add(vel);
        }
    }

    public boolean hasActive() {
        return !active.isEmpty();
    }

    public boolean hasQueued() {
        return !queued.isEmpty();
    }

    /**
     * Queued or active.
     * @return
     */
    public boolean hasAny() {
        return !active.isEmpty() || !queued.isEmpty();
    }

    /**
     * Tick the velocity entries with a moving event, no invalidation takes
     * place here. This method uses the defaultFrictionFactor.
     */
    public void tick() {
        tick(defaultFrictionFactor);
    }

    /**
     * Tick the velocity entries with a moving event, no invalidation takes
     * place here.
     * 
     * @param frictionFactor
     *            The friction to use with active entries this tick.
     */
    public void tick(final double frictionFactor) {
        // Decrease counts for active.
        // Actual friction is simplified here; a special value could disable it.
        // Removal of invalidated entries is handled elsewhere.
        // Invalidation is handled by removeInvalid().
        for (final AccountEntry vel : active) {
            vel.valCount --;
            vel.sum += vel.value;
            vel.value *= frictionFactor;
            // (Altered entries should be kept, since they get used right away.)
        }
        // Decrease counts for queued.
        for (AccountEntry accountEntry : queued) {
            accountEntry.actCount--;
        }
    }

    /**
     * Remove all velocity entries that are invalid. Checks both active and queued.
     * <br>(This does not catch invalidation by speed / direction changing.)
     * @param tick All velocity added before this tick gets removed.
     */
    public void removeInvalid(final int tick) {
        // Note: Adding unused entries to a collection should later be supported.
        // Merging entries might be done here or when adding.
        Iterator<AccountEntry> it;
        // Active.
        it = active.iterator();
        while (it.hasNext()) {
            final AccountEntry vel = it.next();
            // The 0.001 threshold could be defined elsewhere.
            // Using the tick here might improve actCount/valCount accuracy.
            if (vel.valCount <= 0 || Math.abs(vel.value) <= minValue) {
                //              System.out.prsintln("Invalidate active: " + vel);
                it.remove();
            }
        }
        // Queued.
        it = queued.iterator();
        while (it.hasNext()) {
            // Could check for alternating signum to detect errors.
            final AccountEntry vel = it.next();
            if (vel.actCount <= 0 || vel.tick < tick) {
                //              NCPAPIProvider.getNoCheatPlusAPI().getLogManager().debug(Streams.TRACE_FILE, "Invalidate queued: " + vel);
                it.remove();
            }
        }
    }

    /**
     * Get the sum of active velocity values.
     * @return Can be positive or negative.
     */
    public double getFreedom() {
        // Further modeling could increase accuracy.
        double f = 0;
        for (final AccountEntry vel : active) {
            f += vel.value;
        }
        return f;
    }

    /**
     * Use all queued velocity until at least amount is matched.
     * Amount is the horizontal distance that is to be covered by velocity (active has already been checked).
     * <br>
     * If the modeling changes (max instead of sum or similar), then this will be affected.
     * @param amount The amount used, should be negative or positive depending on direction.
     * @return
     */
    public double use(final double amount) {
        if (!active.isEmpty()) {
            // Invalidate active on "direction change" [direction of consumption].
            if (amount * active.get(0).value < 0.0) {
                active.clear();
            }
        }
        final Iterator<AccountEntry> it = queued.iterator();
        double used = 0;
        while (it.hasNext()) {
            final AccountEntry vel = it.next();
            if (vel.value * amount < 0.0) {
                // Not aligned.
                // Small amounts of velocity might still be problematic.
                // Determine whether to remove or skip non-fitting entries until a matching one is found.
                it.remove(); // Removal might change queued entries.
                continue;
            }
            used += vel.value;
            active.add(vel);
            it.remove();
            if (Math.abs(used) >= Math.abs(amount)) {
                break;
            }
        }
        // Sum could be tracked here if needed.
        return used;
    }

    public void clearActive() {
        active.clear();
    }

    /**
     * Clear active and queued velocity entries.
     */
    public void clear() {
        queued.clear();
        active.clear();
    }

    /**
     * Debugging.
     * @param builder
     */
    public void addQueued(StringBuilder builder) {
        addVeloctiy(builder, queued);
    }

    /**
     * Debugging.
     * @param builder
     */
    public void addActive(StringBuilder builder) {
        addVeloctiy(builder, active);
    }

    /**
     * Debugging.
     * @param builder
     * @param entries
     */
    private void addVeloctiy(final StringBuilder builder, final List<AccountEntry> entries) {
        for (final AccountEntry vel: entries) {
            builder.append(" ");
            builder.append(vel);
        }
    }

}
