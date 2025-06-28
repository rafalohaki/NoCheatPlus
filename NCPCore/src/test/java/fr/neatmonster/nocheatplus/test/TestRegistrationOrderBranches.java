package fr.neatmonster.nocheatplus.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import fr.neatmonster.nocheatplus.components.registry.order.RegistrationOrder;

public class TestRegistrationOrderBranches {

    private static boolean shouldSortBefore(RegistrationOrder o1, RegistrationOrder o2) {
        return RegistrationOrder.AbstractRegistrationOrderSort.shouldSortBefore(o1, o2);
    }

    @Test
    public void testBothNull_beforeTag1Set() {
        RegistrationOrder o1 = new RegistrationOrder(null, "z", "b", "a");
        RegistrationOrder o2 = new RegistrationOrder(null, "x", null, "x");
        assertTrue(shouldSortBefore(o1, o2));
        RegistrationOrder o3 = new RegistrationOrder(null, "a", null, "b");
        assertFalse(shouldSortBefore(o1, o3));
    }

    @Test
    public void testBothNull_beforeTag2Set() {
        RegistrationOrder o1 = new RegistrationOrder(null, "a", null, null);
        RegistrationOrder o2 = new RegistrationOrder(null, null, "b", "a");
        assertTrue(shouldSortBefore(o1, o2));
    }

    @Test
    public void testBothNull_noBeforeTags_afterTag1Null() {
        RegistrationOrder o1 = new RegistrationOrder(null, "a", null, null);
        RegistrationOrder o2 = new RegistrationOrder(null, "b", null, null);
        assertTrue(shouldSortBefore(o1, o2));
    }

    @Test
    public void testBothNull_noBeforeTags_afterTag2Null() {
        RegistrationOrder o1 = new RegistrationOrder(null, "a", null, "b");
        RegistrationOrder o2 = new RegistrationOrder(null, "b", null, null);
        assertFalse(shouldSortBefore(o1, o2));
    }

    @Test
    public void testFirstNull_beforeTag1NotNull() {
        RegistrationOrder o1 = new RegistrationOrder(null, "a", "b", null);
        RegistrationOrder o2 = new RegistrationOrder(1, "b", null, null);
        assertTrue(shouldSortBefore(o1, o2));
    }

    @Test
    public void testFirstNull_afterTag1NullBasePriorityPositive() {
        RegistrationOrder o1 = new RegistrationOrder(null, "a", null, null);
        RegistrationOrder o2 = new RegistrationOrder(1, "b", null, null);
        assertTrue(shouldSortBefore(o1, o2));
    }

    @Test
    public void testFirstNull_afterTag1NullBasePriorityZeroConditionTrue() {
        RegistrationOrder o1 = new RegistrationOrder(null, null, null, null);
        RegistrationOrder o2 = new RegistrationOrder(0, "b", null, "b");
        assertTrue(shouldSortBefore(o1, o2));
    }

    @Test
    public void testFirstNull_afterTag1NullBasePriorityZeroConditionFalse() {
        RegistrationOrder o1 = new RegistrationOrder(null, "a", null, null);
        RegistrationOrder o2 = new RegistrationOrder(0, "b", "c", null);
        assertFalse(shouldSortBefore(o1, o2));
    }

    @Test
    public void testFirstNull_afterTag1NotNull() {
        RegistrationOrder o1 = new RegistrationOrder(null, "a", null, "c");
        RegistrationOrder o2 = new RegistrationOrder(0, "b", null, null);
        assertFalse(shouldSortBefore(o1, o2));
    }

    @Test
    public void testSecondNull_beforeTag2NotNull() {
        RegistrationOrder o1 = new RegistrationOrder(0, "a", null, null);
        RegistrationOrder o2 = new RegistrationOrder(null, "b", "c", null);
        assertFalse(shouldSortBefore(o1, o2));
    }

    @Test
    public void testSecondNull_afterTag2NotNull() {
        RegistrationOrder o1 = new RegistrationOrder(0, "a", null, null);
        RegistrationOrder o2 = new RegistrationOrder(null, "b", null, "c");
        assertTrue(shouldSortBefore(o1, o2));
    }

    @Test
    public void testSecondNull_basePriorityNegative() {
        RegistrationOrder o1 = new RegistrationOrder(-1, "a", null, null);
        RegistrationOrder o2 = new RegistrationOrder(null, "b", null, null);
        assertFalse(shouldSortBefore(o1, o2));
    }

    @Test
    public void testSecondNull_basePriorityPositive() {
        RegistrationOrder o1 = new RegistrationOrder(2, "a", null, null);
        RegistrationOrder o2 = new RegistrationOrder(null, "b", null, null);
        assertTrue(shouldSortBefore(o1, o2));
    }

    @Test
    public void testSecondNull_equalPriority_tag2MatchesAfterTag1() {
        RegistrationOrder o1 = new RegistrationOrder(0, "a", null, "b");
        RegistrationOrder o2 = new RegistrationOrder(null, "b", null, null);
        assertFalse(shouldSortBefore(o1, o2));
    }

    @Test
    public void testSecondNull_equalPriority_beforeTag1NotNull() {
        RegistrationOrder o1 = new RegistrationOrder(0, "a", "c", null);
        RegistrationOrder o2 = new RegistrationOrder(null, "b", null, null);
        assertTrue(shouldSortBefore(o1, o2));
    }

    @Test
    public void testSecondNull_equalPriority_afterTag1NotNull() {
        RegistrationOrder o1 = new RegistrationOrder(0, "a", null, "c");
        RegistrationOrder o2 = new RegistrationOrder(null, "b", null, null);
        assertFalse(shouldSortBefore(o1, o2));
    }

    @Test
    public void testSecondNull_equalPriority_none() {
        RegistrationOrder o1 = new RegistrationOrder(0, "a", null, null);
        RegistrationOrder o2 = new RegistrationOrder(null, "b", null, null);
        assertTrue(shouldSortBefore(o1, o2));
    }

    @Test
    public void testBothPrioritiesSet_compare() {
        RegistrationOrder low = new RegistrationOrder(-1, "a", null, null);
        RegistrationOrder high = new RegistrationOrder(1, "b", null, null);
        assertTrue(shouldSortBefore(low, high));
        assertFalse(shouldSortBefore(high, low));
    }

    @Test
    public void testBothPrioritiesSet_equal_beforeTag1Null() {
        RegistrationOrder o1 = new RegistrationOrder(0, "a", null, null);
        RegistrationOrder o2 = new RegistrationOrder(0, "b", null, null);
        assertTrue(shouldSortBefore(o1, o2));
    }

    @Test
    public void testBothPrioritiesSet_equal_beforeTag1NotNull() {
        RegistrationOrder o1 = new RegistrationOrder(0, "a", "c", null);
        RegistrationOrder o2 = new RegistrationOrder(0, "b", null, null);
        assertTrue(shouldSortBefore(o1, o2));
    }

    @Test
    public void testBothPrioritiesSet_equal_tag2MatchesAfterTag1() {
        RegistrationOrder o1 = new RegistrationOrder(0, "a", "c", "b");
        RegistrationOrder o2 = new RegistrationOrder(0, "b", null, null);
        assertFalse(shouldSortBefore(o1, o2));
    }
}
