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
package fr.neatmonster.nocheatplus.utilities.collision;

import org.bukkit.Location;

/**
 * Ray tracing for block coordinates with entry point offsets.
 * @author mc_dev
 *
 */
public abstract class RayTracing implements ICollideBlocks {

    /** Tolerance for floating point comparisons. */
    private static final double EPSILON = 1.0E-6;

    //	/** End point coordinates (from, to) */
    protected double x0, y0, z0; // x1, y1, z1;

    //	/** Total distance between end points. */
    //	protected double d;

    /** Distance per axis. */
    protected double dX, dY, dZ;

    /** Current block, step always has been or is called with these. */
    protected int blockX, blockY, blockZ;

    /** End block. */
    protected int endBlockX, endBlockY, endBlockZ;

    /** Offset within current block. */
    protected double oX, oY, oZ;

    /** Current "time" in [0..1]. */
    protected double t = Double.MIN_VALUE;

    /** Tolerance for time, for checking the abort condition: 1.0 - t <= tol . */
    protected double tol = 0.0;

    /** Force calling step at the end position, for the case it is reached with block transitions. */
    protected boolean forceStepEndPos = true;

    /**
     * Counting the number of steps along the primary line. Step is incremented
     * before calling step(), and is 0 after set(...). Checking this from within
     * step means to get the current step number, checking after loop gets the
     * number of steps done.
     */
    protected int step = 0;

    /** If to call stepSecondary at all (secondary transitions).*/
    protected boolean secondaryStep = true;

    /** Maximum steps that will be done. */
    private int maxSteps = Integer.MAX_VALUE;

    /** Just the flag, a sub-class must make use during handling step. */
    protected boolean ignoreInitiallyColliding = false;

    protected boolean collides = false;

    public RayTracing(double x0, double y0, double z0, double x1, double y1, double z1) {
        set(x0, y0, z0, x1, y1, z1);
    }

    public RayTracing() {
        set(0, 0, 0, 0, 0, 0);
    }

    @Override
    public void setIgnoreInitiallyColliding(boolean ignoreInitiallyColliding) {
        this.ignoreInitiallyColliding = ignoreInitiallyColliding;
    }

    @Override
    public boolean getIgnoreInitiallyColliding() {
        return ignoreInitiallyColliding;
    }

    @Override
    public void set(double x0, double y0, double z0, double x1, double y1, double z1) {
        this.x0 = x0;
        this.y0 = y0;
        this.z0 = z0;
        //		this.x1 = x1;
        //		this.y1 = y1;
        //		this.z1 = z1;
        //		d = CheckUtils.distance(x0, y0, z0, x1, y1, z1);
        dX = x1 - x0;
        dY = y1 - y0;
        dZ = z1 - z0;
        blockX = Location.locToBlock(x0);
        blockY = Location.locToBlock(y0);
        blockZ = Location.locToBlock(z0);
        endBlockX = Location.locToBlock(x1);
        endBlockY = Location.locToBlock(y1);
        endBlockZ = Location.locToBlock(z1);
        oX = x0 - (double) blockX;
        oY = y0 - (double) blockY;
        oZ = z0 - (double) blockZ;
        t = 0.0;
        step = 0;
        collides = false;
    }

    /**
     * 
     * @param dTotal
     * @param offset
     * @param isEndBlock If the end block coordinate is reached for this axis.
     * @return
     */
    private static final double tDiff(final double dTotal, final double offset, final boolean isEndBlock) {
        // NOTE: Verify if endBlock should check only for equality (==) rather than </> comparisons.
        if (dTotal > 0.0) {
            if (offset >= 1.0) {
                // Static block change (e.g. diagonal move).
                return isEndBlock ? Double.MAX_VALUE : 0.0;
            } else {
                return (1.0 - offset) / dTotal; 
            }
        }
        else if (dTotal < 0.0) {
            if (offset <= 0.0) {
                // Static block change (e.g. diagonal move).
                return isEndBlock ? Double.MAX_VALUE : 0.0;
            } else {
                return offset / -dTotal;
            }
        }
        else {
            return Double.MAX_VALUE;
        }
    }

    /**
     * Calculate the next time step for the main loop.
     *
     * @param tX time to the next x-axis block edge
     * @param tY time to the next y-axis block edge
     * @param tZ time to the next z-axis block edge
     * @return the time delta or a negative value to signal abort
     */
    private double computeTMin(final double tX, final double tY, final double tZ) {
        double result = Math.max(0.0, Math.min(tX, Math.min(tY, tZ)));
        if (result == Double.MAX_VALUE) {
            // All differences are 0 (no progress).
            if (step < 1) {
                result = 0.0;
            } else {
                return -1.0;
            }
        }
        if (t + result > 1.0) {
            // Limit to the remaining distance.
            result = 1.0 - t;
        }
        return result;
    }

    /**
     * Determine which axes need transitions for the given time step.
     * Bits: 1 = X, 2 = Y, 4 = Z.
     *
     * @param tX time to the next x-axis block edge
     * @param tY time to the next y-axis block edge
     * @param tZ time to the next z-axis block edge
     * @param tMin smallest time step considered
     * @return a bit mask of axes that require a transition
     */
    private int computeTransitionMask(final double tX, final double tY, final double tZ, final double tMin) {
        int mask = 0;
        if (Math.abs(tX - tMin) < EPSILON && blockX != endBlockX && dX != 0.0) {
            mask |= 1;
        }
        if (Math.abs(tY - tMin) < EPSILON && blockY != endBlockY && dY != 0.0) {
            mask |= 2;
        }
        if (Math.abs(tZ - tMin) < EPSILON && blockZ != endBlockZ && dZ != 0.0) {
            mask |= 4;
        }
        return mask;
    }

    /**
     * Advance the on-block origin and the absolute time by the given amount.
     *
     * @param delta the time delta for this move
     */
    private void advanceOriginAndTime(final double delta) {
        oX = Math.min(1.0, Math.max(0.0, oX + delta * dX));
        oY = Math.min(1.0, Math.max(0.0, oY + delta * dY));
        oZ = Math.min(1.0, Math.max(0.0, oZ + delta * dZ));
        t = Math.min(1.0, t + delta);
    }

    @Override
    public void loop() {

        // Time to block edge.
        double tX, tY, tZ, tMin;
        // Number of axes to make a transition for.
        int transitions;
        // Transition direction per axis.
        boolean transX, transY, transZ;

        // Actual loop.
        /*
         * NOTE: the last transition might occasionally be skipped due to
         * rounding issues or t=0 transitions. Correcting t based on block
         * coordinates could help.
         */
        while (t + tol < 1.0) {
            // Determine smallest time to block edge, per axis.
            tX = tDiff(dX, oX, blockX == endBlockX);
            tY = tDiff(dY, oY, blockY == endBlockY);
            tZ = tDiff(dZ, oZ, blockZ == endBlockZ);

            tMin = computeTMin(tX, tY, tZ);
            if (tMin < 0.0) {
                break;
            }

            // Step for the primary line.
            step++;
            if (!step(blockX, blockY, blockZ, oX, oY, oZ, tMin, true)) {
                break;
            }

            // Abort if arrived.
            if (t + tMin + tol >= 1.0 && isEndBlock()) {
                break;
            }

            int mask = computeTransitionMask(tX, tY, tZ, tMin);
            // Bits: 1 = X-axis, 2 = Y-axis, 4 = Z-axis.
            transX = (mask & 1) != 0;
            transY = (mask & 2) != 0;
            transZ = (mask & 4) != 0;
            transitions = (transX ? 1 : 0)
                    + (transY ? 1 : 0)
                    + (transZ ? 1 : 0);

            // Advance on-block origin and time based on this move.
            // NOTE: consider calculating the new position directly based on
            // the current or next block and the current t value.
            advanceOriginAndTime(tMin);

            // Handle block transitions.
            if (transitions > 0) {
                if (!handleTransitions(transitions, transX, transY, transZ, tMin)) {
                    break;
                }
                // Check conditions for abort/end.
                if (forceStepEndPos && t + tol >= 1.0) {
                    // Reached the end with transitions, ensure we check the end block.
                    step(blockX, blockY, blockZ, oX, oY, oZ, 0.0, true);
                    break;
                }
            } else {
                // No transitions, finished.
                break;
            }
            // Ensure not to go beyond maxSteps.
            if (step >= maxSteps) {
                break;
            }
        }
    }

    /**
     * 
     * @param transitions
     * @param transX
     * @param transY
     * @param transZ
     * @param tMin
     * @return If to continue at all.
     */
    protected boolean handleTransitions(final int transitions, final boolean transX, final boolean transY, final boolean transZ, final double tMin) {
        // Secondary transitions.
        if (transitions > 1 && secondaryStep) {
            if (!handleSecondaryTransitions(transitions, transX, transY, transZ, tMin)) {
                return false; 
            }
        }

        // Apply all transitions to the primary line.
        double tcMin = 1.0; // Corrected absolute time to reach the resulting block position.
        if (transX) {
            if (dX > 0.0) {
                blockX ++;
                oX = 0.0;
                tcMin = Math.min(tcMin, ((double) blockX - x0) / dX);
            }
            else {
                blockX --;
                oX = 1.0;
                tcMin = Math.min(tcMin, (1.0 + (double) blockX - x0) / dX);
            }
        }
        if (transY) {
            if (dY > 0.0) {
                blockY ++;
                oY = 0.0;
                tcMin = Math.min(tcMin, ((double) blockY - y0) / dY);
            }
            else {
                blockY --;
                oY = 1.0;
                tcMin = Math.min(tcMin, (1.0 + (double) blockY - y0) / dY);
            }
        }
        if (transZ) {
            if (dZ > 0.0) {
                blockZ ++;
                oZ = 0.0;
                tcMin = Math.min(tcMin, ((double) blockZ - z0) / dZ);
            }
            else {
                blockZ --;
                oZ = 1.0;
                tcMin = Math.min(tcMin, (1.0 + (double) blockZ - z0) / dZ);
            }
        }
        // Correct time and offsets based on tcMin.
        oX = x0 + tcMin * dX - (double) blockX;
        oY = y0 + tcMin * dY - (double) blockY;
        oZ = z0 + tcMin * dZ - (double) blockZ;
        t = tcMin;
        return true; // Continue loop.
    }

    /**
     * Handle all secondary transitions (incomplete transitions).
     * @param transitions
     * @param transX
     * @param transY
     * @param transZ
     * @param tMin
     * @return If to continue at all.
     */
    protected boolean handleSecondaryTransitions(final int transitions, final boolean transX, final boolean transY, final boolean transZ, final double tMin) {
        // Handle one transition.
        if (transX) {
            if (!step(blockX + (dX > 0 ? 1 : -1), blockY, blockZ, dX > 0 ? 0.0 : 1.0, oY, oZ, 0.0, false)) {
                return false;
            }
        }
        if (transY) {
            if (!step(blockX, blockY + (dY > 0 ? 1 : -1), blockZ, oX, dY > 0 ? 0.0 : 1.0, oZ, 0.0, false)) {
                return false;
            }
        }
        if (transZ) {
            if (!step(blockX, blockY, blockZ + (dZ > 0 ? 1 : -1), oX, oY, dZ > 0 ? 0.0 : 1.0, 0.0, false)) {
                return false;
            }
        }

        // Handle double-transitions.
        if (transitions == 3) {
            if (!handleSecondaryDoubleTransitions(transitions, transX, transY, transZ, tMin)) {
                return false; 
            }
        }

        // All passed.
        return true;
    }

    /**
     * Handle secondary transitions with 2 axes at once (incomplete transitions).
     * @param transitions
     * @param transX
     * @param transY
     * @param transZ
     * @param tMin
     * @return
     */
    protected boolean handleSecondaryDoubleTransitions(final int transitions, final boolean transX, final boolean transY, final boolean transZ, final double tMin) {
        // Two transitions at once, thus step directly.
        // X and Y.
        if (!step(blockX + (dX > 0 ? 1 : -1), blockY + (dY > 0 ? 1 : -1), blockZ, dX > 0 ? 0.0 : 1.0, dY > 0 ? 0.0 : 1.0, oZ, 0.0, false)) {
            return false;
        }
        // X and Z.
        if (!step(blockX + (dX > 0 ? 1 : -1), blockY, blockZ + (dZ > 0 ? 1 : -1), dX > 0 ? 0.0 : 1.0, oY, dZ > 0 ? 0.0 : 1.0, 0.0, false)) {
            return false;
        }
        // Y and Z.
        if (!step(blockX, blockY + (dY > 0 ? 1 : -1), blockZ + (dZ > 0 ? 1 : -1), oX, dY > 0 ? 0.0 : 1.0, dZ > 0 ? 0.0 : 1.0, 0.0, false)) {
            return false;
        }
        // All passed.
        return true;
    }

    @Override
    public Axis[] getAxisOrder() {
        return new Axis[] {Axis.XYZ_AXES};
    }

    @Override
    public boolean collides() {
        return collides;
    }

    @Override
    public Axis getCollidingAxis() {
        return Axis.XYZ_AXES;
    }

    /**
     * Test if the primary line reached the end block.<br>
     * (Might later get changed to protected visibility.)
     * @return
     */
    public boolean isEndBlock() {
        return blockX == endBlockX && blockY == endBlockY && blockZ == endBlockZ;
    }

    /**
     * This is for external use. The field step will be incremented before
     * step(...) is called, thus checking it from within step means to get the
     * current step number, checking after loop gets the number of steps done.
     * 
     * @return
     */
    @Override
    public int getStepsDone() {
        return step;
    }

    @Override
    public int getMaxSteps() {
        return maxSteps;
    }

    @Override
    public void setMaxSteps(int maxSteps) {
        this.maxSteps = maxSteps;
    }

    /**
     * Current block coordinate (main line).
     * @return
     */
    public int getBlockX() {
        return blockX;
    }

    /**
     * Current block coordinate (main line).
     * @return
     */
    public int getBlockY() {
        return blockY;
    }

    /**
     * Current block coordinate (main line).
     * @return
     */
    public int getBlockZ() {
        return blockZ;
    }

    /**
     * One step in the loop. Set the collides flag to indicate a specific
     * result.
     * 
     * @param blockX
     *            The block coordinates regarded in this step.
     * @param blockY
     * @param blockZ
     * @param oX
     *            Origin relative to the block coordinates.
     * @param oY
     * @param oZ
     * @param dT
     *            Amount of time regarded in this step (note that 0.0 is
     *            possible for transitions).
     * @param isPrimary
     *            If this is along the primary line, for which all transitions
     *            are done at once. The secondary line would cover all
     *            combinations of transitions off the primary line.
     * @return If to continue processing at all. Mind that the collides flag is
     *         not set based on the result, instead has to be set from within
     *         handling this method.
     */
    protected abstract boolean step(int blockX, int blockY, int blockZ, double oX, double oY, double oZ, double dT, boolean isPrimary);

}
