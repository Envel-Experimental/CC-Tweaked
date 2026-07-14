// SPDX-FileCopyrightText: 2024 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.client.gui.widgets;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Overlay widget that displays AI explanations for Lua errors.
 */
public class AiHintOverlay extends AbstractWidget {
    private String hintText = "Loading hint from AI...";

    public AiHintOverlay(int x, int y, int width, int height) {
        super(x, y, width, height, Component.literal("AI Hint"));
    }

    public void setHintText(String hintText) {
        this.hintText = hintText;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;

        // Draw a dark semi-transparent background
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xE0000000); // 88% opaque black

        // Draw border
        graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFF5555FF);

        // Draw text wrapped
        var font = net.minecraft.client.Minecraft.getInstance().font;
        graphics.drawWordWrap(font, Component.literal(hintText), getX() + 4, getY() + 4, getWidth() - 8, 0xFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // If clicked outside, close the overlay
        if (!isMouseOver(mouseX, mouseY)) {
            this.visible = false;
            return false;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            this.visible = false;
            return true; // handled
        }
        return super.keyPressed(key, scancode, modifiers);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.literal(hintText));
    }
}
