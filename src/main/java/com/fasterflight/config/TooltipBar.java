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

        int blockWidth = lines.stream().mapToInt(font::getWidth).max().orElse(0);
        int blockHeight = lines.size() * (font.fontHeight + 1);
        int x = Math.max(4, (screen.width - blockWidth) / 2);
        int y = Math.max(4, screen.height - BOTTOM_MARGIN - blockHeight);

        configScreen.addTooltip(QueuedTooltip.create(new Point(x, y), lines));
    }

    /**
     * OrderedText exposes no plain accessor, so the characters are collected through its visitor. Any
     * styling is dropped because the tooltip is rebuilt as a fresh literal.
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
