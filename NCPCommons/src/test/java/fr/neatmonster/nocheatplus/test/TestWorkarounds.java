package fr.neatmonster.nocheatplus.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import fr.neatmonster.nocheatplus.utilities.ds.count.acceptdeny.AcceptDenyCounter;
import fr.neatmonster.nocheatplus.utilities.ds.count.acceptdeny.IAcceptDenyCounter;
import fr.neatmonster.nocheatplus.utilities.ds.count.acceptdeny.ICounterWithParent;
import fr.neatmonster.nocheatplus.workaround.IStagedWorkaround;
import fr.neatmonster.nocheatplus.workaround.IWorkaround;
import fr.neatmonster.nocheatplus.workaround.IWorkaroundRegistry;
import fr.neatmonster.nocheatplus.workaround.IWorkaroundRegistry.WorkaroundSet;
import fr.neatmonster.nocheatplus.workaround.SimpleWorkaroundRegistry;
import fr.neatmonster.nocheatplus.workaround.WorkaroundCountDown;
import fr.neatmonster.nocheatplus.workaround.WorkaroundCounter;

/**
 * A test suite for the Workaround system, including counters, countdowns, and the registry.
 */
@DisplayName("Workaround System Tests")
public class TestWorkarounds {

    /**
     * A helper method to check the accept/deny counts of a counter.
     * This makes the test class self-contained.
     */
    private void assertCounts(IAcceptDenyCounter counter, int expectedAccepts, int expectedDenies, String context) {
        assertNotNull(counter, "Counter should not be null for context: " + context);
        assertEquals(expectedAccepts, counter.getAcceptCount(), "Accept count mismatch for: " + context);
        assertEquals(expectedDenies, counter.getDenyCount(), "Deny count mismatch for: " + context);
    }

    /**
     * Tests the basic functionality of a WorkaroundCounter.
     */
    @Nested
    @DisplayName("WorkaroundCounter")
    class WorkaroundCounterTests {
        private WorkaroundCounter wac;
        private AcceptDenyCounter parentCounter;

        @BeforeEach
        void setUp() {
            wac = new WorkaroundCounter("test.wac");
            parentCounter = new AcceptDenyCounter();
            ((ICounterWithParent) wac.getAllTimeCounter()).setParentCounter(parentCounter);
        }

        @Test
        @DisplayName("should always be usable and increment counters on each use")
        void shouldAlwaysBeUsableAndIncrementCounters() {
            for (int i = 0; i < 57; i++) {
                assertTrue(wac.canUse(), "WorkaroundCounter should always be usable.");
                assertTrue(wac.use(), "use() should return true.");
            }
            assertCounts(wac.getAllTimeCounter(), 57, 0, "WorkaroundCounter all-time");
            assertCounts(parentCounter, 57, 0, "Parent counter");
        }
    }

    /**
     * Tests the stateful logic of a WorkaroundCountDown.
     */
    @Nested
    @DisplayName("WorkaroundCountDown")
    class WorkaroundCountDownTests {
        private WorkaroundCountDown wacd;
        private AcceptDenyCounter parentCounter;

        @BeforeEach
        void setUp() {
            wacd = new WorkaroundCountDown("test.wacd", 1);
            parentCounter = new AcceptDenyCounter();
            ((ICounterWithParent) wacd.getAllTimeCounter()).setParentCounter(parentCounter);
        }

        @Test
        @DisplayName("should allow use once by default, then deny subsequent uses")
        void shouldAllowUseOnceByDefault() {
            assertTrue(wacd.use(), "First use should be allowed.");
            for (int i = 0; i < 10; i++) {
                assertFalse(wacd.use(), "Subsequent uses should be denied.");
            }

            assertCounts(wacd.getStageCounter(), 1, 10, "Stage counter after one use");
            assertCounts(wacd.getAllTimeCounter(), 1, 10, "All-time counter after one use");
            assertCounts(parentCounter, 1, 10, "Parent counter after one use");
        }

        @Test
        @DisplayName("resetConditions() should reset the stage counter but not the all-time counter")
        void resetConditionsShouldOnlyResetStageCounter() {
            wacd.use(); // Use up the initial count
            assertFalse(wacd.use());
            assertCounts(wacd.getAllTimeCounter(), 1, 1, "All-time before reset");

            wacd.resetConditions();

            assertCounts(wacd.getStageCounter(), 0, 0, "Stage counter should be zero after reset");
            assertCounts(wacd.getAllTimeCounter(), 1, 1, "All-time counter should be unchanged by reset");

            assertTrue(wacd.use(), "Should be usable again after reset.");
            assertCounts(wacd.getAllTimeCounter(), 2, 1, "All-time should accumulate after reset and new use");
        }

        @Test
        @DisplayName("setCurrentCount() allows a specific number of uses")
        void setCurrentCountShouldAllowUses() {
            wacd.setCurrentCount(5);
            for (int i = 0; i < 5; i++) {
                assertTrue(wacd.use(), "Use #" + (i + 1) + " should be allowed.");
            }
            assertFalse(wacd.use(), "Use #6 should be denied.");

            assertCounts(wacd.getStageCounter(), 5, 1, "Stage counter");
            assertCounts(wacd.getAllTimeCounter(), 5, 1, "All-time counter");
        }

        @Test
        @DisplayName("setCurrentCount() with a negative value should deny all uses")
        void setCurrentCountWithNegativeValueShouldDenyAll() {
            wacd.setCurrentCount(-1);
            assertFalse(wacd.use(), "Should not be usable with a negative count.");
            assertCounts(wacd.getStageCounter(), 0, 1, "Stage counter");
        }
    }

    /**
     * Tests for the central registry that manages workarounds.
     */
    @Nested
    @DisplayName("SimpleWorkaroundRegistry")
    class SimpleWorkaroundRegistryTests {
        private IWorkaroundRegistry registry;

        @BeforeEach
        void setUp() {
            registry = new SimpleWorkaroundRegistry();
        }

        @Test
        @DisplayName("createGlobalCounter() should return a new counter and then the same instance for the same ID")
        void createGlobalCounterShouldWork() {
            IAcceptDenyCounter counter1 = registry.createGlobalCounter("c.man");
            assertNotNull(counter1);

            IAcceptDenyCounter counter2 = registry.createGlobalCounter("c.man");
            assertSame(counter1, counter2, "Should return the same instance for the same ID.");
        }

        @Test
        @DisplayName("getWorkaround() should return a new instance, not the blueprint")
        void getWorkaroundShouldReturnNewInstance() {
            IWorkaround bluePrint = new WorkaroundCounter("wc.man");
            registry.setWorkaroundBluePrint(bluePrint);

            IWorkaround newInstance = registry.getWorkaround("wc.man");
            assertNotNull(newInstance);
            assertNotSame(bluePrint, newInstance, "getWorkaround must return a new instance.");
            assertEquals(bluePrint.getClass(), newInstance.getClass());
        }

        @Test
        @DisplayName("getCheckedIdSet() should throw exception for unregistered workarounds")
        void getCheckedIdSetShouldThrowForUnregistered() {
            List<IWorkaround> unregistered = List.of(new WorkaroundCounter("unregistered.id"));
            assertThrows(IllegalArgumentException.class, () -> registry.getCheckedIdSet(unregistered),
                "Should throw an exception for workarounds that are not registered as blueprints.");
        }

        /**
         * Tests for a WorkaroundSet created by the registry.
         */
        @Nested
        @DisplayName("WorkaroundSet behavior")
        class WorkaroundSetTests {
            private WorkaroundSet ws;
            private List<String> counterIds;
            private List<String> countDownIds;
            private List<String> allIds;

            @BeforeEach
            void setUp() {
                // Setup a complex registry state with multiple workarounds and groups
                List<WorkaroundCounter> counters = List.of(new WorkaroundCounter("wc.1"), new WorkaroundCounter("wc.2"));
                List<WorkaroundCountDown> countDowns = List.of(new WorkaroundCountDown("wcd.1", 1), new WorkaroundCountDown("wcd.2", 1));

                registry.setWorkaroundBluePrint(counters.toArray(new IWorkaround[0]));
                registry.setWorkaroundBluePrint(countDowns.toArray(new IWorkaround[0]));
                
                counterIds = new ArrayList<>(registry.getCheckedIdSet(counters));
                countDownIds = new ArrayList<>(registry.getCheckedIdSet(countDowns));
                
                allIds = new ArrayList<>();
                allIds.addAll(counterIds);
                allIds.addAll(countDownIds);

                registry.setGroup("group.counters", counterIds);
                registry.setGroup("group.countdowns", countDownIds);
                
                // FIX: The WorkaroundSet must be created with knowledge of the groups it contains.
                registry.setWorkaroundSetByIds("ws.all", allIds, "group.counters", "group.countdowns");
                
                ws = registry.getWorkaroundSet("ws.all");
            }

            @Test
            @DisplayName("use() should affect the correct workaround within the set")
            void useShouldAffectCorrectWorkaround() {
                ws.use("wc.1");
                ws.use("wcd.1");
                ws.use("wcd.1"); // Second use should be denied

                assertCounts(ws.getWorkaround("wc.1").getAllTimeCounter(), 1, 0, "Counter wc.1");
                assertCounts(ws.getWorkaround("wc.2").getAllTimeCounter(), 0, 0, "Counter wc.2 (unused)");
                assertCounts(ws.getWorkaround("wcd.1").getAllTimeCounter(), 1, 1, "Countdown wcd.1");
            }

            @Test
            @DisplayName("resetConditions() on the entire set should reset all staged counters")
            void resetAllShouldResetStagedCounters() {
                allIds.forEach(ws::use); // Use everything once
                assertCounts(((IStagedWorkaround) ws.getWorkaround("wcd.1")).getStageCounter(), 1, 0, "Stage before reset");

                ws.resetConditions();

                countDownIds.forEach(id -> 
                    assertCounts(((IStagedWorkaround) ws.getWorkaround(id)).getStageCounter(), 0, 0, "Stage counter for " + id + " after reset")
                );

                // All-time counts should remain
                assertCounts(ws.getWorkaround("wcd.1").getAllTimeCounter(), 1, 0, "All-time after reset");
            }

            @Test
            @DisplayName("resetConditions() on a group should only reset members of that group")
            void resetGroupShouldOnlyResetGroupMembers() {
                allIds.forEach(ws::use); // Use everything once
                assertCounts(((IStagedWorkaround) ws.getWorkaround("wcd.1")).getStageCounter(), 1, 0, "wcd.1 stage before reset");
                assertCounts(((IStagedWorkaround) ws.getWorkaround("wcd.2")).getStageCounter(), 1, 0, "wcd.2 stage before reset");

                ws.resetConditions("group.counters"); // Resetting counters should have no effect on countdowns

                assertCounts(((IStagedWorkaround) ws.getWorkaround("wcd.1")).getStageCounter(), 1, 0, "wcd.1 stage should be unchanged");
                assertCounts(((IStagedWorkaround) ws.getWorkaround("wcd.2")).getStageCounter(), 1, 0, "wcd.2 stage should be unchanged");

                ws.resetConditions("group.countdowns"); // Now reset the countdowns

                assertCounts(((IStagedWorkaround) ws.getWorkaround("wcd.1")).getStageCounter(), 0, 0, "wcd.1 stage should now be reset");
                assertCounts(((IStagedWorkaround) ws.getWorkaround("wcd.2")).getStageCounter(), 0, 0, "wcd.2 stage should now be reset");
            }
        }
    }
}