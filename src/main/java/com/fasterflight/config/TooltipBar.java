package com.fasterflight.config;

import me.shedaniel.clothconfig2.api.ConfigScreen;
import me.shedaniel.clothconfig2.api.QueuedTooltip;
import me.shedaniel.math.Point;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows hover hints in a bar along the bottom of the config screen instead of beside the cursor.
 *
 * <p>Cloth Config floats tooltips next to the pointer, where they cover the very controls they
 * describe. Here the hint is queued at a fixed position near the bottom of the screen instead.
 *
 * <p>This reuses Cloth's own tooltip queue rather than drawing a custom overlay: the screen renders
 * everything in that queue after all of its rows have drawn, and then clears it, which is exactly the
 * end-of-frame behaviour needed. Positioning is achieved by handing Cloth a point near the bottom of
 * the screen rather than one next to the cursor.
 */
final class TooltipBar {

    /** Distance from the bottom of the screen to the hint text. */
    private static final int BOTTOM_MARGIN = 40;

    /** Wrap width for hint text, in pixels. Roughly 45 characters at the default font size. */
    private static final int WRAP_PIXELS = 270;

    private TooltipBar() {
    }

    /**
     * Queues a hint for the bottom of the screen.
     *
     * <p>Called from a row's render while that row is hovered. Because each row queues its own hint
     * and Cloth draws them in queue order, a row later in the list draws over an earlier one, so the
     * visible hint always matches the row under the cursor.
     *
     * @param hintKey translation key of the hint text
     */
    static void publish(String hintKey) {
        MinecraftClient client = MinecraftClient.getInstance();
        Screen screen = client.currentScreen;
        if (!(screen instanceof ConfigScreen configScreen)) {
            return;
        }

        TextRenderer font = client.textRenderer;
        List<Text> lines = new ArrayList<>();
        for (OrderedText wrapped : font.wrapLines(Text.translatable(hintKey), WRAP_PIXELS)) {
            lines.add(toText(wrapped));
        }
        if (lines.isEmpty()) {
            return;
        }

        // Centre the block horizontally and lift it clear of the buttons at the bottom of the screen.
        int blockWidth = lines.stream().mapToInt(font::getWidth).max().orElse(0);
        int blockHeight = lines.size() * (font.fontHeight + 1);
        int x = Math.max(4, (screen.width - blockWidth) / 2);
        int y = Math.max(4, screen.height - BOTTOM_MARGIN - blockHeight);

        configScreen.addTooltip(QueuedTooltip.create(new Point(x, y), lines));
    }

    /**
     * Flattens a wrapped line back to plain text.
     *
     * <p>{@code OrderedText} carries per-character styling and has no plain accessor, so the
     * characters are collected through its visitor. Only the characters matter here because the
     * tooltip is rebuilt as a fresh literal.
     *
     * @param ordered a wrapped line
     * @return the same line as plain text
     */
    private static Text toText(OrderedText ordered) {
        StringBuilder builder = new StringBuilder();
        ordered.accept((index, style, codePoint) -> {
            builder.appendCodePoint(codePoint);
            return true;
        });
        return Text.literal(builder.toString());
    }
}
