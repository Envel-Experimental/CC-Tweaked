// SPDX-FileCopyrightText: 2024 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.network.server;

import dan200.computercraft.core.AiConfig;
import dan200.computercraft.core.apis.ai.AiRateLimiter;
import dan200.computercraft.shared.computer.core.ServerComputer;
import dan200.computercraft.shared.computer.menu.ComputerMenu;
import dan200.computercraft.shared.network.server.AskAiErrorHintMessage;
import dan200.computercraft.shared.network.server.ServerNetworkContext;
import dan200.computercraft.test.core.ReplaceUnderscoresDisplayNameGenerator;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayNameGeneration(ReplaceUnderscoresDisplayNameGenerator.class)
class AiPacketSecurityTest {

    private AskAiErrorHintMessage message;
    private ServerNetworkContext context;
    private ComputerMenu menu;
    private Player player;
    private UUID playerUuid;

    @BeforeEach
    void setUp() {
        AiConfig.enabled = true;
        AiConfig.errorHintEnabled = true;
        
        context = mock(ServerNetworkContext.class);
        player = mock(Player.class);
        playerUuid = UUID.randomUUID();
        when(player.getUUID()).thenReturn(playerUuid);
        when(context.getSender()).thenReturn(player);

        menu = mock(ComputerMenu.class);
        var computer = mock(ServerComputer.class);
        when(menu.getComputer()).thenReturn(computer);
        when(computer.getInstanceID()).thenReturn(42);

        // Reset rate limiter state for tests
        AiRateLimiter.INSTANCE.playerStatesForTest().clear();
    }
    
    @AfterEach
    void tearDown() {
        AiRateLimiter.INSTANCE.playerStatesForTest().clear();
    }

    @Test
    void drops_silently_if_ai_disabled() {
        AiConfig.enabled = false;
        
        message = new AskAiErrorHintMessage(menu, "bios.lua:14: Expected number");
        message.handle(context, menu);
        
        // Assert rate limiter wasn't even touched
        assertEquals(0, AiRateLimiter.INSTANCE.playerStatesForTest().size());
    }

    @Test
    void drops_silently_if_hint_disabled() {
        AiConfig.errorHintEnabled = false;
        
        message = new AskAiErrorHintMessage(menu, "bios.lua:14: Expected number");
        message.handle(context, menu);
        
        // Assert rate limiter wasn't even touched
        assertEquals(0, AiRateLimiter.INSTANCE.playerStatesForTest().size());
    }

    @Test
    void sanitizes_control_chars() {
        // Just verify it doesn't crash or trigger rate limits before sanitization drops it if it's empty
        message = new AskAiErrorHintMessage(menu, "\u0000\u0001\u001F");
        message.handle(context, menu);
        
        // Blank after sanitize -> drops
        assertEquals(0, AiRateLimiter.INSTANCE.playerStatesForTest().size());
    }
}
