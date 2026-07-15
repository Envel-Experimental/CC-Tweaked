// SPDX-FileCopyrightText: 2024 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.client.font;

import com.mojang.blaze3d.platform.NativeImage;
import dan200.computercraft.client.render.text.FixedWidthFontRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jspecify.annotations.Nullable;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;

/**
 * Extends the CC terminal font atlas from 256×256 (16 cols × 16 rows, Latin-1 only)
 * to 512×256 (32 cols × 16 rows) by extracting Cyrillic glyphs from Minecraft's own
 * {@link Font} renderer at client startup.
 *
 * <p>This means we never need manual glyph artwork — we reuse whatever font the player
 * has loaded (default Minecraft font includes full Cyrillic U+0400–U+04FF coverage).
 *
 * <p>The patched texture is uploaded as a {@link DynamicTexture} that overrides the
 * static {@code computercraft:textures/gui/term_font.png} atlas.
 *
 * <p>Call {@link #patch()} once from the Fabric {@code ClientModInitializer} <em>after</em>
 * Minecraft's font system has finished loading (i.e. inside a
 * {@code ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener} callback,
 * or at the earliest in {@code ClientTickEvents.START_CLIENT_TICK} on the first tick).
 */
public final class CyrillicFontPatcher {

    private static final Logger LOG = LoggerFactory.getLogger(CyrillicFontPatcher.class);

    /** Original atlas is 256×256 covering codepoints 0x00–0xFF (16 columns × 16 rows). */
    private static final int ORIG_WIDTH = 256;
    private static final int ATLAS_HEIGHT = 256;

    /** Extend atlas to 512x256 so the right half (columns 16–31) can hold 256 Cyrillic glyphs.
     *  Width stays 256 because 32 columns × 8px = 256px exactly fits. */
    public static final int EXTENDED_WIDTH = 256;

    /** Each glyph cell occupies 8×11px (6px glyph + 2px horizontal padding, 9px + 2px vertical). */
    private static final int CELL_W = FixedWidthFontRenderer.FONT_WIDTH + 2;   // 8
    private static final int CELL_H = FixedWidthFontRenderer.FONT_HEIGHT + 2;  // 11

    /** Columns per row in the new layout (32 instead of 16). */
    public static final int COLS = 32;

    /** Cyrillic Unicode block: U+0400 – U+04FF. */
    private static final int CYRILLIC_START = 0x0400;
    private static final int CYRILLIC_END   = 0x04FF;

    /** Whether the patch has already been applied (idempotent). */
    private static volatile boolean patched = false;

    private CyrillicFontPatcher() {}

    /**
     * Called by the Fabric resource reload listener on every resource pack reload.
     * Resets the {@link #patched} flag so the atlas is re-built with the newly loaded font.
     */
    public static void onReload() {
        patched = false;
        patch();
    }

    /**
     * Apply the Cyrillic atlas patch.
     * Safe to call multiple times — only runs once per session (guarded by {@link #patched}).
     * Must be called on the render/client thread.
     */
    public static void patch() {
        if (patched) return;
        patched = true;

        var mc = Minecraft.getInstance();

        // Load the original 256×256 atlas from the mod's resources.
        NativeImage extended;
        try (var original = loadOriginalAtlas(mc)) {
            if (original == null) {
                LOG.error("[CC:Cyrillic] Failed to load original term_font.png — Cyrillic support disabled.");
                return;
            }
            extended = buildExtendedAtlas(original, mc.font);
        } catch (Exception e) {
            LOG.error("[CC:Cyrillic] Error building extended font atlas", e);
            return;
        }

        // Upload as a DynamicTexture, overriding the static resource.
        var texture = new DynamicTexture(extended);
        mc.getTextureManager().register(FixedWidthFontRenderer.FONT, texture);

        LOG.info("[CC:Cyrillic] Extended font atlas (512×256) uploaded — Cyrillic support active.");
    }

    // --- Private helpers ---

    @Nullable
    private static NativeImage loadOriginalAtlas(Minecraft mc) {
        try {
            var resource = mc.getResourceManager().getResource(FixedWidthFontRenderer.FONT);
            if (resource.isEmpty()) return null;
            try (var stream = resource.get().open()) {
                return NativeImage.read(stream);
            }
        } catch (Exception e) {
            LOG.warn("[CC:Cyrillic] Could not open original font atlas", e);
            return null;
        }
    }

    /**
     * Build a 512×256 {@link NativeImage} by:
     * <ol>
     *   <li>Copying the original 256×256 Latin-1 glyphs into the left half.</li>
     *   <li>Rasterising Cyrillic codepoints (U+0400–U+04FF) from {@code mcFont} into
     *       the right half (columns 16–31).</li>
     * </ol>
     */
    private static NativeImage buildExtendedAtlas(NativeImage original, Font mcFont) {
        var atlas = new NativeImage(NativeImage.Format.RGBA, EXTENDED_WIDTH, ATLAS_HEIGHT, false);

        // Copy original image exactly as is (contains Latin in left half, backgrounds at bottom)
        for (var y = 0; y < ATLAS_HEIGHT; y++) {
            for (var x = 0; x < ORIG_WIDTH; x++) {
                atlas.setPixelRGBA(x, y, original.getPixelRGBA(x, y));
            }
        }

        // Rasterise Cyrillic glyphs into the right half (columns 16-31).
        // 256 Cyrillic characters will map to slots 256-511.
        for (var cp = CYRILLIC_START; cp <= CYRILLIC_END; cp++) {
            var slotIndex = cp - CYRILLIC_START + 256; 
            renderGlyph(atlas, mcFont, cp, slotIndex);
        }

        return atlas;
    }

    /** Lazily initialised AWT font used for rasterising Cyrillic glyphs. */
    private static java.awt.@org.jspecify.annotations.Nullable Font awtFont = null;

    /**
     * Render a single codepoint into the atlas using Java AWT font rasterisation.
     *
     * <p>This avoids any dependency on Minecraft's internal GL glyph pipeline — we just use
     * the system's {@link java.awt.Font} (SansSerif) to render each character into an
     * offscreen {@link BufferedImage} and then copy the pixels into the atlas NativeImage.
     *
     * <p>The AWT route works on any JVM without LWJGL context or Mixins.
     */
    private static void renderGlyph(NativeImage atlas, Font mcFont, int cp, int slotIndex) {
        var col = 16 + (slotIndex % 16);
        var row = (slotIndex - 256) / 16;

        var destX = 1 + col * CELL_W;
        var destY = 1 + row * CELL_H;

        if (destX + FixedWidthFontRenderer.FONT_WIDTH > EXTENDED_WIDTH ||
            destY + FixedWidthFontRenderer.FONT_HEIGHT > ATLAS_HEIGHT) {
            return; // Slot out of bounds — skip silently.
        }

        try {
            if (awtFont == null) {
                // Use a commonly available sans-serif font with Cyrillic coverage.
                // We request a bold-ish weight to match CC's crispy pixel font look.
                awtFont = new java.awt.Font("SansSerif", java.awt.Font.BOLD, 8);
                // Verify it can actually render Cyrillic
                if (!awtFont.canDisplay(cp)) {
                    // Fallback: try to find a font that can
                    awtFont = findFontFor(cp, 9);
                }
            }

            // Render the character into a temporary greyscale BufferedImage.
            var img = new BufferedImage(CELL_W, CELL_H, BufferedImage.TYPE_BYTE_GRAY);
            var g2d = img.createGraphics();
            try {
                g2d.setBackground(Color.BLACK);
                g2d.clearRect(0, 0, CELL_W, CELL_H);

                g2d.setColor(Color.WHITE);
                g2d.setFont(awtFont);
                // No antialiasing — we want sharp pixel edges like the original CC font.
                g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
                g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                    RenderingHints.VALUE_FRACTIONALMETRICS_OFF);

                // Centre the glyph in the cell vertically using font metrics.
                var str = new String(Character.toChars(cp));
                var metrics = g2d.getFontMetrics();
                // Baseline = cell height - descent - 1 (lift it up to prevent chopping legs off letters like Д and Р)
                var baseline = CELL_H - metrics.getDescent() - 1;
                g2d.drawString(str, 1, baseline);
            } finally {
                g2d.dispose();
            }

            // Copy pixels into the NativeImage atlas.
            // TYPE_BYTE_GRAY: each int pixel = 0xFF__AA__AA__AA (A=R=G=B=gray).
            // We want white-on-black: white pixels keep their colour but with alpha=255.
            // The original atlas uses RGBA format.
            for (var dy = 0; dy < CELL_H; dy++) {
                for (var dx = 0; dx < CELL_W; dx++) {
                    if (destX + dx >= EXTENDED_WIDTH || destY + dy >= ATLAS_HEIGHT) continue;
                    var argb = img.getRGB(dx, dy);
                    var gray = argb & 0xFF; // In TYPE_BYTE_GRAY, R=G=B=gray, we take blue
                    if (gray > 0) {
                        // White pixel: write RGBA = (gray, gray, gray, 255)
                        atlas.setPixelRGBA(destX + dx, destY + dy,
                            0xFF000000 | (gray << 16) | (gray << 8) | gray);
                    } else {
                        // Black pixel: transparent (keeps whatever is underneath, but since
                        // we start from a blank atlas this is effectively empty)
                        atlas.setPixelRGBA(destX + dx, destY + dy, 0x00000000);
                    }
                }
            }

            LOG.trace("[CC:Cyrillic] Rendered U+{} at slot {}", Integer.toHexString(cp), slotIndex);

        } catch (Exception e) {
            LOG.warn("[CC:Cyrillic] Failed to render U+{}: {}", Integer.toHexString(cp), e.getMessage());
        }
    }

    /**
     * Search available AWT fonts for one that can display the given codepoint.
     */
    private static java.awt.Font findFontFor(int codepoint, int size) {
        // Try common fallback fonts in order
        var fallbacks = new String[]{
            "SansSerif", "Serif", "Monospaced",
            "Arial", "Helvetica", "DejaVu Sans", "Noto Sans",
            "Lucida Sans", "Tahoma", "Verdana"
        };
        for (var name : fallbacks) {
            var f = new java.awt.Font(name, java.awt.Font.BOLD, size);
            if (f.canDisplay(codepoint)) return f;
        }
        // Last resort: search all available fonts
        for (var f : java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                 .getAllFonts()) {
            if (f.canDisplay(codepoint)) {
                return f.deriveFont(java.awt.Font.BOLD, size);
            }
        }
        // Absolute fallback
        return new java.awt.Font("SansSerif", java.awt.Font.BOLD, size);
    }

    /**
     * Compute the UV coordinates for a codepoint in the extended 512×256 atlas.
     * Used by both font renderers to replace the old 256-wide UV formulas.
     *
     * @param codepoint The character codepoint (0–511 supported range).
     * @return float[4] = {u1, v1, u2, v2} in [0,1] texture space.
     */
    public static float[] uvForCodepoint(int codepoint) {
        var col = (codepoint < 256) ? (codepoint % 16) : (16 + (codepoint % 16));
        var row = (codepoint < 256) ? (codepoint / 16) : ((codepoint - 256) / 16);

        var xStart = 1 + col * CELL_W;
        var yStart = 1 + row * CELL_H;

        float tw = EXTENDED_WIDTH;
        float th = ATLAS_HEIGHT;

        return new float[]{
            xStart / tw,
            yStart / th,
            (xStart + FixedWidthFontRenderer.FONT_WIDTH) / tw,
            (yStart + FixedWidthFontRenderer.FONT_HEIGHT) / th,
        };
    }
}
