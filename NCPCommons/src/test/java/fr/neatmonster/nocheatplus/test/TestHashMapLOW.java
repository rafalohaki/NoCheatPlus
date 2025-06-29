package fr.neatmonster.nocheatplus.test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import fr.neatmonster.nocheatplus.utilities.ds.map.HashMapLOW;

/**
 * A comprehensive test suite for the {@link HashMapLOW} class.
 * <p>
 * This class uses modern JUnit 5 features like nested tests
 * and {@code @DisplayName} annotations to verify the map's contract
 * in different states (e.g., when new or when populated).
 */
@DisplayName("HashMapLOW Tests")
public class TestHashMapLOW {

    /** A default initial capacity used in tests, similar to the standard one in {@link java.util.HashMap}. */
    private static final int DEFAULT_CAPACITY = 16;

    /**
     * Tests for the behavior of a newly instantiated, empty map.
     */
    @Nested
    @DisplayName("when new")
    class WhenNew {
        private HashMapLOW<String, Integer> map;

        @BeforeEach
        void setUp() {
            map = new HashMapLOW<String, Integer>(DEFAULT_CAPACITY);
        }
        
        @Test
        @DisplayName("should be empty")
        void shouldBeEmpty() {
            assertTrue(map.isEmpty(), "New map should be empty");
        }

        @Test
        @DisplayName("should have size of 0")
        void shouldHaveZeroSize() {
            assertEquals(0, map.size(), "New map should have size 0");
        }

        @Test
        @DisplayName("get() should return null for a non-existent key")
        void getShouldReturnNull() {
            assertNull(map.get("anyKey"));
        }

        @Test
        @DisplayName("containsKey() should return false for a non-existent key")
        void containsKeyShouldReturnFalse() {
            assertFalse(map.containsKey("anyKey"));
        }

        @Test
        @DisplayName("remove() should return null for a non-existent key")
        void removeShouldReturnNull() {
            assertNull(map.remove("anyKey"));
        }
        
        @Test
        @DisplayName("put() should add a new element")
        void putShouldAddElement() {
            Integer previous = map.put("one", 1);
            
            assertAll(
                () -> assertNull(previous, "Putting a new key should return null"),
                () -> assertFalse(map.isEmpty()),
                () -> assertEquals(1, map.size()),
                () -> assertTrue(map.containsKey("one")),
                () -> assertEquals(1, map.get("one"))
            );
        }
    }

    /**
     * Tests for the behavior of a map that has been populated
     * with a predefined set of elements.
     */
    @Nested
    @DisplayName("when populated")
    class WhenPopulated {
        private HashMapLOW<String, Integer> map;

        @BeforeEach
        void setUp() {
            map = new HashMapLOW<String, Integer>(DEFAULT_CAPACITY);
            map.put("one", 1);
            map.put("two", 2);
            map.put("three", 3);
        }

        @Test
        @DisplayName("should have the correct size")
        void shouldHaveCorrectSize() {
            assertEquals(3, map.size());
        }

        @Test
        @DisplayName("get() should retrieve the correct value for an existing key")
        void getShouldRetrieveValue() {
            assertEquals(2, map.get("two"));
        }
        
        @Test
        @DisplayName("put() on an existing key should update the value and return the old one")
        void putOnExistingKeyShouldUpdateValue() {
            Integer previousValue = map.put("one", 100);
            
            assertAll(
                () -> assertEquals(1, previousValue, "Should return the old value"),
                () -> assertEquals(100, map.get("one"), "Map should contain the new value"),
                () -> assertEquals(3, map.size(), "Size should not change")
            );
        }

        @Test
        @DisplayName("remove() should delete an element and return its value")
        void removeShouldDeleteElement() {
            Integer removedValue = map.remove("two");

            assertAll(
                () -> assertEquals(2, removedValue),
                () -> assertEquals(2, map.size()),
                () -> assertFalse(map.containsKey("two"))
            );
        }

        @Test
        @DisplayName("clear() should remove all elements")
        void clearShouldRemoveAllElements() {
            map.clear();
            assertAll(
                () -> assertEquals(0, map.size()),
                () -> assertTrue(map.isEmpty())
            );
        }

        @Test
        @DisplayName("iterator should visit all elements")
        void iteratorShouldVisitAllElements() {
            Set<String> keys = new HashSet<>();
            Set<Integer> values = new HashSet<>();
            
            // The HashMapLOW class provides a direct iterator() method but does not implement Iterable.
            // For this reason, an enhanced for-loop cannot be used, and the iterator must be used explicitly.
            Iterator<Map.Entry<String, Integer>> it = map.iterator();
            while (it.hasNext()) {
                Map.Entry<String, Integer> entry = it.next();
                keys.add(entry.getKey());
                values.add(entry.getValue());
            }

            assertAll(
                () -> assertEquals(Set.of("one", "two", "three"), keys),
                () -> assertEquals(Set.of(1, 2, 3), values)
            );
        }

        @Test
        @DisplayName("iterator's remove() should delete the current element from the map")
        void iteratorRemoveShouldWork() {
            Iterator<Map.Entry<String, Integer>> it = map.iterator();
            
            // According to the Iterator contract, the next() method must be called 
            // before the remove() method to specify which element should be removed.
            if (it.hasNext()) {
                it.next(); // Advance the iterator to the first element.
                it.remove(); // Now, remove that element.
            }
            
            assertEquals(2, map.size(), "Size should decrease after iterator.remove()");
        }

        @Test
        @DisplayName("iterator's entry.setValue() should update the value in the map")
        void iterator_entrySetValue_shouldUpdateValue() {
            // We search for the entry for the key "two" using the iterator and update its value.
            Iterator<Map.Entry<String, Integer>> it = map.iterator();
            while (it.hasNext()) {
                Map.Entry<String, Integer> entry = it.next();
                if (entry.getKey().equals("two")) {
                    entry.setValue(200);
                    break; // End the loop after finding and updating the entry.
                }
            }
            
            assertEquals(200, map.get("two"), "Value in the map should be updated");
        }
    }
}