// Copyright Daniel Ratcliffe, 2011-2022. Do not distribute without permission.
//
// SPDX-License-Identifier: LicenseRef-CCPL

package dan200.computercraft.shared.config;

/**
 * The font to use for computers and other terminals.
 */
public enum Font {
    /**
     * The original ComputerCraft font, using a custom texture. This has limited character support, but is very fast
     * to render.
     */
    LEGACY,

    /**
     * A high-quality font, using Minecraft's built-in font renderer. This has much better language support, but may be
     * slower.
     */
    UNICODE,
}