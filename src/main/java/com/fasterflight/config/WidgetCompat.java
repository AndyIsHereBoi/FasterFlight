package com.fasterflight.config;

import net.minecraft.client.gui.widget.ClickableWidget;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reads and writes {@link ClickableWidget} geometry without binding to one Minecraft version.
 *
 * <p>The config screen positions widgets by hand, and the representation of a widget's origin
 * changed in 1.19.3:
 * <ul>
 *   <li>1.18.2 / 1.19.2: {@code x} and {@code y} are <em>public fields</em>; no accessors exist.</li>
 *   <li>1.19.3+: the fields are private and {@code getX()/setX()/getY()/setY()} are the only route.</li>
 * </ul>
 *
 * <p>Referencing either form directly would pin the jar to one side, so the accessors are tried
 * first and the fields are the fallback. Both are resolved once and cached.
 *
 * <p>{@code getWidth}/{@code setWidth} are public on every supported version and are called
 * directly by callers.
 */
final class WidgetCompat {

    private static Method getXMethod;
    private static Method getYMethod;
    private static Method setXMethod;
    private static Method setYMethod;

    private static Field xField;
    private static Field yField;

    private static boolean resolved;

    private WidgetCompat() {
    }

    /**
     * Reads a widget's left edge.
     *
     * @param widget the widget to measure
     * @return the widget's x coordinate
     */
    static int getX(ClickableWidget widget) {
        resolve(widget);
        return getXMethod != null ? (int) invoke(getXMethod, widget) : (int) readField(xField, widget);
    }

    /**
     * Reads a widget's top edge.
     *
     * @param widget the widget to measure
     * @return the widget's y coordinate
     */
    static int getY(ClickableWidget widget) {
        resolve(widget);
        return getYMethod != null ? (int) invoke(getYMethod, widget) : (int) readField(yField, widget);
    }

    /**
     * Moves a widget, using accessors where available and the public fields otherwise.
     *
     * @param widget the widget to move
     * @param x new left edge
     * @param y new top edge
     */
    static void setPosition(ClickableWidget widget, int x, int y) {
        resolve(widget);
        if (setXMethod != null) {
            invoke(setXMethod, widget, x);
            invoke(setYMethod, widget, y);
            return;
        }
        writeField(xField, widget, x);
        writeField(yField, widget, y);
    }

    /**
     * Locates the accessors, falling back to the fields, once.
     *
     * @param widget a live widget, used only to name the class in error messages
     */
    private static synchronized void resolve(ClickableWidget widget) {
        if (resolved) {
            return;
        }
        resolved = true;

        try {
            getXMethod = ClickableWidget.class.getMethod("getX");
            getYMethod = ClickableWidget.class.getMethod("getY");
            setXMethod = ClickableWidget.class.getMethod("setX", int.class);
            setYMethod = ClickableWidget.class.getMethod("setY", int.class);
            return;
        } catch (NoSuchMethodException ignored) {
            // 1.18.2 / 1.19.2: no accessors, fall through to the public fields.
        }

        Class<?> type = widget.getClass();
        while (type != null) {
            try {
                xField = type.getDeclaredField("x");
                yField = type.getDeclaredField("y");
                xField.setAccessible(true);
                yField.setAccessible(true);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new IllegalStateException("Cannot locate x/y on " + widget.getClass().getName());
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to invoke " + method, e);
        }
    }

    private static Object readField(Field field, Object target) {
        try {
            return field.get(target);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to read " + field, e);
        }
    }

    private static void writeField(Field field, Object target, Object value) {
        try {
            field.set(target, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to write " + field, e);
        }
    }
}
