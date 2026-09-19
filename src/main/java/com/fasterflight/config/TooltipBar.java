package com.fasterflight.config;

import me.shedaniel.clothconfig2.api.Tooltip;
import me.shedaniel.clothconfig2.gui.AbstractConfigScreen;
import me.shedaniel.math.Point;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Shows hover hints in a bar along the bottom of the config screen instead of beside the cursor,
 * where Cloth would cover the controls being described.
 *
 * <p>This feeds Cloth's own tooltip queue, which it renders after all rows have drawn and then
 * clears, so no custom overlay is needed.
 */
final class TooltipBar {

    private static final int BOTTOM_MARGIN = 40;

    /** Wrap width in pixels, roughly 45 characters at the default font size. */
    private static final int WRAP_PIXELS = 270;

    private TooltipBar() {
    }

    /**
     * Queues a hint, called from a row's render while it is hovered. Rows later in the list queue
     * later, so the visible hint always matches the row under the cursor.
     */
    static void publish(String hintKey) {
        Minecraft client = Minecraft.getInstance();
        Screen screen = client.screen;
        if (!(screen instanceof AbstractConfigScreen configScreen)) {
            return;
        }

        List<FormattedCharSequence> lines = client.font.split(Component.translatable(hintKey), WRAP_PIXELS);
        if (lines.isEmpty()) {
            return;
        }

        int blockWidth = lines.stream().mapToInt(client.font::width).max().orElse(0);
        int blockHeight = lines.size() * (client.font.lineHeight + 1);
        int x = Math.max(4, (screen.width - blockWidth) / 2);
        int y = Math.max(4, screen.height - BOTTOM_MARGIN - blockHeight);

        configScreen.addTooltip(Tooltip.of(new Point(x, y), lines.toArray(FormattedCharSequence[]::new)));
    }
}
