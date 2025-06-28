package fr.neatmonster.nocheatplus.checks.blockbreak;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.Test;

import fr.neatmonster.nocheatplus.compat.AlmostBoolean;

public class FastBreakContextTest {

    @Test
    public void testContextEncapsulation() {
        Player player = mock(Player.class);
        Block block = mock(Block.class);
        FastBreakContext ctx = new FastBreakContext(player, block, null, null, null, AlmostBoolean.NO);
        assertSame(player, ctx.player());
        assertSame(block, ctx.block());
        assertNull(ctx.config());
        assertNull(ctx.breakData());
        assertNull(ctx.playerData());
        assertEquals(AlmostBoolean.NO, ctx.isInstaBreak());
    }
}
