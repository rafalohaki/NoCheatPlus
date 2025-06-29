package fr.neatmonster.nocheatplus.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import fr.neatmonster.nocheatplus.utilities.ds.bktree.BKModTree;
import fr.neatmonster.nocheatplus.utilities.ds.bktree.BKModTree.LookupEntry;
import fr.neatmonster.nocheatplus.utilities.ds.bktree.BKModTree.SimpleNode;

@DisplayName("BKModTree Tests")
public class TestBKModTree {

    /**
     * Test-specific implementation of a BK-Tree for Integers, where
     * the distance is the absolute difference between two numbers.
     */
    private static class IntBKTree extends BKModTree<Integer, SimpleNode<Integer>, LookupEntry<Integer, SimpleNode<Integer>>> {
        IntBKTree() {
            super((value, parent) -> new SimpleNode<>(value), LookupEntry::new);
        }

        @Override
        public int distance(Integer v1, Integer v2) {
            return Math.abs(v1 - v2);
        }
    }

    @Nested
    @DisplayName("When tree is empty")
    class WhenTreeIsEmpty {
        
        private IntBKTree tree;

        @BeforeEach
        void setUp() {
            tree = new IntBKTree();
        }

        @Test
        @DisplayName("should insert the first element when lookup is called with insertion enabled")
        void shouldInsertFirstElement() {
            // When performing a lookup with insertion enabled
            LookupEntry<Integer, SimpleNode<Integer>> res = tree.lookup(5, 0, 10, true);

            // Then a new node should be created and returned
            assertTrue(res.isNew, "A new node should be indicated");
            assertNotNull(res.match, "The matching node should not be null");
            assertEquals(5, res.match.getValue(), "The node should have the correct value");
        }
    }

    @Nested
    @DisplayName("When tree is populated")
    class WhenTreeIsPopulated {

        private IntBKTree tree;

        @BeforeEach
        void setUp() {
            // Given a pre-populated tree
            tree = new IntBKTree();
            tree.lookup(5, 0, 10, true);
            tree.lookup(9, 0, 10, true);
            tree.lookup(12, 0, 10, true);
        }

        @Test
        @DisplayName("should find an exact match when insertion is disabled")
        void shouldFindExactMatch() {
            // When looking up an existing element without insertion
            LookupEntry<Integer, SimpleNode<Integer>> res = tree.lookup(9, 0, 10, false);

            // Then the existing node should be found
            assertFalse(res.isNew, "The result should not be marked as new");
            assertNotNull(res.match, "An exact match should be found");
            assertEquals(9, res.match.getValue(), "The value of the matched node should be correct");
        }
        
        @Test
        @DisplayName("should insert a new element when no exact match is found")
        void shouldInsertNewElement() {
            // When looking up a new element with insertion enabled
            LookupEntry<Integer, SimpleNode<Integer>> res = tree.lookup(10, 2, 10, true);

            // Then a new node should be created
            assertTrue(res.isNew, "A new node should be created");
            assertNotNull(res.match, "The new node should be returned as the match");
            assertEquals(10, res.match.getValue(), "The new node should have the correct value");
        }

        @Test
        @DisplayName("should find nearby nodes without an exact match")
        void shouldFindNearbyNodes() {
            // When looking for a value within a certain range of existing nodes
            LookupEntry<Integer, SimpleNode<Integer>> res = tree.lookup(10, 2, 10, false);

            // Then no exact match should be found
            assertNull(res.match, "There should be no exact match for the value 10");
            
            // But nodes within the distance (2) should be returned
            assertFalse(res.nodes.isEmpty(), "The list of nearby nodes should not be empty");
            
            Set<Integer> nearbyValues = res.nodes.stream()
                .map(SimpleNode::getValue)
                .collect(Collectors.toSet());
            
            assertTrue(nearbyValues.contains(9), "Node 9 should be found (distance is 1)");
            assertTrue(nearbyValues.contains(12), "Node 12 should be found (distance is 2)");
            assertEquals(2, nearbyValues.size(), "Exactly two nodes should be in the result set");
        }
    }
}