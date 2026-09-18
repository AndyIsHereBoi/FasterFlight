package com.fasterflight.config;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * Parses and formats the multiplier text box contents.
 *
 * <p>Kept separate from the widget so the parsing rules can be reasoned about and tested on their
 * own, and so both the slider and the text box agree on what a legal value looks like.
 */
public final class MultiplierFormat {

    /** Formats a multiplier for display, always with exactly one decimal place. */
    public static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", FasterFlightConfig.clamp(value));
    }

    /** Formats a multiplier including the trailing {@code x}, for slider labels. */
    public static String formatWithSuffix(double value) {
        return format(value) + "x";
    }

    /**
     * Attempts to parse user input into a valid multiplier.
     *
     * <p>Tolerates a trailing {@code x}, surrounding whitespace, and either a comma or a full stop
     * as the decimal separator, because both are common depending on the player's locale.
     *
     * @param raw the raw text box contents
     * @return the parsed value clamped and snapped to the step size, or empty if the text is not a number
     */
    public static java.util.OptionalDouble parse(String raw) {
        if (raw == null) {
            return java.util.OptionalDouble.empty();
        }

        String cleaned = raw.trim().toLowerCase(Locale.ROOT);
        if (cleaned.endsWith("x")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        cleaned = cleaned.replace(',', '.');

        if (cleaned.isEmpty()) {
            return java.util.OptionalDouble.empty();
        }

        try {
            return java.util.OptionalDouble.of(FasterFlightConfig.clamp(Double.parseDouble(cleaned)));
        } catch (NumberFormatException exception) {
            return java.util.OptionalDouble.empty();
        }
    }

    /** @return the multiplier formatted as a translation-ready message for the slider label. */
    public static Text toText(double value) {
        return Text.literal(formatWithSuffix(value));
    }

    /** @return the current client font, used for widget width calculations. */
    static net.minecraft.client.font.TextRenderer font() {
        return MinecraftClient.getInstance().textRenderer;
    }

    private MultiplierFormat() {
    }
}
