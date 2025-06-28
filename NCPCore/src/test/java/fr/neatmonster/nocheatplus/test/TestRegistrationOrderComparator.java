package fr.neatmonster.nocheatplus.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import fr.neatmonster.nocheatplus.components.registry.order.RegistrationOrder;

/**
 * Verify sorting order of {@link RegistrationOrder#cmpBasePriority}.
 */
public class TestRegistrationOrderComparator {

    @Test
    public void testComparatorDescending() {
        List<RegistrationOrder> list = new ArrayList<>();
        list.add(new RegistrationOrder(5));
        list.add(new RegistrationOrder((Integer) null));
        list.add(new RegistrationOrder(10));
        list.add(new RegistrationOrder(1));

        list.sort(RegistrationOrder.cmpBasePriority);

        assertNull(list.get(0).getBasePriority(), "Null priority should come first");
        assertEquals(Integer.valueOf(10), list.get(1).getBasePriority());
        assertEquals(Integer.valueOf(5), list.get(2).getBasePriority());
        assertEquals(Integer.valueOf(1), list.get(3).getBasePriority());
    }

    @Test
    public void testComparatorAscending() {
        List<RegistrationOrder> list = new ArrayList<>();
        list.add(new RegistrationOrder(5));
        list.add(new RegistrationOrder((Integer) null));
        list.add(new RegistrationOrder(10));
        list.add(new RegistrationOrder(1));

        list.sort(RegistrationOrder.cmpBasePriorityAscending);

        assertNull(list.get(0).getBasePriority(), "Null priority should come first");
        assertEquals(Integer.valueOf(1), list.get(1).getBasePriority());
        assertEquals(Integer.valueOf(5), list.get(2).getBasePriority());
        assertEquals(Integer.valueOf(10), list.get(3).getBasePriority());
    }
}
