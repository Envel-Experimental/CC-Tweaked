// SPDX-FileCopyrightText: 2024 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.network.client;

import dan200.computercraft.shared.network.MessageType;
import dan200.computercraft.shared.network.NetworkMessage;
import dan200.computercraft.shared.network.NetworkMessages;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Sent from the server to the client with the requested AI error hint text.
 */
public class AiHintResponseMessage implements NetworkMessage<ClientNetworkContext> {
    private final String hintText;

    public AiHintResponseMessage(String hintText) {
        this.hintText = hintText;
    }

    public AiHintResponseMessage(FriendlyByteBuf buf) {
        this.hintText = buf.readUtf(32767); // Allow up to 32k chars for response
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(hintText, 32767);
    }

    @Override
    public void handle(ClientNetworkContext context) {
        context.handleAiHintResponse(hintText);
    }

    @Override
    public MessageType<AiHintResponseMessage> type() {
        return NetworkMessages.AI_HINT_RESPONSE;
    }
}
