package fr.neatmonster.nocheatplus.checks.blockbreak;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import fr.neatmonster.nocheatplus.compat.AlmostBoolean;

public class FastBreakContextTest {

    @Test
    public void testContextEncapsulation() {
        ServerMock server = MockBukkit.mock();
        try {
            PlayerMock player = server.addPlayer();
            Block block = mock(Block.class);
            FastBreakContext ctx = new FastBreakContext(player, block, null, null, null, AlmostBoolean.NO);
            assertSame(player, ctx.player());
            assertSame(block, ctx.block());
            assertNull(ctx.config());
            assertNull(ctx.breakData());
            assertNull(ctx.playerData());
            assertEquals(AlmostBoolean.NO, ctx.isInstaBreak());
        } finally {
            MockBukkit.unmock();
        }
    }
}
