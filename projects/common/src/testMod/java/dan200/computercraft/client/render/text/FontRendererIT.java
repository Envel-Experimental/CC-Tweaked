// Copyright Daniel Ratcliffe, 2011-2022. Do not distribute without permission.
//
// SPDX-License-Identifier: LicenseRef-CCPL

package dan200.computercraft.client.render.text;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dan200.computercraft.core.terminal.Palette;
import dan200.computercraft.core.terminal.TextBuffer;
import dan200.computercraft.client.render.RenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class FontRendererIT {
    @Test
    void testFixedWidthFontRendererWithRussianCharacters() {
        var buffer = Tesselator.getInstance().getBuilder();
        MultiBufferSource.BufferSource source = MultiBufferSource.immediate(buffer);
        VertexConsumer consumer = source.getBuffer(RenderType.text(FixedWidthFontRenderer.FONT));

        var emitter = new FixedWidthFontRenderer.QuadEmitter(new PoseStack().last().pose(), consumer);
        var text = new TextBuffer("Привет, мир!");
        var textColor = new TextBuffer("0000000000000");
        var palette = new Palette(false);

        assertDoesNotThrow(() -> FixedWidthFontRenderer.drawString(emitter, 0, 0, text, textColor, palette, 0));
    }
}
