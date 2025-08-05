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
package fr.neatmonster.nocheatplus.penalties;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class DefaultPenaltyList implements IPenaltyList {

    /**
     * Desperation.
     * 
     * @author asofold
     *
     * @param <RI>
     */
    private static class GenericNode<RI> {
        private final List<IPenalty<RI>> penalties = new LinkedList<IPenalty<RI>>();

        /**
         * 
         * @param input
         * @param removeAppliedPenalties
         *            See {@link IPenalty#apply(Object)}.
         * @return True if the list has been emptied, false otherwise.
         */
        private boolean apply(final RI input, final boolean removeAppliedPenalties) {
            penalties.removeIf(riiPenalty -> riiPenalty.apply(input) && removeAppliedPenalties);
            return penalties.isEmpty();
        }
    }

    private boolean willCancel = false;
    private final Map<Class<?>, GenericNode<?>> penaltyMap = new LinkedHashMap<Class<?>, GenericNode<?>>();

    @Override
    public <RI> void addPenalty(final Class<RI> registeredInput, 
            final IPenalty<RI> penalty) {
        if (penalty == CancelPenalty.CANCEL) {
            willCancel = true;
        }
        else {
            @SuppressWarnings("unchecked")
            GenericNode<RI> node = (GenericNode<RI>) penaltyMap.get(registeredInput);
            if (node == null) {
                node = new GenericNode<RI>();
                penaltyMap.put(registeredInput, node);
            }
            node.penalties.add(penalty);
        }
    }

    @Override
    public <RI, I extends RI> void applyPenaltiesPrecisely(
            final Class<RI> type, final I input, 
            final boolean removeAppliedPenalties) {
        @SuppressWarnings("unchecked")
        final GenericNode<RI> node = (GenericNode<RI>) penaltyMap.get(type);
        if (node != null && node.apply(input, removeAppliedPenalties)
                && removeAppliedPenalties) {
            penaltyMap.remove(type);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <I> void applyAllApplicablePenalties(final I input, 
            final boolean removeAppliedPenalties) {
        final Class<?> inputClass = input.getClass();
        penaltyMap.entrySet().removeIf(entry -> entry.getKey().isAssignableFrom(inputClass) &&
                ((GenericNode<? super I>) entry.getValue()).apply(input, removeAppliedPenalties)
                && removeAppliedPenalties);
    }


    @Override
    public boolean isEmpty() {
        return penaltyMap.isEmpty();
    }

    @Override
    public boolean willCancel() {
        return willCancel;
    }

}
