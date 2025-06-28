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
package fr.neatmonster.nocheatplus.checks.access;

import fr.neatmonster.nocheatplus.actions.ParameterHolder;

/**
 * Access interface for extended information about violations.
 * 
 * @author mc_dev
 *
 */
public interface IViolationInfo extends ParameterHolder {

    // May be moved to a component or another API location in a future iteration.

    /**
     * Get the violation level just added by this violation.
     * 
     * @return
     */
    public double getAddedVl();

    /**
     * Get the total violation level the player has right now. This is not the
     * value shown with "/ncp info <player>", but the value used for actions.
     * 
     * @return
     */
    public double getTotalVl();

    /**
     * Check if a cancel would happen.
     *
     * @return {@code true} if cancellation would occur
     * @deprecated The concept of cancel is now represented in penalties. Use
     *             {@link #willCancel()} instead. This method will be removed in
     *             version 2.0.
     *             <p>
     *             Migration: replace calls to {@code hasCancel()} with
     *             {@code willCancel()}.
     *             </p>
     */
    public boolean hasCancel();

    /**
     * Test, if the evaluation of penalties will lead to canceling. This may be
     * overridden, if violation processing is cancelled by a hook.
     * 
     * @return
     */
    public boolean willCancel();

    /**
     * Test if there is any instances of (Generic)LogAction set in the
     * applicable actions.
     * 
     * @return
     */
    public boolean hasLogAction();

}
