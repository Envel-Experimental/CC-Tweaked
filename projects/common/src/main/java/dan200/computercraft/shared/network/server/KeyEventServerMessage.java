// SPDX-FileCopyrightText: 2019 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.network.server;

import dan200.computercraft.shared.computer.menu.ComputerMenu;
import dan200.computercraft.shared.network.MessageType;
import dan200.computercraft.shared.network.NetworkMessages;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.inventory.AbstractContainerMenu;


public class KeyEventServerMessage extends ComputerServerMessage {
    private final Action type;
    private final int key;

    public KeyEventServerMessage(AbstractContainerMenu menu, Action type, int key) {
        super(menu);
        this.type = type;
        this.key = key;
    }

    public KeyEventServerMessage(FriendlyByteBuf buf) {
        super(buf);
        type = buf.readEnum(Action.class);
        key = buf.readVarInt();
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        super.write(buf);
        buf.writeEnum(type);
        buf.writeVarInt(key);
    }

    @Override
    protected void handle(ServerNetworkContext context, ComputerMenu container) {
        var input = container.getInput();
        switch (type) {
            case UP -> input.keyUp(key);
            case DOWN -> input.keyDown(key, false);
            case REPEAT -> input.keyDown(key, true);
            case CHAR -> {
                if (key < 128) {
                    input.charTyped((char) key);
                } else {
                    // If the character is not ASCII, we encode it as UTF-8 and send each byte as a character.
                    // This allows the Lua side to receive UTF-8 encoded strings.
                    var s = String.valueOf((char) key);
                    var bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    for (byte b : bytes) {
                        input.charTyped((char) (b & 0xFF));
                    }
                }
            }
        }
    }

    @Override
    public MessageType<KeyEventServerMessage> type() {
        return NetworkMessages.KEY_EVENT;
    }

    public enum Action {
        DOWN, REPEAT, UP, CHAR
    }
}
