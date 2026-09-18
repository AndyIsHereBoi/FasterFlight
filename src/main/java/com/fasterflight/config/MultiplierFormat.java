package com.fasterflight.config;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;

import java.util.Locale;
import java.util.OptionalDouble;

/**
 * Parsing and formatting for the multiplier text box, kept out of the widget so both the slider and
 * the text box agree on what a legal value looks like.
 */
public final class MultiplierFormat {

    public static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", FasterFlightConfig.clamp(value));
    }

    public static String formatWithSuffix(double value) {
        return format(value) + "x";
    }

    /**
     * Accepts a trailing {@code x}, surrounding whitespace, and either a comma or a full stop as the
     * decimal separator, since the latter varies by locale.
     *
     * @return the value clamped and snapped to the step size, or empty if the text is not a number
     */
    public static OptionalDouble parse(String raw) {
        if (raw == null) {
            return OptionalDouble.empty();
        }

        String cleaned = raw.trim().toLowerCase(Locale.ROOT);
        if (cleaned.endsWith("x")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        cleaned = cleaned.replace(',', '.');

        if (cleaned.isEmpty()) {
            return OptionalDouble.empty();
        }

        try {
            return OptionalDouble.of(FasterFlightConfig.clamp(Double.parseDouble(cleaned)));
        } catch (NumberFormatException exception) {
            return OptionalDouble.empty();
        }
    }

    public static Text toText(double value) {
        return Text.literal(formatWithSuffix(value));
    }

    static TextRenderer font() {
        return MinecraftClient.getInstance().textRenderer;
    }

    private MultiplierFormat() {
    }
}
