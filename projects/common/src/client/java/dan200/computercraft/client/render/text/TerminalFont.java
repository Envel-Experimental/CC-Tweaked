// Copyright Daniel Ratcliffe, 2011-2022. Do not distribute without permission.
//
// SPDX-License-Identifier: LicenseRef-CCPL

package dan200.computercraft.client.render.text;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import dan200.computercraft.core.terminal.TextBuffer;
import dan200.computercraft.core.util.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector4f;

import java.io.IOException;
import java.io.InputStream;
import java.util.BitSet;

import static dan200.computercraft.client.render.text.FixedWidthFontRenderer.FONT;
import static dan200.computercraft.client.render.text.FixedWidthFontRenderer.FONT_HEIGHT;
import static dan200.computercraft.client.render.text.FixedWidthFontRenderer.FONT_WIDTH;

public class TerminalFont {
    private static final int TEXTURE_SIZE = 256;
    private static final int COLUMNS = 16;
    private static final int ROWS = 16;

    private static final TerminalFont INSTANCE = new TerminalFont();

    private final BitSet fontGlyphs = new BitSet(Character.MAX_VALUE);
    private final Vector4f[] fontGlyphUvs = new Vector4f[Character.MAX_VALUE];
    private final DynamicTexture fontTexture;
    private final ResourceLocation fontTextureName;

    private TerminalFont() {
        fontTexture = new DynamicTexture(TEXTURE_SIZE, TEXTURE_SIZE, true);
        var textureManager = Minecraft.getInstance().getTextureManager();
        fontTextureName = textureManager.register("computercraft:dynamic_font", fontTexture);

        try (InputStream fontStream = Minecraft.getInstance().getResourceManager().open(FONT)) {
            var image = NativeImage.read(fontStream);
            for (int i = 0; i < 256; i++) {
                if (isGlyphLoaded(image, i)) {
                    fontGlyphs.set(i);
                }
            }
        } catch (IOException e) {
            // Should not happen
        }
    }

    public static TerminalFont getInstance() {
        return INSTANCE;
    }

    public void preloadCharacterFont(TextBuffer text) {
        var minecraft = Minecraft.getInstance();
        var font = minecraft.font;
        for (int i = 0; i < text.length(); i++) {
            var c = text.charAt(i);
            if (c != ' ' && !fontGlyphs.get(c)) {
                font.getFontSet(font.getFontSet(c).getFont(c).getProvider().getFabricId()).getGlyph(c);
            }
        }
    }

    private boolean isGlyphLoaded(NativeImage image, int index) {
        var glyphX = (index % COLUMNS) * (FONT_WIDTH + 2) + 1;
        var glyphY = (index / COLUMNS) * (FONT_HEIGHT + 2) + 1;
        for (int y = 0; y < FONT_HEIGHT; y++) {
            for (int x = 0; x < FONT_WIDTH; x++) {
                if (image.getLuminanceOrAlpha(glyphX + x, glyphY + y) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private void uploadGlyph(Font font, int index) {
        var glyphInfo = font.getFontSet(font.getFontSet((char) index).getFont((char) index).getProvider().getFabricId()).getGlyph((char) index);
        var image = new NativeImage(NativeImage.Format.RGBA, (int) glyphInfo.getBearingX() + glyphInfo.getPixelWidth(), (int) glyphInfo.getBearingY() + glyphInfo.getPixelHeight(), false);
        glyphInfo.upload(image);

        var glyphX = (index % COLUMNS) * (FONT_WIDTH + 2) + 1;
        var glyphY = (index / COLUMNS) * (FONT_HEIGHT + 2) + 1;
        for (int y = 0; y < FONT_HEIGHT; y++) {
            for (int x = 0; x < FONT_WIDTH; x++) {
                fontTexture.getPixels().setPixelRGBA(glyphX + x, glyphY + y, image.getPixelRGBA(x, y));
            }
        }
    }

    public Vector4f getGlyphUv(int index) {
        if (fontGlyphUvs[index] == null) {
            var glyphX = (index % COLUMNS) * (FONT_WIDTH + 2) + 1;
            var glyphY = (index / COLUMNS) * (FONT_HEIGHT + 2) + 1;
            fontGlyphUvs[index] = new Vector4f(
                (float) glyphX / TEXTURE_SIZE,
                (float) glyphY / TEXTURE_SIZE,
                (float) (glyphX + StringUtil.getCodepointWidth(index) * FONT_WIDTH) / TEXTURE_SIZE,
                (float) (glyphY + FONT_HEIGHT) / TEXTURE_SIZE
            );
        }
        return fontGlyphUvs[index];
    }

    public Vector4f getWhiteGlyphUv() {
        return getGlyphUv(255);
    }

    public void bind() {
        TextureManager textureManager = Minecraft.getInstance().getTextureManager();
        AbstractTexture texture = textureManager.getTexture(fontTextureName);
        GlStateManager._bindTexture(texture.getId());
    }
}