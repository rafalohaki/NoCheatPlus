package fr.neatmonster.nocheatplus.test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import fr.neatmonster.nocheatplus.utilities.ds.map.CoordHashMap;
import fr.neatmonster.nocheatplus.utilities.ds.map.CoordMap;
import fr.neatmonster.nocheatplus.utilities.ds.map.CoordMap.Entry;
import fr.neatmonster.nocheatplus.utilities.ds.map.LinkedCoordHashMap;
import fr.neatmonster.nocheatplus.utilities.ds.map.LinkedCoordHashMap.MoveOrder;

@DisplayName("CoordMap Implementation Tests")
public class TestCoordMap {

    // --- Test Scenarios for All CoordMap Implementations ---

    @DisplayName("General Map Contract")
    @Nested
    class GeneralMapContractTests {

        /**
         * Provides instances of all CoordMap implementations to be tested.
         * Using Supplier allows creating a fresh map for each test run.
         */
        static Stream<Arguments> mapImplementationsProvider() {
            return Stream.of(
                Arguments.of(Named.of("CoordHashMap", (Supplier<CoordMap<Integer>>) CoordHashMap::new)),
                Arguments.of(Named.of("LinkedCoordHashMap", (Supplier<CoordMap<Integer>>) LinkedCoordHashMap::new))
            );
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @MethodSource("mapImplementationsProvider")
        @DisplayName("should store and retrieve a value")
        void putAndGet(Supplier<CoordMap<Integer>> mapSupplier) {
            CoordMap<Integer> map = mapSupplier.get();
            map.put(1, 2, 3, 100);

            assertAll(
                () -> assertEquals(1, map.size()),
                () -> assertTrue(map.contains(1, 2, 3)),
                () -> assertEquals(100, map.get(1, 2, 3))
            );
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @MethodSource("mapImplementationsProvider")
        @DisplayName("should return null for non-existent key")
        void getNonExistent(Supplier<CoordMap<Integer>> mapSupplier) {
            CoordMap<Integer> map = mapSupplier.get();
            assertNull(map.get(1, 2, 3));
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @MethodSource("mapImplementationsProvider")
        @DisplayName("should update value for an existing key")
        void putOverwrite(Supplier<CoordMap<Integer>> mapSupplier) {
            CoordMap<Integer> map = mapSupplier.get();
            map.put(1, 2, 3, 100);
            Integer previous = map.put(1, 2, 3, 200);

            assertAll(
                () -> assertEquals(1, map.size()),
                () -> assertEquals(100, previous, "put should return the previous value"),
                () -> assertEquals(200, map.get(1, 2, 3), "get should return the new value")
            );
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @MethodSource("mapImplementationsProvider")
        @DisplayName("should remove an element and return its value")
        void removeElement(Supplier<CoordMap<Integer>> mapSupplier) {
            CoordMap<Integer> map = mapSupplier.get();
            map.put(1, 2, 3, 100);
            
            Integer removedValue = map.remove(1, 2, 3);

            assertAll(
                () -> assertEquals(100, removedValue),
                () -> assertEquals(0, map.size()),
                () -> assertFalse(map.contains(1, 2, 3))
            );
        }
        
        @ParameterizedTest(name = "[{index}] {0}")
        @MethodSource("mapImplementationsProvider")
        @DisplayName("should clear all elements from the map")
        void clearMap(Supplier<CoordMap<Integer>> mapSupplier) {
            CoordMap<Integer> map = mapSupplier.get();
            map.put(1, 2, 3, 100);
            map.put(4, 5, 6, 200);
            
            map.clear();

            assertAll(
                () -> assertEquals(0, map.size()),
                () -> assertFalse(map.iterator().hasNext())
            );
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @MethodSource("mapImplementationsProvider")
        @DisplayName("iterator should traverse all elements")
        void iteratorTraversesAll(Supplier<CoordMap<Integer>> mapSupplier) {
            CoordMap<Integer> map = mapSupplier.get();
            Set<Integer> expectedValues = new HashSet<>(Arrays.asList(100, 200, 300));
            
            map.put(1, 1, 1, 100);
            map.put(2, 2, 2, 200);
            map.put(3, 3, 3, 300);

            Set<Integer> foundValues = new HashSet<>();
            map.iterator().forEachRemaining(entry -> foundValues.add(entry.getValue()));

            assertEquals(expectedValues, foundValues);
        }
        
        @ParameterizedTest(name = "[{index}] {0}")
        @MethodSource("mapImplementationsProvider")
        @DisplayName("iterator.remove() should remove elements during iteration")
        void iteratorRemove(Supplier<CoordMap<Integer>> mapSupplier) {
            CoordMap<Integer> map = mapSupplier.get();
            map.put(1, 1, 1, 100);
            map.put(2, 2, 2, 200);

            Iterator<Entry<Integer>> it = map.iterator();
            while (it.hasNext()) {
                Entry<Integer> entry = it.next();
                if (entry.getValue() == 100) {
                    it.remove();
                }
            }

            assertAll(
                () -> assertEquals(1, map.size()),
                () -> assertFalse(map.contains(1, 1, 1)),
                () -> assertTrue(map.contains(2, 2, 2))
            );
        }
    }

    // --- Tests Specific to LinkedCoordHashMap ---

    @Nested
    @DisplayName("LinkedCoordHashMap Specific Behavior")
    class LinkedCoordHashMapTests {
        
        private LinkedCoordHashMap<Integer> map;

        @BeforeEach
        void setUp() {
            map = new LinkedCoordHashMap<>();
            // Insertion order
            map.put(1, 1, 1, 100);
            map.put(2, 2, 2, 200);
            map.put(3, 3, 3, 300);
        }

        @Test
        @DisplayName("should iterate in insertion order by default")
        void iterationOrder() {
            List<Integer> values = new ArrayList<>();
            map.iterator().forEachRemaining(entry -> values.add(entry.getValue()));
            
            assertEquals(Arrays.asList(100, 200, 300), values);
        }
        
        @Test
        @DisplayName("should iterate in reverse insertion order when requested")
        void reverseIterationOrder() {
            List<Integer> values = new ArrayList<>();
            map.iterator(true).forEachRemaining(entry -> values.add(entry.getValue()));
            
            assertEquals(Arrays.asList(300, 200, 100), values);
        }

        @Test
        @DisplayName("put() with MoveOrder.END should move existing element to the end")
        void moveExistingElementToEnd() {
            map.put(1, 1, 1, 101, MoveOrder.END); // Re-insert first element
            
            List<Integer> values = new ArrayList<>();
            map.iterator().forEachRemaining(entry -> values.add(entry.getValue()));
            
            assertAll(
                () -> assertEquals(3, map.size()),
                () -> assertEquals(101, map.get(1, 1, 1)),
                () -> assertEquals(Arrays.asList(200, 300, 101), values, "Element 101 should be last")
            );
        }

        @Test
        @DisplayName("put() with MoveOrder.FRONT should move existing element to the front")
        void moveExistingElementToFront() {
            map.put(3, 3, 3, 301, MoveOrder.FRONT); // Re-insert last element

            List<Integer> values = new ArrayList<>();
            map.iterator().forEachRemaining(entry -> values.add(entry.getValue()));
            
            assertAll(
                () -> assertEquals(3, map.size()),
                () -> assertEquals(301, map.get(3, 3, 3)),
                () -> assertEquals(Arrays.asList(301, 100, 200), values, "Element 301 should be first")
            );
        }
    }
}