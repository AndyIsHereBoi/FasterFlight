package com.fasterflight.config;

import net.minecraft.client.gui.widget.ClickableWidget;

import java.lang.reflect.Field;

/**
 * Reads and writes {@link ClickableWidget} geometry across the supported Minecraft versions.
 *
 * <p>A widget's origin lives in the {@code x} and {@code y} fields it inherits from
 * {@link ClickableWidget}. Those fields are a normal part of the class on every version this mod
 * targets, so they are the simplest way to reposition a widget.
 *
 * <p>Only their <em>names</em> differ between environments, which is the trap here. In a development
 * client Fabric remaps the game to Yarn names, so the fields are literally {@code x} and {@code y}.
 * In a normal installed client only intermediary names exist, so the same fields are
 * {@code field_22760} and {@code field_22761}. A lookup written against either spelling alone
 * therefore works in exactly one of the two environments and fails in the other.
 *
 * <p>Both spellings are listed, intermediary first. The intermediary names are stable across the
 * versions supported here, which is precisely what intermediary names are for, so no mapping lookup
 * is needed at runtime and nothing has to be resolved per version.
 *
 * <p>If neither spelling is found the position is reported as {@link #UNKNOWN} rather than throwing.
 * A wrong offset is a cosmetic problem; an exception thrown while rendering takes the whole screen
 * down with it, so failing softly is the correct trade.
 */
final class WidgetCompat {

    /** Returned when a widget's position cannot be determined on this version. */
    static final int UNKNOWN = Integer.MIN_VALUE;

    /** Intermediary name first (installed client), then the Yarn name (development client). */
    private static final String[] X_NAMES = {"field_22760", "x"};
    private static final String[] Y_NAMES = {"field_22761", "y"};

    private static Field xField;
    private static Field yField;

    private static boolean resolved;

    private WidgetCompat() {
    }

    /**
     * Reads a widget's left edge.
     *
     * @param widget the widget to measure
     * @return the x coordinate, or {@link #UNKNOWN} if it cannot be read
     */
    static int getX(ClickableWidget widget) {
        resolve(widget);
        return xField != null ? (int) readField(xField, widget) : UNKNOWN;
    }

    /**
     * Reads a widget's top edge.
     *
     * @param widget the widget to measure
     * @return the y coordinate, or {@link #UNKNOWN} if it cannot be read
     */
    static int getY(ClickableWidget widget) {
        resolve(widget);
        return yField != null ? (int) readField(yField, widget) : UNKNOWN;
    }

    /**
     * Moves a widget when its position is writable on this version.
     *
     * @param widget the widget to move
     * @param x new left edge
     * @param y new top edge
     * @return whether the widget was moved
     */
    static boolean setPosition(ClickableWidget widget, int x, int y) {
        resolve(widget);
        if (xField == null || yField == null) {
            return false;
        }
        writeField(xField, widget, x);
        writeField(yField, widget, y);
        return true;
    }

    /**
     * Locates the inherited position fields once per JVM.
     *
     * <p>Discovery walks the class hierarchy because the fields are declared on a supertype rather
     * than on the concrete widget. Failure is not an error: it means this version stores position
     * somewhere this class does not know about, and callers fall back to the row geometry Cloth
     * supplies.
     *
     * @param widget a live widget whose class identifies where to start searching
     */
    private static synchronized void resolve(ClickableWidget widget) {
        if (resolved) {
            return;
        }
        resolved = true;

        xField = findField(widget.getClass(), X_NAMES);
        yField = findField(widget.getClass(), Y_NAMES);
    }

    /**
     * Finds the first present field from a list of candidate names.
     *
     * @param owner the class to start from, walking up through supertypes
     * @param candidates candidate field names to accept
     * @return the field, or {@code null} if none of the candidates exists in the hierarchy
     */
    private static Field findField(Class<?> owner, String[] candidates) {
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                for (String candidate : candidates) {
                    if (field.getName().equals(candidate)) {
                        field.setAccessible(true);
                        return field;
                    }
                }
            }
        }
        return null;
    }

    private static Object readField(Field field, Object target) {
        try {
            return field.get(target);
        } catch (IllegalAccessException e) {
            return UNKNOWN;
        }
    }

    private static void writeField(Field field, Object target, Object value) {
        try {
            field.set(target, value);
        } catch (IllegalAccessException ignored) {
            // Position stays put; alignment only, never correctness.
        }
    }
}
