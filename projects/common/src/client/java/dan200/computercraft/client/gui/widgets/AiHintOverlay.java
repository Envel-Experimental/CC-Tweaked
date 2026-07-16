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
    private String hintText = net.minecraft.client.resources.language.I18n.get("gui.computercraft.tooltip.ai_hint_loading");

    public AiHintOverlay(int x, int y, int width, int height) {
        super(x, y, width, height, Component.translatable("gui.computercraft.tooltip.ai_hint_overlay"));
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
    public boolean isMouseOver(double mouseX, double mouseY) {
        return visible; // Intercept all clicks when visible
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check if clicked inside the visual box
        boolean insideX = mouseX >= getX() && mouseX < getX() + getWidth();
        boolean insideY = mouseY >= getY() && mouseY < getY() + getHeight();
        if (!insideX || !insideY) {
            this.visible = false;
            return true; // Consume the click so it doesn't leak to the terminal
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
