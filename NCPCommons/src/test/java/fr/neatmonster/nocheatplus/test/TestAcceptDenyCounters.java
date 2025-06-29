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
package fr.neatmonster.nocheatplus.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import fr.neatmonster.nocheatplus.utilities.ds.count.acceptdeny.AcceptDenyCounter;
import fr.neatmonster.nocheatplus.utilities.ds.count.acceptdeny.IAcceptDenyCounter;

@DisplayName("AcceptDenyCounter Tests")
public class TestAcceptDenyCounters {

    @Nested
    @DisplayName("When a counter has a parent")
    class HierarchicalCounterTest {
        private AcceptDenyCounter leaf;
        private AcceptDenyCounter parent;

        @BeforeEach
        void setUp() {
            leaf = new AcceptDenyCounter();
            parent = new AcceptDenyCounter();
            leaf.setParentCounter(parent);
        }

        @Test
        @DisplayName("incrementing the child should update its own counts and propagate to the parent")
        void incrementingChildUpdatesBoth() {
            // Action: Increment child counter
            for (int i = 0; i < 73; i++) leaf.accept();
            for (int i = 0; i < 65; i++) leaf.deny();

            // Assert: Check child's own counts
            assertEquals(73, leaf.getAcceptCount(), "Leaf accept count should be correct.");
            assertEquals(65, leaf.getDenyCount(), "Leaf deny count should be correct.");

            // Assert: Check parent's total counts
            assertEquals(73, parent.getAcceptCount(), "Parent should reflect child's accept count.");
            assertEquals(65, parent.getDenyCount(), "Parent should reflect child's deny count.");
        }

        @Test
        @DisplayName("incrementing the parent should only affect the parent's total, not the child")
        void incrementingParentOnlyAffectsParent() {
            // Arrange: Give child and parent initial counts
            for (int i = 0; i < 10; i++) leaf.accept();
            for (int i = 0; i < 5; i++) leaf.deny();

            // Action: Increment parent counter directly
            for (int i = 0; i < 52; i++) parent.accept();
            for (int i = 0; i < 97; i++) parent.deny();

            // Assert: Child's counts should be unchanged
            assertEquals(10, leaf.getAcceptCount(), "Leaf accept count should not change when parent is incremented directly.");
            assertEquals(5, leaf.getDenyCount(), "Leaf deny count should not change when parent is incremented directly.");

            // Assert: Parent's counts should be the sum of its own increments and the child's
            assertEquals(10 + 52, parent.getAcceptCount(), "Parent's accept count should be the total.");
            assertEquals(5 + 97, parent.getDenyCount(), "Parent's deny count should be the total.");
        }

        @Test
        @DisplayName("resetting the parent should clear its totals but not affect the child's counts")
        void resettingParentDoesNotAffectChild() {
            // Arrange: Give child initial counts
            for (int i = 0; i < 73; i++) leaf.accept();
            for (int i = 0; i < 65; i++) leaf.deny();

            // Sanity check: Parent has the counts from child
            assertEquals(73, parent.getAcceptCount());
            assertEquals(65, parent.getDenyCount());

            // Action: Reset the parent
            parent.resetCounter();

            // Assert: Parent's counts are now zero
            assertEquals(0, parent.getAcceptCount(), "Parent accept count should be 0 after reset.");
            assertEquals(0, parent.getDenyCount(), "Parent deny count should be 0 after reset.");

            // Assert: Child's counts are unaffected
            assertEquals(73, leaf.getAcceptCount(), "Leaf accept count should be unaffected by parent reset.");
            assertEquals(65, leaf.getDenyCount(), "Leaf deny count should be unaffected by parent reset.");
        }
    }


    // --- Legacy Public Static Helper Methods ---
    // These methods are preserved for backward compatibility with other tests that might use them.
    // Their internal logic has been modernized to use standard assertions.

    /**
     * Checks the counts of a given counter.
     * This method is preserved for potential use by other test classes.
     */
    public static void checkCounts(IAcceptDenyCounter counter, int acceptCount, int denyCount, String counterName) {
        assertEquals(acceptCount, counter.getAcceptCount(),
                "Wrong accept count for counter '" + counterName + "'.");
        assertEquals(denyCount, counter.getDenyCount(),
                "Wrong deny count for counter '" + counterName + "'.");
    }

    /**
     * Checks if a series of counters have the same accept/deny counts.
     * This method is preserved for potential use by other test classes.
     */
    public static void checkSame(String testName, IAcceptDenyCounter... counters) {
        if (counters.length < 2) {
            return;
        }
        IAcceptDenyCounter first = counters[0];
        for (int i = 1; i < counters.length; i++) {
            IAcceptDenyCounter current = counters[i];
            assertEquals(first.getAcceptCount(), current.getAcceptCount(),
                    "Accept count differs at index " + i + " for test: " + testName);
            assertEquals(first.getDenyCount(), current.getDenyCount(),
                    "Deny count differs at index " + i + " for test: " + testName);
        }
    }

    /**
     * Checks if a series of counters have the same, specific accept/deny counts.
     * This method is preserved for potential use by other test classes.
     */
    public static void checkSame(int acceptCount, int denyCount, String testName, IAcceptDenyCounter... counters) {
        for (int i = 0; i < counters.length; i++) {
            checkCounts(counters[i], acceptCount, denyCount, "counter at index " + i + " / " + testName);
        }
        // The original implementation called checkSame again, which is redundant but preserved for behavioral compatibility.
        checkSame(testName, counters);
    }
}
