package fr.neatmonster.nocheatplus.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import fr.neatmonster.nocheatplus.utilities.ds.bktree.BKModTree;
import fr.neatmonster.nocheatplus.utilities.ds.bktree.BKModTree.LookupEntry;
import fr.neatmonster.nocheatplus.utilities.ds.bktree.BKModTree.SimpleNode;

public class TestBKModTree {

    private static class IntBKTree extends BKModTree<Integer, SimpleNode<Integer>, LookupEntry<Integer, SimpleNode<Integer>>> {
        IntBKTree() {
            super((v, p) -> new SimpleNode<>(v), LookupEntry::new);
        }
        @Override
        public int distance(Integer v1, Integer v2) {
            return Math.abs(v1 - v2);
        }
    }

    @Test
    public void testSearchAndInsert() {
        IntBKTree tree = new IntBKTree();
        LookupEntry<Integer, SimpleNode<Integer>> res = tree.lookup(5, 0, 10, true);
        assertTrue(res.isNew);
        assertEquals(Integer.valueOf(5), res.match.getValue());

        res = tree.lookup(9, 0, 10, true);
        assertTrue(res.isNew);
        assertEquals(Integer.valueOf(9), res.match.getValue());

        res = tree.lookup(9, 0, 10, false);
        assertNotNull(res.match);
        assertEquals(Integer.valueOf(9), res.match.getValue());
        assertTrue(!res.isNew);
    }

    @Test
    public void testRangeAndInsert() {
        IntBKTree tree = new IntBKTree();
        tree.lookup(5, 0, 10, true);
        tree.lookup(9, 0, 10, true);
        tree.lookup(12, 0, 10, true);

        LookupEntry<Integer, SimpleNode<Integer>> res = tree.lookup(10, 2, 10, false);
        assertNull(res.match);
        assertTrue(res.nodes.size() >= 1);

        res = tree.lookup(10, 2, 10, true);
        assertTrue(res.isNew);
        assertEquals(Integer.valueOf(10), res.match.getValue());

        res = tree.lookup(10, 0, 10, false);
        assertNotNull(res.match);
        assertEquals(Integer.valueOf(10), res.match.getValue());
    }
}
