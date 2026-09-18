package com.fasterflight.config;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Creates a {@link ButtonWidget} on versions that disagree about how to build one.
 *
 * <p>1.19.3 made ButtonWidget's constructor protected and added a
 * {@code builder(Text, PressAction)} factory; 1.18.2 and 1.19.2 expose a public constructor and have
 * no builder. Neither route compiles against the other version's target, so both are resolved
 * reflectively and cached.
 *
 * <p>The press handler is wrapped in the version's {@code PressAction} interface with a proxy, so
 * the handler still runs without this file naming an interface whose nesting has moved between
 * releases.
 */
final class ButtonCompat {

    private static Constructor<?> legacyCtor;
    private static Method builderFactory;
    private static Method builderBuild;
    private static Class<?> pressActionType;

    private static boolean resolved;

    private ButtonCompat() {
    }

    /**
     * Builds a button with the given label and press handler.
     *
     * @param x left edge
     * @param y top edge
     * @param width button width
     * @param height button height
     * @param label the button text
     * @param onPress the action to run when pressed
     * @return a configured button widget
     */
    static ButtonWidget create(int x, int y, int width, int height, Text label, Runnable onPress) {
        resolve();

        ButtonWidget widget = builderFactory != null
                ? buildViaBuilder(x, y, width, height, label, onPress)
                : buildViaConstructor(x, y, width, height, label, onPress);

        // Builder method names vary by version, so pin the geometry afterwards for an exact match
        // with the constructor path regardless of which builder methods were available.
        WidgetCompat.setPosition(widget, x, y);
        widget.setWidth(width);
        return widget;
    }

    /** Uses the 1.19.3+ builder route. */
    private static ButtonWidget buildViaBuilder(int x, int y, int width, int height, Text label,
                                                Runnable onPress) {
        try {
            Object builder = builderFactory.invoke(null, label, pressAction(onPress));
            callIfPresent(builder, "size", width, height);
            callIfPresent(builder, "position", x, y);
            return (ButtonWidget) builderBuild.invoke(builder);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to build ButtonWidget via builder", e);
        }
    }

    /** Uses the 1.18.2 / 1.19.2 public constructor route. */
    private static ButtonWidget buildViaConstructor(int x, int y, int width, int height, Text label,
                                                    Runnable onPress) {
        try {
            return (ButtonWidget) legacyCtor.newInstance(
                    x, y, width, height, label, pressAction(onPress));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to build ButtonWidget via constructor", e);
        }
    }

    /**
     * Wraps a {@link Runnable} in the version's {@code PressAction} interface.
     *
     * <p>Only {@code onPress} needs to do real work; the remaining methods are handled so the proxy
     * behaves sensibly if the widget compares or logs it.
     *
     * @param onPress the action to run
     * @return a proxy implementing {@code PressAction}
     */
    private static Object pressAction(Runnable onPress) {
        return Proxy.newProxyInstance(
                ButtonCompat.class.getClassLoader(),
                new Class<?>[]{pressActionType},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "onPress" -> {
                            onPress.run();
                            return null;
                        }
                        case "equals" -> {
                            return args != null && args.length == 1 && proxy == args[0];
                        }
                        case "hashCode" -> {
                            return System.identityHashCode(proxy);
                        }
                        case "toString" -> {
                            return "FasterFlightButtonAction";
                        }
                        default -> {
                            return null;
                        }
                    }
                });
    }

    /** Calls a builder method when present, tolerating versions that name it differently. */
    private static void callIfPresent(Object builder, String name, int v1, int v2) {
        try {
            builder.getClass().getMethod(name, int.class, int.class).invoke(builder, v1, v2);
        } catch (ReflectiveOperationException ignored) {
            // Absent here; geometry is pinned by the caller anyway.
        }
    }

    /** Resolves the available construction route once. */
    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;

        pressActionType = findPressAction();

        for (Method candidate : ButtonWidget.class.getMethods()) {
            if (!"builder".equals(candidate.getName()) || candidate.getParameterCount() != 2) {
                continue;
            }
            if (candidate.getParameterTypes()[0] != Text.class) {
                continue;
            }
            try {
                builderBuild = candidate.getReturnType().getMethod("build");
                builderFactory = candidate;
                return;
            } catch (NoSuchMethodException ignored) {
                // Unexpected builder shape; fall through to the legacy constructor.
            }
        }

        legacyCtor = findLegacyConstructor();
        if (legacyCtor == null) {
            throw new IllegalStateException("No usable ButtonWidget construction path found");
        }
    }

    private static Class<?> findPressAction() {
        for (Class<?> nested : ButtonWidget.class.getDeclaredClasses()) {
            if (nested.getSimpleName().equals("PressAction") && nested.isInterface()) {
                return nested;
            }
        }
        throw new IllegalStateException("ButtonWidget.PressAction not found");
    }

    /**
     * Finds the six-argument constructor used before the builder existed.
     *
     * <p>It is protected on 1.19.3, so {@code setAccessible} is required there even though the
     * builder is the preferred route and this is only a fallback.
     */
    private static Constructor<?> findLegacyConstructor() {
        for (Constructor<?> ctor : ButtonWidget.class.getDeclaredConstructors()) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length == 6 && params[0] == int.class) {
                ctor.setAccessible(true);
                return ctor;
            }
        }
        return null;
    }
}
