// SPDX-FileCopyrightText: 2024 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.network.server;

import dan200.computercraft.core.AiConfig;
import dan200.computercraft.core.apis.ai.AiAPI;
import dan200.computercraft.core.apis.ai.AiRateLimiter;
import dan200.computercraft.core.apis.ai.AiRequestHandler;
import dan200.computercraft.shared.computer.menu.ComputerMenu;
import dan200.computercraft.shared.network.MessageType;
import dan200.computercraft.shared.network.NetworkMessages;
import dan200.computercraft.shared.network.client.AiHintResponseMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Sent by the client to request an AI hint for a specific error message.
 */
public class AskAiErrorHintMessage extends ComputerServerMessage {
    private final String errorMessage;

    public AskAiErrorHintMessage(AbstractContainerMenu menu, String errorMessage) {
        super(menu);
        this.errorMessage = errorMessage;
    }

    public AskAiErrorHintMessage(FriendlyByteBuf buf) {
        super(buf);
        this.errorMessage = buf.readUtf(AiConfig.maxMessageChars);
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        super.write(buf);
        buf.writeUtf(errorMessage, AiConfig.maxMessageChars);
    }

    @Override
    protected void handle(ServerNetworkContext context, ComputerMenu container) {
        // D4. Check if error hints are enabled
        if (!AiConfig.errorHintEnabled || !AiConfig.enabled) return;

        // D7. Sanitize the string to remove malicious control chars
        var sanitized = errorMessage.replaceAll("\\p{Cc}", "").trim();
        if (sanitized.isBlank()) return;
        
        var truncated = sanitized.length() > AiConfig.maxMessageChars ? sanitized.substring(0, AiConfig.maxMessageChars) : sanitized;

        var sender = context.getSender();
        if (sender == null) return;
        
        var uuid = sender.getUUID();
        var limitResult = AiRateLimiter.INSTANCE.check(uuid);
        if (limitResult != AiRateLimiter.LimitResult.ALLOWED) {
            // Drop it, or optionally send a rate limit error message to the client.
            // For now, silent drop is fine to avoid DDoS via error spam.
            return;
        }

        // Send to background worker
        var model = AiConfig.getDefaultModel();
        if (model == null) {
            AiRateLimiter.INSTANCE.release();
            return;
        }

        // Fire and forget, AiRequestHandler manages executor wrapping.
        AiRequestHandler.dispatchWithCallbacks(
            java.util.List.of(new AiAPI.AiMessage("user", "Explain this error: " + truncated)),
            model,
            new AiAPI.RequestOptions(model.id(), 0.7f, AiConfig.maxResponseTokens, null, null, AiConfig.defaultResponseLanguage, true),
            response -> {
                // S2C network response on success
                ServerNetworking.sendToPlayer(new AiHintResponseMessage(response.trim()), sender);
            },
            error -> {
                // Send back the error as the hint so user knows what went wrong
                ServerNetworking.sendToPlayer(new AiHintResponseMessage("Error: " + error.getMessage()), sender);
            }
        );
    }

    @Override
    public MessageType<AskAiErrorHintMessage> type() {
        return NetworkMessages.ASK_AI_ERROR_HINT;
    }
}
